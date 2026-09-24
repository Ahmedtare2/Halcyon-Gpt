package com.ella.music.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.PlaybackHistoryEntry
import com.ella.music.data.ActionMenuIds
import com.ella.music.data.ActionMenuLayout
import com.ella.music.data.artistNamesForSong
import com.ella.music.data.model.FolderPlaylist
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.model.formatPlaybackDuration
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.data.splitGenreNames
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaMiuixDialog
import com.ella.music.ui.components.SongMoreActionHost
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.TextField
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.preference.SwitchPreference
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class RecentPlaybackTab(val routeValue: String, val labelRes: Int) {
    Collection("collection", R.string.recent_playback_tab_collection),
    Song("song", R.string.recent_playback_tab_song),
    Mv("mv", R.string.recent_playback_tab_mv),
    Playlist("playlist", R.string.category_playlist),
    Artist("artist", R.string.category_artist),
    Album("album", R.string.category_album),
    Folder("folder", R.string.category_folder),
    FolderPlaylists("folder_playlists", R.string.folder_playlist_title),
    Year("year", R.string.category_year),
    Genre("genre", R.string.category_genre),
    Composer("composer", R.string.category_composer),
    Arranger("arranger", R.string.category_arranger),
    Lyricist("lyricist", R.string.category_lyricist);

    companion object {
        fun fromRoute(value: String?): RecentPlaybackTab =
            entries.firstOrNull { it.routeValue == value } ?: Collection
    }
}

private data class ResolvedRecentEntry(
    val entry: PlaybackHistoryEntry,
    val song: Song?
)

private data class RecentPlaybackRow(
    val key: String,
    val title: String,
    val subtitle: String,
    val playedAt: Long,
    val song: Song?,
    val entryIds: List<String>
)

@Composable
fun RecentPlaybackScreen(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    initialType: String? = null,
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit = {},
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {}
) {
    val songs by mainViewModel.songs.collectAsState()
    val history by mainViewModel.recentPlaybackHistory.collectAsState()
    val playlists by mainViewModel.playlists.collectAsState()
    val folderPlaylists by mainViewModel.settingsManager.folderPlaylists.collectAsState(initial = emptyList())
    var selectedTab by rememberSaveable(initialType) {
        mutableStateOf(RecentPlaybackTab.fromRoute(initialType).routeValue)
    }
    var showSettings by remember { mutableStateOf(false) }
    var clearRows by remember { mutableStateOf<List<RecentPlaybackRow>?>(null) }
    var deleteSingleEntryId by remember { mutableStateOf<String?>(null) }
    var deleteIdenticalEntryIds by remember { mutableStateOf<Set<String>?>(null) }
    var deleteIdenticalTitle by remember { mutableStateOf<String?>(null) }
    var actionSong by remember { mutableStateOf<Song?>(null) }
    var actionRecentRow by remember { mutableStateOf<RecentPlaybackRow?>(null) }
    val scope = rememberCoroutineScope()
    val currentTab = remember(selectedTab) { RecentPlaybackTab.fromRoute(selectedTab) }
    val rows by produceState(
        initialValue = emptyList<RecentPlaybackRow>(),
        history, songs, playlists, folderPlaylists, currentTab
    ) {
        value = withContext(Dispatchers.Default) {
            buildRecentPlaybackRows(history, songs, playlists, folderPlaylists, currentTab)
        }
    }
    val recentLimit by mainViewModel.settingsManager
        .recentPlaybackLimit(currentTab.routeValue)
        .collectAsState(initial = com.ella.music.data.SettingsManager.DEFAULT_RECENT_PLAYBACK_LIMIT)
    val showDate by mainViewModel.settingsManager
        .recentPlaybackShowDate(currentTab.routeValue)
        .collectAsState(initial = com.ella.music.data.SettingsManager.DEFAULT_RECENT_PLAYBACK_SHOW_DATE)
    val collectionTypes by mainViewModel.settingsManager.recentPlaybackCollectionTypes.collectAsState(
        initial = com.ella.music.data.SettingsManager.DEFAULT_RECENT_PLAYBACK_COLLECTION_TYPES.split(',').toSet()
    )
    val listActionMenuLayout by mainViewModel.settingsManager.listActionMenuLayout.collectAsState(initial = "")
    val visibleListActionIds = remember(listActionMenuLayout) {
        ActionMenuLayout.parse(listActionMenuLayout, ActionMenuIds.listDefaults)
            .visibleIds(ActionMenuIds.listDefaults)
    }
    val clearRecentPlaybackVisible = ActionMenuIds.CLEAR_RECENT_PLAYBACK in visibleListActionIds
    val visibleRows = remember(rows, recentLimit, collectionTypes, currentTab) {
        rows
            .filter { row ->
                currentTab != RecentPlaybackTab.Collection ||
                    row.key.substringBefore(':') in collectionTypes
            }
            .let { filtered ->
                if (recentLimit == com.ella.music.data.SettingsManager.RECENT_PLAYBACK_UNLIMITED) {
                    filtered
                } else {
                    filtered.take(recentLimit.coerceAtLeast(0))
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = MiuixIcons.Regular.Back,
                    contentDescription = stringResource(R.string.common_back),
                    tint = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = stringResource(R.string.recent_playback_title),
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )
            IconButton(onClick = { showSettings = true }) {
                Icon(
                    imageVector = MiuixIcons.Regular.Settings,
                    contentDescription = stringResource(R.string.recent_playback_settings),
                    tint = MiuixTheme.colorScheme.onBackground
                )
            }
            if (clearRecentPlaybackVisible) {
                IconButton(onClick = { clearRows = rows }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Delete,
                        contentDescription = stringResource(R.string.recent_playback_clear),
                        tint = MiuixTheme.colorScheme.onBackground
                    )
                }
            }
        }

        val tabScrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(tabScrollState)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RecentPlaybackTab.entries.forEach { tab ->
                val selected = tab == currentTab
                Text(
                    text = stringResource(tab.labelRes),
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .wrapContentWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (selected) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
                        )
                        .clickable { selectedTab = tab.routeValue }
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                )
            }
        }

        if (visibleRows.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.recent_playback_empty),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(18.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 160.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(visibleRows, key = { _, row -> row.key }) { _, row ->
                    RecentPlaybackRowCard(
                        row = row,
                        showDate = showDate,
                        mainViewModel = mainViewModel,
                        onPlay = {
                            row.song?.let { song ->
                                playerViewModel.playSongUncategorized(song)
                                onNavigateToPlayer()
                            }
                        },
                        onMore = {
                            row.song?.let {
                                actionSong = it
                                actionRecentRow = row
                            }
                        }
                    )
                }
            }
        }
    }

    SongMoreActionHost(
        actionSong = actionSong,
        mainViewModel = mainViewModel,
        playerViewModel = playerViewModel,
        onDismissAction = {
            actionSong = null
            actionRecentRow = null
        },
        onNavigateToAlbum = onNavigateToAlbum,
        onNavigateToArtist = onNavigateToArtist,
        onDeleteSingleRecentPlayback = actionRecentRow?.let { row ->
            val singleId = row.entryIds.firstOrNull()
            if (singleId != null) {
                {
                    actionSong = null
                    actionRecentRow = null
                    deleteSingleEntryId = singleId
                }
            } else null
        },
        onClearRecentPlayback = actionRecentRow?.let { row ->
            {
                val song = row.song
                val targetIds = if (song != null) {
                    history.filter { it.songId == song.id || (it.title == song.title && it.artist == song.artist) }
                        .map { it.entryId }
                        .toSet()
                        .ifEmpty { row.entryIds.toSet() }
                } else {
                    row.entryIds.toSet()
                }
                actionSong = null
                actionRecentRow = null
                deleteIdenticalEntryIds = targetIds
                deleteIdenticalTitle = row.title
            }
        }
    )

    var showCustomLimitDialog by remember { mutableStateOf(false) }
    var customLimitInput by remember { mutableStateOf("") }

    if (showSettings) {
        EllaMiuixBottomSheet(
            show = true,
            title = stringResource(R.string.recent_playback_settings),
            onDismissRequest = { showSettings = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.recent_playback_settings_count),
                    color = MiuixTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        com.ella.music.data.SettingsManager.DEFAULT_RECENT_PLAYBACK_LIMIT to stringResource(R.string.recent_playback_limit_100),
                        300 to stringResource(R.string.recent_playback_limit_300),
                        com.ella.music.data.SettingsManager.RECENT_PLAYBACK_UNLIMITED to stringResource(R.string.recent_playback_limit_unlimited)
                    ).forEach { (value, label) ->
                        val selected = recentLimit == value
                        Button(
                            onClick = {
                                scope.launch {
                                    mainViewModel.settingsManager.setRecentPlaybackLimit(currentTab.routeValue, value)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            minWidth = 0.dp,
                            cornerRadius = 14.dp,
                            colors = if (selected) {
                                ButtonDefaults.buttonColorsPrimary()
                            } else {
                                ButtonDefaults.buttonColors()
                            },
                            insideMargin = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Text(text = label)
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.recent_playback_limit_custom),
                        color = MiuixTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val currentVal = if (recentLimit <= 0) 300 else recentLimit
                                val nextVal = (currentVal - 20).coerceAtLeast(10)
                                scope.launch {
                                    mainViewModel.settingsManager.setRecentPlaybackLimit(currentTab.routeValue, nextVal)
                                }
                            },
                            minWidth = 36.dp,
                            cornerRadius = 10.dp,
                            colors = ButtonDefaults.buttonColors(),
                            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(text = "−", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Button(
                            onClick = {
                                customLimitInput = if (recentLimit <= 0) "100" else recentLimit.toString()
                                showCustomLimitDialog = true
                            },
                            cornerRadius = 10.dp,
                            colors = ButtonDefaults.buttonColors(),
                            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (recentLimit <= 0) stringResource(R.string.recent_playback_limit_unlimited) else recentLimit.toString(),
                                color = MiuixTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Button(
                            onClick = {
                                val currentVal = if (recentLimit <= 0) 100 else recentLimit
                                val nextVal = currentVal + 20
                                scope.launch {
                                    mainViewModel.settingsManager.setRecentPlaybackLimit(currentTab.routeValue, nextVal)
                                }
                            },
                            minWidth = 36.dp,
                            cornerRadius = 10.dp,
                            colors = ButtonDefaults.buttonColors(),
                            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(text = "+", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
                SwitchPreference(
                    checked = showDate,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            mainViewModel.settingsManager.setRecentPlaybackShowDate(currentTab.routeValue, enabled)
                        }
                    },
                    title = stringResource(R.string.recent_playback_show_date),
                    modifier = Modifier.fillMaxWidth()
                )
                if (currentTab == RecentPlaybackTab.Collection) {
                    Text(
                        text = stringResource(R.string.recent_playback_collection_types),
                        color = MiuixTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
                    )
                    RecentPlaybackTab.entries
                        .filter { it !in setOf(RecentPlaybackTab.Collection, RecentPlaybackTab.Song, RecentPlaybackTab.Mv) }
                        .forEach { tab ->
                            val enabled = tab.routeValue in collectionTypes
                            SwitchPreference(
                                checked = enabled,
                                onCheckedChange = { checked ->
                                    val updated = if (checked) collectionTypes + tab.routeValue
                                    else collectionTypes - tab.routeValue
                                    scope.launch {
                                        mainViewModel.settingsManager.setRecentPlaybackCollectionTypes(updated)
                                    }
                                },
                                title = stringResource(tab.labelRes),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                }
            }
        }
    }

    if (showCustomLimitDialog) {
        EllaMiuixDialog(
            show = true,
            title = stringResource(R.string.recent_playback_limit_custom),
            onDismissRequest = { showCustomLimitDialog = false }
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                TextField(
                    value = customLimitInput,
                    onValueChange = { customLimitInput = it.filter(Char::isDigit).take(5) },
                    label = "100",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showCustomLimitDialog = false },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = stringResource(R.string.common_cancel))
                    }
                    Button(
                        onClick = {
                            val parsed = customLimitInput.toIntOrNull()
                            if (parsed != null && parsed > 0) {
                                scope.launch {
                                    mainViewModel.settingsManager.setRecentPlaybackLimit(currentTab.routeValue, parsed)
                                }
                            }
                            showCustomLimitDialog = false
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColorsPrimary()
                    ) {
                        Text(text = stringResource(R.string.common_confirm))
                    }
                }
            }
        }
    }

    clearRows?.let { targetRows ->
        ConfirmDangerDialog(
            show = true,
            title = stringResource(R.string.recent_playback_clear),
            message = stringResource(R.string.recent_playback_clear_message, stringResource(currentTab.labelRes)),
            onDismiss = { clearRows = null },
            onConfirm = {
                val ids = targetRows.flatMap(RecentPlaybackRow::entryIds).toSet()
                clearRows = null
                scope.launch {
                    val targetEntries = history.filter { it.entryId in ids }
                    mainViewModel.removeRecentPlaybackHistoryEntries(targetEntries)
                }
            }
        )
    }

    deleteSingleEntryId?.let { entryId ->
        ConfirmDangerDialog(
            show = true,
            title = stringResource(R.string.recent_playback_delete_single),
            message = stringResource(R.string.recent_playback_delete_single_message),
            onDismiss = { deleteSingleEntryId = null },
            onConfirm = {
                val targetId = entryId
                deleteSingleEntryId = null
                scope.launch {
                    history.find { it.entryId == targetId }?.let { entry ->
                        mainViewModel.removeRecentPlaybackHistoryEntry(entry)
                    }
                }
            }
        )
    }

    deleteIdenticalEntryIds?.let { targetIds ->
        ConfirmDangerDialog(
            show = true,
            title = stringResource(R.string.recent_playback_delete_identical),
            message = stringResource(
                R.string.recent_playback_delete_identical_message,
                deleteIdenticalTitle ?: stringResource(currentTab.labelRes)
            ),
            onDismiss = {
                deleteIdenticalEntryIds = null
                deleteIdenticalTitle = null
            },
            onConfirm = {
                val ids = targetIds
                deleteIdenticalEntryIds = null
                deleteIdenticalTitle = null
                scope.launch {
                    val targetEntries = history.filter { it.entryId in ids }
                    mainViewModel.removeRecentPlaybackHistoryEntries(targetEntries)
                }
            }
        )
    }
}

@Composable
private fun RecentPlaybackRowCard(
    row: RecentPlaybackRow,
    showDate: Boolean,
    mainViewModel: MainViewModel,
    onPlay: () -> Unit,
    onMore: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnalyticsSongCover(
                song = row.song,
                mainViewModel = mainViewModel,
                modifier = Modifier.size(52.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = row.title,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = row.subtitle,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            if (showDate) {
                Text(
                    text = recentPlaybackDate(row.playedAt),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            IconButton(onClick = onMore) {
                Icon(
                    imageVector = MiuixIcons.Regular.More,
                    contentDescription = stringResource(R.string.song_more_actions_title),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
    }
}

private class RecentSongLookup(songs: List<Song>) {
    private val songsById = HashMap<Long, Song>(songs.size)
    private val songsByMeta = HashMap<String, Song>(songs.size)

    init {
        for (song in songs) {
            songsById[song.id] = song
            val key = metadataKey(song.title, song.artist, song.album)
            if (key.isNotEmpty()) {
                songsByMeta.putIfAbsent(key, song)
            }
        }
    }

    fun resolve(entry: PlaybackHistoryEntry): Song? {
        val metaKey = metadataKey(entry.title, entry.artist, entry.album)
        return (if (metaKey.isNotEmpty()) songsByMeta[metaKey] else null)
            ?: songsById[entry.songId]
    }

    companion object {
        fun metadataKey(title: String, artist: String, album: String): String {
            val t = title.trim().lowercase(Locale.ROOT)
            val a = artist.trim().lowercase(Locale.ROOT)
            val b = album.trim().lowercase(Locale.ROOT)
            if (t.isEmpty() && a.isEmpty() && b.isEmpty()) return ""
            return "$t\u0000$a\u0000$b"
        }
    }
}

private fun buildRecentPlaybackRows(
    history: List<PlaybackHistoryEntry>,
    songs: List<Song>,
    playlists: List<UserPlaylist>,
    folderPlaylists: List<FolderPlaylist>,
    tab: RecentPlaybackTab
): List<RecentPlaybackRow> {
    val lookup = RecentSongLookup(songs)
    val resolved = history
        .sortedByDescending(PlaybackHistoryEntry::playedAt)
        .map { entry -> ResolvedRecentEntry(entry, lookup.resolve(entry)) }
    return buildRecentPlaybackRowsForResolved(resolved, playlists, folderPlaylists, tab)
}

private fun buildRecentPlaybackRowsForResolved(
    resolved: List<ResolvedRecentEntry>,
    playlists: List<UserPlaylist>,
    folderPlaylists: List<FolderPlaylist>,
    tab: RecentPlaybackTab
): List<RecentPlaybackRow> {
    return when (tab) {
        RecentPlaybackTab.Song -> resolved.map { it.asSongRow() }
        RecentPlaybackTab.Mv -> resolved
            .filter { it.song?.isAudioMv() == true }
            .map(ResolvedRecentEntry::asMvRow)
        RecentPlaybackTab.Playlist -> buildPlaylistRows(resolved, playlists)
        RecentPlaybackTab.FolderPlaylists -> buildFolderPlaylistRows(resolved, folderPlaylists)
        RecentPlaybackTab.Collection -> listOf(
            RecentPlaybackTab.Playlist,
            RecentPlaybackTab.Artist,
            RecentPlaybackTab.Album,
            RecentPlaybackTab.Folder,
            RecentPlaybackTab.FolderPlaylists,
            RecentPlaybackTab.Year,
            RecentPlaybackTab.Genre,
            RecentPlaybackTab.Composer,
            RecentPlaybackTab.Arranger,
            RecentPlaybackTab.Lyricist
        ).flatMap { collectionTab ->
            buildRecentPlaybackRowsForResolved(resolved, playlists, folderPlaylists, collectionTab)
                .map { row -> row.copy(key = "${collectionTab.routeValue}:${row.key}") }
        }.sortedByDescending(RecentPlaybackRow::playedAt)
        RecentPlaybackTab.Artist -> groupRecentRows(resolved, tab) { song ->
            artistNamesForSong(song).ifEmpty { listOf(song.artist) }
        }
        RecentPlaybackTab.Album -> groupRecentRows(resolved, tab) { song ->
            listOf(song.album.ifBlank { "Unknown Album" })
        }
        RecentPlaybackTab.Folder -> groupRecentRows(resolved, tab) { song ->
            listOf(song.folderPathValue())
        }
        RecentPlaybackTab.Year -> groupRecentRows(resolved, tab) { song ->
            listOf(song.year.ifBlank { "Unknown" })
        }
        RecentPlaybackTab.Genre -> groupRecentRows(resolved, tab) { song ->
            splitGenreNames(song.genre).ifEmpty { listOf(song.genre) }
        }
        RecentPlaybackTab.Composer -> groupRecentRows(resolved, tab) { song ->
            listOf(song.composer.ifBlank { "Unknown" })
        }
        RecentPlaybackTab.Arranger -> groupRecentRows(resolved, tab) { song ->
            listOf(song.arranger.ifBlank { "Unknown" })
        }
        RecentPlaybackTab.Lyricist -> groupRecentRows(resolved, tab) { song ->
            listOf(song.lyricist.ifBlank { "Unknown" })
        }
    }
}

private fun ResolvedRecentEntry.asSongRow(): RecentPlaybackRow = RecentPlaybackRow(
    key = "song:${entry.entryId}",
    title = song?.title ?: entry.title,
    subtitle = listOf(song?.artist ?: entry.artist, song?.album ?: entry.album)
        .filter(String::isNotBlank)
        .joinToString(" · "),
    playedAt = entry.playedAt,
    song = song,
    entryIds = listOf(entry.entryId)
)

private fun ResolvedRecentEntry.asMvRow(): RecentPlaybackRow = RecentPlaybackRow(
    key = "mv:${entry.entryId}",
    title = song?.title ?: entry.title,
    subtitle = listOf(
        song?.artist ?: entry.artist,
        (song?.duration ?: entry.durationMs).takeIf { it > 0L }?.formatPlaybackDuration()
    ).filterNotNull().filter(String::isNotBlank).joinToString(" · "),
    playedAt = entry.playedAt,
    song = song,
    entryIds = listOf(entry.entryId)
)

private fun groupRecentRows(
    resolved: List<ResolvedRecentEntry>,
    tab: RecentPlaybackTab,
    keys: (Song) -> List<String>
): List<RecentPlaybackRow> {
    val groups = linkedMapOf<String, MutableList<ResolvedRecentEntry>>()
    resolved.forEach { item ->
        item.song?.let { song ->
            keys(song)
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct()
                .forEach { key -> groups.getOrPut(key) { mutableListOf() } += item }
        }
    }
    return groups.map { (name, entries) ->
        val latest = entries.maxBy(ResolvedRecentEntry::entryPlayedAt)
        RecentPlaybackRow(
            key = "${tab.routeValue}:${name.lowercase(Locale.getDefault())}",
            title = name,
            subtitle = entries.size.toString() + " · " + (latest.song?.artist ?: latest.entry.artist),
            playedAt = latest.entry.playedAt,
            song = latest.song,
            entryIds = entries.map { it.entry.entryId }.distinct()
        )
    }.sortedByDescending(RecentPlaybackRow::playedAt)
}

private fun buildPlaylistRows(
    resolved: List<ResolvedRecentEntry>,
    playlists: List<UserPlaylist>
): List<RecentPlaybackRow> {
    return playlists.mapNotNull { playlist ->
        val playlistKeys = playlist.songs.map { it.key }.toSet()
        val playlistIds = playlist.songs.map { it.id }.toSet()
        val matching = resolved.filter { item ->
            val song = item.song ?: return@filter false
            song.playlistIdentityKey() in playlistKeys || song.id in playlistIds
        }
        val latest = matching.maxByOrNull(ResolvedRecentEntry::entryPlayedAt) ?: return@mapNotNull null
        RecentPlaybackRow(
            key = "playlist:${playlist.id}",
            title = playlist.name,
            subtitle = "${matching.size} · ${latest.song?.title ?: latest.entry.title}",
            playedAt = latest.entry.playedAt,
            song = latest.song,
            entryIds = matching.map { it.entry.entryId }
        )
    }.sortedByDescending(RecentPlaybackRow::playedAt)
}

private fun buildFolderPlaylistRows(
    resolved: List<ResolvedRecentEntry>,
    folderPlaylists: List<FolderPlaylist>
): List<RecentPlaybackRow> = folderPlaylists.mapNotNull { playlist ->
    val matching = resolved.filter { item ->
        val song = item.song ?: return@filter false
        val folderPath = song.folderPathValue()
        playlist.folders.any { configured ->
            val normalized = configured.trim().trimEnd('/')
            folderPath.equals(normalized, ignoreCase = true) ||
                folderPath.startsWith("$normalized/", ignoreCase = true) ||
                folderPath.substringAfterLast('/').equals(normalized.substringAfterLast('/'), ignoreCase = true)
        }
    }
    val latest = matching.maxByOrNull(ResolvedRecentEntry::entryPlayedAt) ?: return@mapNotNull null
    RecentPlaybackRow(
        key = "folder_playlist:${playlist.id}",
        title = playlist.name,
        subtitle = "${matching.size} · ${latest.song?.title ?: latest.entry.title}",
        playedAt = latest.entry.playedAt,
        song = latest.song,
        entryIds = matching.map { it.entry.entryId }
    )
}.sortedByDescending(RecentPlaybackRow::playedAt)

private fun ResolvedRecentEntry.entryPlayedAt(): Long = entry.playedAt

private fun Song.isAudioMv(): Boolean =
    mimeType.startsWith("video/", ignoreCase = true) ||
        fileName.substringAfterLast('.', "").lowercase() in setOf("mp4", "m4v", "mkv", "webm", "mov")

private fun Song.folderPathValue(): String =
    path.substringBeforeLast('/', missingDelimiterValue = "").ifBlank { "Unknown" }

private fun recentPlaybackDate(timestampMs: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestampMs }
    return if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    ) {
        "今天"
    } else {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestampMs))
    }
}
