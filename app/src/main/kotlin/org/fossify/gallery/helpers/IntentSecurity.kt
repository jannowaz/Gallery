package org.fossify.gallery.helpers

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Security validation helpers for untrusted incoming Intents, URIs, and filesystem paths received
 * by exported Activities and BroadcastReceivers.
 */
object IntentSecurity {

    private val RESTRICTED_ROOT_PREFIXES = listOf(
        "/data/",
        "/proc/",
        "/sys/",
        "/dev/",
        "/system/",
        "/vendor/",
        "/apex/",
        "/product/",
    )

    private const val MAX_FILENAME_LENGTH = 128
    private const val MAX_FILE_PREFIX_LENGTH = 64
    private val UNSAFE_PREFIX_CHARS = Regex("[/\\\\:*?\"<>|\\u0000-\\u001F]")

    private val RESTRICTED_SELF_PROVIDER_SEGMENTS = setOf(
        "internal_files",
        "cache_files",
        "shared_images",
    )

    /**
     * Validates that [rawPath] is a safe, absolute, non-traversing path outside system directories
     * and outside this app's private storage directories.
     */
    fun isSafeExternalPath(context: Context?, rawPath: String?): Boolean {
        if (!isSyntacticallySafeExternalPath(rawPath)) return false
        val path = rawPath!!
        val canonical = runCatching { File(path).canonicalPath }.getOrNull() ?: return false
        if (!isSyntacticallySafeExternalPath(canonical)) return false
        if (context != null && isInsideAppPrivateStorage(context, canonical)) return false
        return true
    }

    /**
     * Pure syntactic/prefix check (usable in JVM unit tests without an Android [Context]).
     */
    internal fun isSyntacticallySafeExternalPath(rawPath: String?): Boolean {
        if (rawPath.isNullOrBlank()) return false
        if (!rawPath.startsWith("/")) return false
        if (rawPath.contains('\u0000') || rawPath.contains('\\')) return false
        if (rawPath.split('/').any { it == ".." }) return false
        val normalized = rawPath.trimEnd('/') + "/"
        return RESTRICTED_ROOT_PREFIXES.none { normalized.startsWith(it, ignoreCase = true) }
    }

    /**
     * Validates an incoming `file://` or `content://` URI received by an exported Activity.
     * Blocks unsupported schemes, path traversal, private app storage `file://` URIs, and
     * self-targeted `FileProvider` URIs pointing at internal/cache segments.
     */
    fun isSafeIncomingUri(context: Context, uri: Uri?): Boolean {
        if (uri == null) return false
        return when (uri.scheme?.lowercase()) {
            "file" -> isSafeExternalPath(context, uri.path)
            "content" -> !isRestrictedSelfProviderUri(context.packageName, uri)
            else -> false
        }
    }

    /**
     * Validates a writable output URI (such as [android.provider.MediaStore.EXTRA_OUTPUT]).
     * Rejects `file://` targets inside private/system paths and blocks self-targeted `FileProvider`
     * URIs so untrusted callers cannot trick the app into overwriting its own files.
     */
    fun isSafeOutputUri(context: Context, uri: Uri?): Boolean {
        if (uri == null) return false
        return when (uri.scheme?.lowercase()) {
            "file" -> isSafeExternalPath(context, uri.path)
            "content" -> {
                val ownAuthority = "${context.packageName}.provider"
                !uri.authority.equals(ownAuthority, ignoreCase = true)
            }
            else -> false
        }
    }

    /**
     * Sanitizes an untrusted filename (e.g., from a remote `ContentResolver` `DISPLAY_NAME`) so
     * it cannot escape its target directory via path separators or `..` segments.
     */
    fun sanitizeSingleFilename(rawName: String?, fallback: String): String {
        val leaf = rawName
            ?.replace('\\', '/')
            ?.substringAfterLast('/')
            ?.replace('\u0000', '_')
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it != "." && it != ".." }
            ?: return fallback
        return leaf.replace("..", "_").take(MAX_FILENAME_LENGTH).ifBlank { fallback }
    }

    /**
     * Sanitizes a user-supplied filename or archive subfolder prefix so it cannot contain path
     * separators, traversal segments, or control/wildcard characters.
     */
    fun sanitizeFilePrefix(raw: String): String {
        val cleaned = raw
            .replace("..", "")
            .replace(UNSAFE_PREFIX_CHARS, "_")
            .trim()
            .trim('.', '_')
        return cleaned.ifBlank { "IMG" }.take(MAX_FILE_PREFIX_LENGTH)
    }

    internal fun isRestrictedSelfProviderUri(packageName: String, uri: Uri): Boolean {
        val ownAuthority = "$packageName.provider"
        if (!uri.authority.equals(ownAuthority, ignoreCase = true)) return false
        val firstSegment = uri.pathSegments.firstOrNull()?.lowercase() ?: return true
        return firstSegment in RESTRICTED_SELF_PROVIDER_SEGMENTS
    }

    private fun isInsideAppPrivateStorage(context: Context, canonicalPath: String): Boolean {
        val privateRoots = buildList {
            runCatching { context.dataDir?.canonicalPath }.getOrNull()?.let(::add)
            runCatching { context.filesDir?.canonicalPath }.getOrNull()?.let(::add)
            runCatching { context.cacheDir?.canonicalPath }.getOrNull()?.let(::add)
            runCatching { context.noBackupFilesDir?.canonicalPath }.getOrNull()?.let(::add)
            runCatching { context.codeCacheDir?.canonicalPath }.getOrNull()?.let(::add)
        }
        return privateRoots.any { root ->
            val prefix = root.trimEnd('/')
            canonicalPath == prefix || canonicalPath.startsWith("$prefix/")
        }
    }
}
