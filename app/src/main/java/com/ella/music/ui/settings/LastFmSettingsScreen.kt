package com.ella.music.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.lastfm.LastFmHistoryStore
import com.ella.music.data.lastfm.LastFmSyncStatus
import com.ella.music.data.lastfm.ListeningHistorySource
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.ui.components.ellaPageBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Last.fm deliberately uses user supplied application credentials. A shared secret must never be
 * bundled with a client APK because it would immediately be public and let third parties sign
 * requests as the application.
 */
@Composable
fun LastFmSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val historyStore = remember { LastFmHistoryStore.getInstance(context) }
    val credentials by historyStore.credentials.collectAsState()
    val sourcePreference by settingsManager.listeningHistorySource.collectAsState(
        initial = SettingsManager.LISTENING_HISTORY_SOURCE_LOCAL
    )
    val syncStatus by historyStore.syncStatus.collectAsState()
    val pendingScrobbles by historyStore.pendingScrobbles.collectAsState()
    val sourceOptions = listOf(
        ListeningHistorySource.Local to stringResource(R.string.lastfm_history_source_local),
        ListeningHistorySource.LastFm to stringResource(R.string.lastfm_history_source_lastfm),
        ListeningHistorySource.Combined to stringResource(R.string.lastfm_history_source_combined)
    )
    val selectedSourceIndex = sourceOptions.indexOfFirst { it.first.preferenceValue == sourcePreference }
        .coerceAtLeast(0)

    fun showResult(action: suspend () -> Unit) {
        scope.launch(Dispatchers.IO) {
            runCatching { action() }
                .onSuccess {
                    launch(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.lastfm_action_success), Toast.LENGTH_SHORT).show()
                    }
                }
                .onFailure { error ->
                    launch(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.lastfm_action_failed, error.message.orEmpty()),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = stringResource(R.string.settings_lastfm),
            color = ellaPageBackground(),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = stringResource(R.string.common_back),
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            SmallTitle(text = stringResource(R.string.lastfm_history_source))
            SettingsCardGroup {
                WindowSpinnerPreference(
                    title = stringResource(R.string.lastfm_history_source),
                    summary = stringResource(R.string.lastfm_history_source_summary),
                    items = sourceOptions.map { DropdownItem(title = it.second) },
                    selectedIndex = selectedSourceIndex,
                    onSelectedIndexChange = { index ->
                        sourceOptions.getOrNull(index)?.first?.let { source ->
                            scope.launch { settingsManager.setListeningHistorySource(source.preferenceValue) }
                        }
                    }
                )
            }

            SmallTitle(text = stringResource(R.string.lastfm_connection))
            SettingsCardGroup {
                Column {
                    SplitSettingTextField(
                        label = stringResource(R.string.lastfm_api_key),
                        value = credentials.apiKey,
                        summary = stringResource(R.string.lastfm_api_key_summary),
                        singleLine = true,
                        onValueChange = { value ->
                            historyStore.updateAppCredentials(value, credentials.sharedSecret)
                        }
                    )
                    SplitSettingTextField(
                        label = stringResource(R.string.lastfm_shared_secret),
                        value = credentials.sharedSecret,
                        summary = stringResource(R.string.lastfm_shared_secret_summary),
                        singleLine = true,
                        isPassword = true,
                        onValueChange = { value ->
                            historyStore.updateAppCredentials(credentials.apiKey, value)
                        }
                    )
                    BasicComponent(
                        title = if (credentials.isAuthorized) {
                            stringResource(R.string.lastfm_connected_as, credentials.username)
                        } else {
                            stringResource(R.string.lastfm_not_connected)
                        },
                        summary = syncStatus.lastFmSummary(pendingScrobbles.size)
                    )
                    ArrowPreference(
                        title = stringResource(R.string.lastfm_open_authorization),
                        summary = stringResource(R.string.lastfm_open_authorization_summary),
                        onClick = {
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) { historyStore.beginAuthorization() }
                                }.onSuccess { authorization ->
                                    authorization.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(authorization)
                                    Toast.makeText(
                                        context,
                                        R.string.lastfm_action_success,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }.onFailure { error ->
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            R.string.lastfm_action_failed,
                                            error.message.orEmpty()
                                        ),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    )
                    if (credentials.pendingToken.isNotBlank()) {
                        ArrowPreference(
                            title = stringResource(R.string.lastfm_complete_authorization),
                            summary = stringResource(R.string.lastfm_complete_authorization_summary),
                            onClick = { showResult { historyStore.completeAuthorizationAndSync() } }
                        )
                    }
                    if (credentials.isAuthorized) {
                        ArrowPreference(
                            title = stringResource(R.string.lastfm_sync_all_history),
                            summary = stringResource(R.string.lastfm_sync_all_history_summary),
                            onClick = { showResult { historyStore.syncAllHistory() } }
                        )
                        ArrowPreference(
                            title = stringResource(R.string.lastfm_retry_scrobbles),
                            summary = stringResource(R.string.lastfm_retry_scrobbles_summary, pendingScrobbles.size),
                            onClick = { showResult { historyStore.flushPendingScrobbles() } }
                        )
                        ArrowPreference(
                            title = stringResource(R.string.lastfm_disconnect),
                            summary = stringResource(R.string.lastfm_disconnect_summary),
                            onClick = {
                                historyStore.clearAuthorization()
                                Toast.makeText(context, R.string.lastfm_disconnected, Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(160.dp))
        }
    }
}

@Composable
private fun LastFmSyncStatus.lastFmSummary(pendingCount: Int): String = when (this) {
    LastFmSyncStatus.Idle -> stringResource(R.string.lastfm_sync_idle, pendingCount)
    is LastFmSyncStatus.Syncing -> stringResource(
        R.string.lastfm_syncing,
        page,
        totalPages.coerceAtLeast(page),
        receivedTracks
    )
    is LastFmSyncStatus.Complete -> stringResource(R.string.lastfm_sync_complete, totalTracks, pendingCount)
    is LastFmSyncStatus.Failed -> stringResource(R.string.lastfm_sync_failed, message)
}
