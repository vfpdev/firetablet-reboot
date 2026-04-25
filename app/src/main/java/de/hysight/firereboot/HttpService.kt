package de.hysight.firereboot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class HttpService : Service() {
    private var server: ServerSocket? = null
    @Volatile private var running = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        startServer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    private fun startInForeground() {
        val channelId = "http_server"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
        )
        val notif: Notification = Notification.Builder(this, channelId)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text_fmt, BuildConfig.REBOOT_PORT))
            .setSmallIcon(android.R.drawable.ic_lock_power_off)
            .build()
        startForeground(1, notif)
    }

    private fun startServer() {
        if (running) return
        thread(name = "http-accept", isDaemon = true) {
            try {
                // Bind first, then publish "running" — avoids onDestroy racing with unbound state.
                val s = ServerSocket(BuildConfig.REBOOT_PORT)
                server = s
                running = true
                while (running) {
                    val client = s.accept()
                    // Bound the time a slow/silent peer can occupy a worker thread (slow-loris guard).
                    client.soTimeout = SOCKET_TIMEOUT_MS
                    thread(isDaemon = true) { handle(client) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "server error", e)
            }
        }
    }

    private fun handle(client: Socket) {
        client.use { sock ->
            try {
                val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
                val writer = PrintWriter(sock.getOutputStream(), true)

                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) {
                    writeResponse(writer, 400, "Bad Request")
                    return
                }
                val method = parts[0]
                val path = parts[1]

                // Drain headers up to the empty line so the connection isn't kept half-read.
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                }

                if (method != "POST") {
                    writeResponse(writer, 404, "Not Found")
                    return
                }
                when (path) {
                    "/reboot" -> {
                        val acc = RebootAccessibilityService.instance
                        if (acc == null) {
                            writeResponse(writer, 503, "accessibility service not active")
                            return
                        }
                        writeResponse(writer, 200, "Rebooting...")
                        try {
                            // Brief delay so the HTTP response is flushed before the power menu
                            // takes over the screen and the accessibility click fires.
                            Thread.sleep(200)
                            acc.triggerReboot()
                        } catch (e: Exception) {
                            Log.e(TAG, "reboot failed", e)
                        }
                    }
                    "/screen/off" -> {
                        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                        val admin = ComponentName(this, AdminReceiver::class.java)
                        if (dpm.isAdminActive(admin)) {
                            writeResponse(writer, 200, "Screen off (admin)")
                            dpm.lockNow()
                        } else {
                            val acc = RebootAccessibilityService.instance
                            if (acc == null) {
                                writeResponse(writer, 503, "device admin or accessibility required")
                                return
                            }
                            writeResponse(writer, 200, "Screen off (a11y)")
                            acc.lockScreen()
                        }
                    }
                    "/screen/on" -> {
                        val i = Intent(this, WakeActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(i)
                        writeResponse(writer, 200, "Screen on")
                    }
                    else -> writeResponse(writer, 404, "Not Found")
                }
            } catch (e: Exception) {
                // Slow-loris timeout, malformed request, or any other I/O — drop quietly.
                Log.d(TAG, "client handler error: ${e.message}")
            }
        }
    }

    private fun writeResponse(writer: PrintWriter, code: Int, body: String) {
        val status = when (code) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            503 -> "Service Unavailable"
            else -> "Error"
        }
        val bytes = body.toByteArray()
        writer.println("HTTP/1.1 $code $status")
        writer.println("Content-Type: text/plain; charset=utf-8")
        writer.println("Content-Length: ${bytes.size}")
        writer.println("Connection: close")
        writer.println()
        // print() — not println() — so the body length matches Content-Length exactly.
        writer.print(body)
        writer.flush()
    }

    override fun onDestroy() {
        running = false
        try { server?.close() } catch (_: Exception) {}
        super.onDestroy()
    }

    companion object {
        private const val TAG = "HttpService"
        private const val SOCKET_TIMEOUT_MS = 5000
    }
}
