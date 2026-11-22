package org.fossify.gallery.compose.screens.settings
import androidx.compose.ui.res.stringResource
import org.fossify.gallery.R
import org.fossify.gallery.compose.theme.Radius
import org.fossify.gallery.helpers.CrashLogger
import org.fossify.gallery.helpers.SettingsBackupHelper
import androidx.compose.ui.text.font.FontFamily

import android.app.Activity
import android.content.Context
import android.content.Intent
import org.fossify.gallery.workers.MetadataSyncWorker
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.commons.helpers.VIEW_TYPE_LIST
import org.fossify.gallery.compose.components.LibrarySearchField
import org.fossify.gallery.compose.components.LibrarySectionHeader
import org.fossify.gallery.helpers.Config
import org.fossify.gallery.helpers.LibraryActivityKind
import org.fossify.gallery.helpers.LibraryActivityPhase
import org.fossify.gallery.helpers.LibraryActivityStatus
import org.fossify.gallery.extensions.config

private val LocalSettingsQuery = androidx.compose.runtime.compositionLocalOf { "" }
private class SettingsMatchTracker {
    var matchedCount = 0
}
private val LocalSettingsMatchTracker = androidx.compose.runtime.compositionLocalOf<SettingsMatchTracker?> { null }

private val EXPANDED_SETTINGS_BREAKPOINT = 840.dp
private val SETTINGS_RAIL_WIDTH = 280.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onNavigateToAbout: () -> Unit = {}) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    val conf = ctx.config
    val scope = rememberCoroutineScope()
    var showExtendedDialog by remember { mutableStateOf(false) }
    var showThemeModeDialog by remember { mutableStateOf(false) }
    var showFolderTypeDialog by remember { mutableStateOf(false) }
    var manageFoldersMode by remember { mutableStateOf<String?>(null) }
    var settingsQuery by rememberSaveable { mutableStateOf("") }
    var settingsVersion by remember { mutableIntStateOf(0) }
    var expandedSections by rememberSaveable { mutableStateOf(setOf(SettingsSection.GENERAL.key)) }
    var selectedSectionKey by rememberSaveable { mutableStateOf(SettingsSection.GENERAL.key) }
    val selectedSection = remember(selectedSectionKey) {
        SettingsSection.entries.firstOrNull { it.key == selectedSectionKey } ?: SettingsSection.GENERAL
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val activityStates by LibraryActivityStatus.states.collectAsStateWithLifecycle()
    val isMetadataScanRunning = activityStates.any {
        it.kind == LibraryActivityKind.METADATA_SCAN && it.phase == LibraryActivityPhase.RUNNING
    }
    fun toggleSection(key: String) {
        expandedSections = if (key in expandedSections) expandedSections - key else expandedSections + key
    }
    val sectionCallbacks = remember(onNavigateToAbout) {
        SettingsSectionCallbacks(
            onOpenThemeMode = { showThemeModeDialog = true },
            onOpenFolderType = { showFolderTypeDialog = true },
            onManageFolders = { manageFoldersMode = it },
            onOpenExtendedDetails = { showExtendedDialog = true },
            onNavigateToAbout = onNavigateToAbout,
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_settings), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        val layoutDirection = LocalLayoutDirection.current
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .consumeWindowInsets(padding)
                .imePadding(),
        ) {
            val isTwoPane = maxWidth >= EXPANDED_SETTINGS_BREAKPOINT
            val matchTracker = SettingsMatchTracker()
            CompositionLocalProvider(
                LocalSettingsQuery provides settingsQuery.trim(),
                LocalSettingsMatchTracker provides matchTracker,
            ) {
                @Suppress("UNUSED_EXPRESSION") settingsVersion
                if (isTwoPane) {
                    SettingsTwoPaneBody(
                        selectedSection = selectedSection,
                        settingsQuery = settingsQuery,
                        onQueryChange = { settingsQuery = it },
                        onSelectSection = { section ->
                            selectedSectionKey = section.key
                            if (settingsQuery.isNotBlank()) settingsQuery = ""
                        },
                        matchTracker = matchTracker,
                        startPadding = padding.calculateStartPadding(layoutDirection),
                        endPadding = padding.calculateEndPadding(layoutDirection),
                        bottomPadding = padding.calculateBottomPadding(),
                        ctx = ctx,
                        activity = activity,
                        conf = conf,
                        scope = scope,
                        snackbarHostState = snackbarHostState,
                        isMetadataScanRunning = isMetadataScanRunning,
                        callbacks = sectionCallbacks,
                    )
                } else {
                    SettingsSinglePaneBody(
                        expandedSections = expandedSections,
                        settingsQuery = settingsQuery,
                        onQueryChange = { settingsQuery = it },
                        onToggleSection = ::toggleSection,
                        matchTracker = matchTracker,
                        startPadding = padding.calculateStartPadding(layoutDirection),
                        endPadding = padding.calculateEndPadding(layoutDirection),
                        bottomPadding = padding.calculateBottomPadding(),
                        ctx = ctx,
                        activity = activity,
                        conf = conf,
                        scope = scope,
                        snackbarHostState = snackbarHostState,
                        isMetadataScanRunning = isMetadataScanRunning,
                        callbacks = sectionCallbacks,
                    )
                }
            }
        }
    }

    if (showExtendedDialog) {
        ExtendedDetailsDialog(
            current = conf.extendedDetails,
            onDismiss = { showExtendedDialog = false },
            onSave = { conf.extendedDetails = it }
        )
    }

    if (showThemeModeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeModeDialog = false },
            title = { Text(stringResource(R.string.theme_mode_label)) },
            text = {
                Column {
                    listOf(
                        stringResource(R.string.theme_mode_light) to 1,
                        stringResource(R.string.theme_mode_dark) to 2,
                        stringResource(R.string.theme_mode_system) to 0,
                    ).forEach { (label, mode) ->
                        val selected = when (mode) {
                            1 -> conf.forceLightMode
                            2 -> conf.forceDarkMode
                            else -> !conf.forceLightMode && !conf.forceDarkMode
                        }
                        TextButton(
                            onClick = {
                                conf.forceLightMode = mode == 1
                                conf.forceDarkMode = mode == 2
                                org.fossify.gallery.compose.theme.ThemePrefsBus.invalidate()
                                showThemeModeDialog = false
                                settingsVersion++
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                label,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Start,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showThemeModeDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (showFolderTypeDialog) {
        SettingsChoiceDialog(
            title = stringResource(R.string.set_folder_type),
            selectedValue = conf.viewTypeFolders,
            options = listOf(
                VIEW_TYPE_GRID to stringResource(R.string.view_type_grid),
                VIEW_TYPE_LIST to stringResource(R.string.view_type_list),
            ),
            onSelected = { value -> conf.viewTypeFolders = value; settingsVersion++; showFolderTypeDialog = false },
            onDismiss = { showFolderTypeDialog = false },
        )
    }

    manageFoldersMode?.let { mode ->
        ManageFoldersDialog(
            isExcluded = mode == "excluded",
            onChanged = { settingsVersion++ },
            onDismiss = { manageFoldersMode = null },
        )
    }
}

@Composable
private fun SettingsSinglePaneBody(
    expandedSections: Set<String>,
    settingsQuery: String,
    onQueryChange: (String) -> Unit,
    onToggleSection: (String) -> Unit,
    matchTracker: SettingsMatchTracker,
    startPadding: Dp,
    endPadding: Dp,
    bottomPadding: Dp,
    ctx: Context,
    activity: Activity?,
    conf: Config,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    isMetadataScanRunning: Boolean,
    callbacks: SettingsSectionCallbacks,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 16.dp + startPadding,
                end = 16.dp + endPadding,
                top = 8.dp,
                bottom = 8.dp + bottomPadding,
            ),
    ) {
        LibrarySearchField(
            value = settingsQuery,
            onValueChange = onQueryChange,
            placeholderRes = R.string.settings_search_hint,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        val sections = SettingsSection.entries
        sections.forEachIndexed { index, section ->
            val isExpanded = section.key in expandedSections || settingsQuery.isNotBlank()
            SettingsCategory(
                title = stringResource(section.titleRes),
                query = settingsQuery,
                expanded = section.key in expandedSections,
                onToggle = { onToggleSection(section.key) },
            )
            if (isExpanded) {
                SettingsSectionContent(
                    section = section,
                    ctx = ctx,
                    activity = activity,
                    conf = conf,
                    scope = scope,
                    snackbarHostState = snackbarHostState,
                    isMetadataScanRunning = isMetadataScanRunning,
                    callbacks = callbacks,
                )
                if (index < sections.lastIndex) SettingsDivider()
            }
        }
        if (settingsQuery.isNotBlank() && matchTracker.matchedCount == 0) {
            SettingsNoResultsMessage()
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsTwoPaneBody(
    selectedSection: SettingsSection,
    settingsQuery: String,
    onQueryChange: (String) -> Unit,
    onSelectSection: (SettingsSection) -> Unit,
    matchTracker: SettingsMatchTracker,
    startPadding: Dp,
    endPadding: Dp,
    bottomPadding: Dp,
    ctx: Context,
    activity: Activity?,
    conf: Config,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    isMetadataScanRunning: Boolean,
    callbacks: SettingsSectionCallbacks,
) {
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .width(SETTINGS_RAIL_WIDTH + startPadding)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp + startPadding,
                    end = 12.dp,
                    top = 8.dp,
                    bottom = 16.dp + bottomPadding,
                ),
        ) {
            LibrarySearchField(
                value = settingsQuery,
                onValueChange = onQueryChange,
                placeholderRes = R.string.settings_search_hint,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            SettingsSection.entries.forEach { section ->
                val isSelected = settingsQuery.isBlank() && section == selectedSection
                Surface(
                    onClick = { onSelectSection(section) },
                    shape = RoundedCornerShape(Radius.md),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .heightIn(min = 48.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(section.titleRes),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 24.dp,
                    end = 24.dp + endPadding,
                    top = 12.dp,
                    bottom = 32.dp + bottomPadding,
                ),
        ) {
            if (settingsQuery.isBlank()) {
                Text(
                    text = stringResource(selectedSection.titleRes),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                SettingsSectionContent(
                    section = selectedSection,
                    ctx = ctx,
                    activity = activity,
                    conf = conf,
                    scope = scope,
                    snackbarHostState = snackbarHostState,
                    isMetadataScanRunning = isMetadataScanRunning,
                    callbacks = callbacks,
                )
            } else {
                SettingsSection.entries.forEach { section ->
                    SettingsSectionContent(
                        section = section,
                        ctx = ctx,
                        activity = activity,
                        conf = conf,
                        scope = scope,
                        snackbarHostState = snackbarHostState,
                        isMetadataScanRunning = isMetadataScanRunning,
                        callbacks = callbacks,
                    )
                }
                if (matchTracker.matchedCount == 0) {
                    SettingsNoResultsMessage()
                }
            }
        }
    }
}

@Composable
private fun SettingsNoResultsMessage() {
    Text(
        text = stringResource(R.string.no_results),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
internal fun CrashLogNav(ctx: Context) {
    var logs by remember { mutableStateOf(emptyList<java.io.File>()) }
    LaunchedEffect(Unit) { logs = withContext(Dispatchers.IO) { CrashLogger.logs(ctx) } }
    var showDialog by remember { mutableStateOf(false) }

    val latest = logs.firstOrNull()
    SettingsNav(
        stringResource(R.string.set_crash_log),
        latest?.name?.removePrefix("crash_")?.removeSuffix(".txt")?.replace('_', ' ')
            ?: stringResource(R.string.crash_log_empty),
    ) {
        if (latest != null) showDialog = true
    }

    if (showDialog && latest != null) {
        val text = remember(latest) { runCatching { latest.readText() }.getOrDefault("") }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.set_crash_log)) },
            text = {
                Box(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    Text(text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, latest.name)
                        .putExtra(Intent.EXTRA_TEXT, text)
                    ctx.startActivity(Intent.createChooser(intent, null))
                }) { Text(stringResource(R.string.action_share)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    CrashLogger.clear(ctx)
                    logs = emptyList()
                    showDialog = false
                }) { Text(stringResource(org.fossify.commons.R.string.delete)) }
            },
        )
    }
}

@Composable
internal fun SectionLabel(text: String) {
    if (LocalSettingsQuery.current.isNotBlank()) return
    LibrarySectionHeader(
        title = text,
        showDivider = false,
        topPadding = 20.dp,
        bottomPadding = 8.dp,
    )
}

/** Collapsed by default to keep the settings landing page scannable. A text search deliberately
 * reveals every matching category, so a setting never becomes harder to find. */
@Composable
private fun SettingsCategory(
    title: String,
    query: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    if (query.isNotBlank()) {
        SectionLabel(title)
        return
    }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "settingsCategoryChevron",
    )
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp)
            .heightIn(min = 48.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(chevronRotation),
            )
        }
    }
}

@Composable
private fun SettingsDivider() {
    if (LocalSettingsQuery.current.isBlank()) {
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
    }
}

@Composable
internal fun SettingsSwitch(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    if (!settingsMatches(label)) return
    var internalChecked by remember(checked) { mutableStateOf(checked) }
    val toggle: (Boolean) -> Unit = { value -> internalChecked = value; onChange(value) }
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .heightIn(min = 48.dp)
            .toggleable(value = internalChecked, enabled = enabled, role = Role.Switch, onValueChange = toggle),
        shape = RoundedCornerShape(Radius.sm),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.weight(1f))
            Switch(checked = internalChecked, enabled = enabled, onCheckedChange = null)
        }
    }
}

/** Like [SettingsSwitch] but stateless - the caller owns [checked], so it can veto/revert a toggle
 * (e.g. when a password prompt behind the switch is cancelled). */
@Composable
internal fun SettingsSwitchControlled(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    if (!settingsMatches(label)) return
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onToggle),
        shape = RoundedCornerShape(Radius.sm),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

@Composable
internal fun SettingsNav(label: String, onClick: () -> Unit) {
    if (!settingsMatches(label)) return
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.sm),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SettingsNav(label: String, subtitle: String, onClick: () -> Unit) {
    if (!settingsMatches(label, subtitle)) return
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.sm),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SecurityNav(label: String, enabled: Boolean, onClick: () -> Unit) {
    if (!settingsMatches(label)) return
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.sm),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                if (enabled) stringResource(R.string.set_on) else stringResource(R.string.set_off),
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (enabled) FontWeight.SemiBold else FontWeight.Normal,
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun settingsMatches(label: String, subtitle: String = ""): Boolean {
    val query = LocalSettingsQuery.current
    val matches = query.isBlank() || label.contains(query, ignoreCase = true) || subtitle.contains(query, ignoreCase = true)
    if (matches && query.isNotBlank()) {
        LocalSettingsMatchTracker.current?.let { it.matchedCount++ }
    }
    return matches
}
