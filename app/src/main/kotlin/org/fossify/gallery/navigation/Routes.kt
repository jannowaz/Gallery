package org.fossify.gallery.navigation

import android.content.Context
import kotlinx.serialization.Serializable
import androidx.navigation.NavHostController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.fossify.gallery.helpers.ViewerLoadDirection
import org.fossify.gallery.helpers.ViewerPathChunk
import org.fossify.gallery.helpers.ViewerPathWindow
import org.fossify.gallery.helpers.ViewerWindowMath
import java.io.File
import java.util.WeakHashMap
import java.util.UUID

@Serializable
data object Home

@Serializable
data class Folder(val folderPath: String)

@Serializable
data class FolderSwipe(val folderPath: String, val startPath: String = "")

@Serializable
data class PersonReview(val folderPath: String)

@Serializable
data object Settings

@Serializable
data object ManageCollections

@Serializable
data object About

@Serializable
data object TagBrowser

@Serializable
data object StorageAnalysis

@Serializable
data class CleanupHub(val folderPath: String = "")

@Serializable
data class DuplicateFinder(val folderPath: String = "")

@Serializable
data object CompressionReview

@Serializable
data object CompressionSwipe

@Serializable
data object FoldersMover

@Serializable
data object RecycleBin

@Serializable
data class Viewer(val sessionId: String, val startIndex: Int, val startPath: String = "")

/**
 * Prevents one physical tap from becoming several Viewer destinations while Navigation Compose is
 * still animating the first one in. A random Viewer session id means launchSingleTop alone cannot
 * deduplicate rapid taps: every tap would otherwise be a different route. The lock is released by
 * GalleryNavHost when the top destination is no longer a Viewer.
 */
object ViewerNavigationGate {
    private val locks = WeakHashMap<NavHostController, Boolean>()

    @Synchronized
    fun tryAcquire(controller: NavHostController): Boolean {
        if (locks[controller] == true) return false
        locks[controller] = true
        return true
    }

    @Synchronized
    fun release(controller: NavHostController) {
        locks.remove(controller)
    }
}

/**
 * Transient holder for the viewer's media list. Passing the full list through the typed route
 * serialises every path into the destination URL, which overflows and fails to match for large
 * libraries (IllegalArgumentException: destination ... cannot be found in the navigation graph).
 */
object ViewerArgs {
    private const val MAX_SESSIONS = 12
    private const val WINDOW_PAGE_SIZE = 24
    private const val MAX_WINDOW_PATHS = 65
    private val sessions = LinkedHashMap<String, SessionState>()
    private val sessionFlows = LinkedHashMap<String, MutableStateFlow<List<String>>>()
    private val resolverJobs = LinkedHashMap<String, kotlinx.coroutines.Job>()
    private val loadingDirections = HashSet<String>()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var appContext: Context? = null
    @Volatile private var storageDir: File? = null

    private data class SessionState(
        var paths: List<String>,
        var hasPrevious: Boolean = false,
        var hasNext: Boolean = false,
        val loadMore: (suspend (String, ViewerLoadDirection, Int) -> ViewerPathChunk)? = null,
    )

    /** Configures the process-local snapshot store without performing disk I/O on the main thread. */
    fun configure(context: Context) {
        appContext = context.applicationContext
    }

    private fun resolveStorageDir(): File? {
        storageDir?.let { return it }
        val ctx = appContext ?: return null
        return synchronized(this) {
            storageDir ?: ctx.filesDir.resolve("viewer_sessions").apply { mkdirs() }.also { storageDir = it }
        }
    }

    /** Stores one immutable browsing snapshot and returns its route-safe identifier.
     *
     * The route still carries only the id (and the tapped start path). The full list is persisted
     * off the main thread so a process death/config recreation cannot turn a valid Viewer route into
     * an empty pager. The in-memory path is retained for the normal, instant navigation case.
     */
    @Synchronized
    fun create(paths: List<String>): String {
        val id = UUID.randomUUID().toString()
        val snapshot = paths.distinct()
        sessions[id] = SessionState(snapshot)
        sessionFlows[id] = MutableStateFlow(snapshot)
        val removed = mutableListOf<String>()
        while (sessions.size > MAX_SESSIONS) {
            val oldest = sessions.keys.first()
            sessions.remove(oldest)
            sessionFlows.remove(oldest)
            resolverJobs.remove(oldest)?.cancel()
            removed += oldest
        }
        if (appContext != null) {
            ioScope.launch {
                val dir = resolveStorageDir() ?: return@launch
                persist(dir.resolve("$id.json"), snapshot)
                removed.forEach { dir.resolve("$it.json").delete() }
            }
        }
        return id
    }

    @Synchronized
    fun peek(sessionId: String): List<String>? = sessions[sessionId]?.paths

    /** Observable snapshot used when a Viewer starts with one item and receives its swipe list
     * after navigation. The initial item is intentionally available immediately. */
    @Synchronized
    fun observe(sessionId: String): StateFlow<List<String>> =
        sessionFlows.getOrPut(sessionId) { MutableStateFlow(sessions[sessionId]?.paths.orEmpty()) }.asStateFlow()

    /** Replaces a launch snapshot after its background resolver has finished. */
    @Synchronized
    fun replace(sessionId: String, paths: List<String>) {
        val snapshot = paths.distinct()
        if (snapshot.isEmpty()) return
        val state = sessions[sessionId] ?: SessionState(snapshot).also { sessions[sessionId] = it }
        state.paths = snapshot
        state.hasPrevious = false
        state.hasNext = false
        sessionFlows.getOrPut(sessionId) { MutableStateFlow(snapshot) }.value = snapshot
        if (appContext != null) {
            ioScope.launch {
                val dir = resolveStorageDir() ?: return@launch
                persist(dir.resolve("$sessionId.json"), snapshot)
            }
        }
    }

    /** Creates a one-item session and fills it with a small DB-backed window after navigation. */
    @Synchronized
    fun createWindow(
        startPath: String,
        resolveWindow: suspend () -> ViewerPathWindow,
        loadMore: suspend (String, ViewerLoadDirection, Int) -> ViewerPathChunk,
    ): String {
        val id = create(listOf(startPath))
        sessions[id] = SessionState(listOf(startPath), loadMore = loadMore)
        ioScope.launch {
            val window = runCatching { resolveWindow() }.getOrNull() ?: return@launch
            replaceWindow(id, window)
        }
        return id
    }

    @Synchronized
    private fun replaceWindow(sessionId: String, window: ViewerPathWindow) {
        val state = sessions[sessionId] ?: return
        val paths = window.paths.distinct().takeIf { it.isNotEmpty() } ?: return
        state.paths = paths
        state.hasPrevious = window.hasPrevious
        state.hasNext = window.hasNext
        sessionFlows.getOrPut(sessionId) { MutableStateFlow(paths) }.value = paths
        if (appContext != null) {
            ioScope.launch {
                val dir = resolveStorageDir() ?: return@launch
                persist(dir.resolve("$sessionId.json"), paths)
            }
        }
    }

    /** Loads more paths at the edge of the current window, coalescing duplicate edge requests. */
    suspend fun loadMore(sessionId: String, anchorPath: String, direction: ViewerLoadDirection) {
        val key = "$sessionId:$direction"
        val loader = synchronized(this) {
            val state = sessions[sessionId] ?: return
            val enabled = if (direction == ViewerLoadDirection.BEFORE) state.hasPrevious else state.hasNext
            if (!enabled || !loadingDirections.add(key)) return
            state.loadMore
        } ?: return
        try {
            val chunk = runCatching { loader(anchorPath, direction, WINDOW_PAGE_SIZE) }
                .getOrElse { ViewerPathChunk(emptyList(), false) }
            synchronized(this) {
                val state = sessions[sessionId] ?: return@synchronized
                if (chunk.paths.isEmpty()) {
                    if (direction == ViewerLoadDirection.BEFORE) state.hasPrevious = false else state.hasNext = false
                    return@synchronized
                }
                state.paths = ViewerWindowMath.merge(state.paths, chunk.paths, direction, MAX_WINDOW_PATHS)
                if (direction == ViewerLoadDirection.BEFORE) state.hasPrevious = chunk.hasMore else state.hasNext = chunk.hasMore
                sessionFlows[sessionId]?.value = state.paths
                if (appContext != null) {
                    ioScope.launch {
                        val dir = resolveStorageDir() ?: return@launch
                        persist(dir.resolve("$sessionId.json"), state.paths)
                    }
                }
            }
        } finally {
            synchronized(this) { loadingDirections.remove(key) }
        }
    }

    /** Starts resolving the complete swipe list without delaying the first Viewer frame. */
    fun resolveInBackground(sessionId: String, resolver: suspend () -> List<String>) {
        val job = synchronized(this) {
            resolverJobs.remove(sessionId)?.cancel()
            ioScope.launch {
                val resolved = try {
                    resolver()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptyList()
                }
                if (resolved.isNotEmpty()) replace(sessionId, resolved)
            }.also { resolverJobs[sessionId] = it }
        }
        job.invokeOnCompletion {
            synchronized(this) {
                if (resolverJobs[sessionId] === job) resolverJobs.remove(sessionId)
            }
        }
    }

    /** Stops a large swipe-list query when the user closes the Viewer before it finishes. */
    fun cancelResolution(sessionId: String) {
        synchronized(this) { resolverJobs.remove(sessionId)?.cancel() }
    }

    /** Loads a snapshot after process death without blocking composition on file I/O. */
    suspend fun load(sessionId: String): List<String> = withContext(Dispatchers.IO) {
        synchronized(this@ViewerArgs) { sessions[sessionId]?.paths }?.let { return@withContext it }
        val file = resolveStorageDir()?.resolve("$sessionId.json") ?: return@withContext emptyList()
        if (!file.isFile) return@withContext emptyList()
        val loaded = runCatching {
            val json = JSONArray(file.readText())
            buildList(json.length()) { for (i in 0 until json.length()) add(json.getString(i)) }.distinct()
        }.getOrDefault(emptyList())
        if (loaded.isNotEmpty()) replace(sessionId, loaded)
        loaded
    }

    private fun persist(file: File, paths: List<String>) {
        runCatching {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(JSONArray(paths).toString())
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }
}

/** Keeps the potentially large media list out of the navigation route while still making the
 * viewer launch explicit and scoped to this particular navigation entry. */
fun NavHostController.navigateToViewer(paths: List<String>, startIndex: Int) {
    if (!ViewerNavigationGate.tryAcquire(this)) return
    val safeIndex = startIndex.coerceIn(0, (paths.size - 1).coerceAtLeast(0))
    try {
        navigate(Viewer(ViewerArgs.create(paths), safeIndex, paths.getOrNull(safeIndex).orEmpty()))
    } catch (t: Throwable) {
        ViewerNavigationGate.release(this)
        throw t
    }
}

/** Opens the tapped item immediately and resolves the complete swipe list off the navigation path.
 * This keeps large-library database work out of the click-to-view critical path. */
fun NavHostController.navigateToViewer(
    startPath: String,
    resolveWindow: (suspend () -> ViewerPathWindow)? = null,
    loadMore: (suspend (String, ViewerLoadDirection, Int) -> ViewerPathChunk)? = null,
) {
    if (!ViewerNavigationGate.tryAcquire(this)) return
    val sessionId = if (resolveWindow != null && loadMore != null) {
        ViewerArgs.createWindow(startPath, resolveWindow, loadMore)
    } else {
        ViewerArgs.create(listOf(startPath))
    }
    try {
        navigate(Viewer(sessionId, 0, startPath))
    } catch (t: Throwable) {
        ViewerNavigationGate.release(this)
        throw t
    }
}
