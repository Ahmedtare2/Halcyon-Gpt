package com.ella.music.mcp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.ella.music.MainActivity
import com.ella.music.R
import com.ella.music.data.repository.MusicRepository
import com.ella.music.player.ExoPlayerManager
import io.ktor.server.engine.embeddedServer
import io.ktor.server.cio.CIO
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground Service that runs the Halcyon MCP server via Ktor + Streamable HTTP.
 *
 * Connect from Claude Desktop or other MCP hosts:
 *   http://<device-ip>:8384/mcp
 */
class McpServerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverJob: io.ktor.server.engine.EmbeddedServer<*, *>? = null

    override fun onCreate() {
        super.onCreate()
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
            startMcpServer()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MCP service", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopMcpServer()
        scope.cancel()
    }

    private fun startMcpServer() {
        scope.launch {
            try {
                val playerManager = ExoPlayerManager(this@McpServerService)
                playerManager.connect()
                val repository = MusicRepository.getInstance(this@McpServerService)
                val mcpServer = HalcyonMcpServer(playerManager, repository)

                serverJob = embeddedServer(CIO, host = "0.0.0.0", port = PORT) {
                    // DNS-rebinding protection defaults to allowing only localhost hosts, which
                    // rejects the documented LAN usage (http://<device-ip>:8384/mcp) with a 403
                    // "Invalid Host". This server binds 0.0.0.0 by explicit user opt-in and is
                    // reached by device IP from another LAN device, so disable the localhost guard.
                    mcpStreamableHttp(path = "/mcp", enableDnsRebindingProtection = false) { mcpServer.server }
                }.start(wait = true)

                Log.i(TAG, "MCP server started on port $PORT")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start MCP server", e)
            }
        }
    }

    private fun stopMcpServer() {
        serverJob?.stop(gracePeriodMillis = 1000)
        serverJob = null
        Log.i(TAG, "MCP server stopped")
    }

    private fun buildNotification(): Notification {
        createNotificationChannel()

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Halcyon MCP Server")
            .setContentText("Listening on port $PORT")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "MCP Server",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Halcyon MCP server notification"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "McpServerService"
        private const val CHANNEL_ID = "mcp_server"
        private const val NOTIFICATION_ID = 7700
        const val PORT = 8384

        fun start(context: Context) {
            val intent = Intent(context, McpServerService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, McpServerService::class.java))
        }

        fun isRunning(): Boolean {
            // Simple check - in production you'd use a static flag
            return false
        }
    }
}
