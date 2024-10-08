package prashant.singh.revenuefetcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class ShareHandlerService : Service() {
    private val CHANNEL_ID = "ShareHandlerChannel"
    private val FOREGROUND_NOTIFICATION_ID = 1
    private val RESULT_NOTIFICATION_ID = 2
    private var timeoutHandler: Handler? = null
    private val TIMEOUT_DELAY = 5000L // 5 seconds
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val client = OkHttpClient()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        timeoutHandler = Handler(Looper.getMainLooper())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Create a minimal foreground notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Processing")
            .setContentText("Please wait...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Lower priority for this temporary notification
            .setOngoing(true)
            .build()

        startForeground(FOREGROUND_NOTIFICATION_ID, notification)

        timeoutHandler?.postDelayed({
            Log.w("TestRevenue:", "Safety timeout triggered - stopping service")
            serviceScope.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }, TIMEOUT_DELAY)

        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            val packageId = extractAppIdFromUrl(sharedText.toString())

            if (packageId != null) {
                makeApiRequest(packageId, sharedText.toString())
            } else {
                handleSharedText(sharedText)
            }
        }

        return START_NOT_STICKY
    }

    private fun makeApiRequest(appId: String, originalUrl: String) {
        val request = Request.Builder()
            .url("https://app.sensortower.com/api/android/apps?app_ids=$appId")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("TestRevenue:", "API request failed", e)
                handleSharedText(originalUrl)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        responseBody?.let {
                            val appInfo = parseAppInfo(it)
                            sendAppInfoNotification(appInfo, originalUrl)
                        }
                    } else {
                        Log.e("TestRevenue:", "API request failed with code: ${response.code}")
                        handleSharedText(originalUrl)
                    }
                } catch (e: Exception) {
                    Log.e("TestRevenue:", "Error processing API response", e)
                    handleSharedText(originalUrl)
                } finally {
                    response.close()
                }
            }
        })
    }

    data class AppInfo(
        val name: String,
        val downloads: String,
        val revenue: String
    )

    private fun parseAppInfo(jsonResponse: String): AppInfo {
        val jsonObject = JSONObject(jsonResponse)
        val appArray = jsonObject.getJSONArray("apps")
        val app = appArray.getJSONObject(0)

        val name = app.getString("name")
        val downloads = app.getJSONObject("humanized_worldwide_last_month_downloads")
            .getString("string")
        val revenue = app.getJSONObject("humanized_worldwide_last_month_revenue")
            .getString("string")

        return AppInfo(name, downloads, revenue)
    }

    private fun sendAppInfoNotification(appInfo: AppInfo, originalUrl: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)

        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notificationText = """
            Monthly Downloads: ${appInfo.downloads} & Revenue: ${appInfo.revenue}
        """.trimIndent()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("App: ${appInfo.name}")
            .setContentText("${appInfo.name} - Downloads: ${appInfo.downloads}, Revenue: ${appInfo.revenue}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // Stop foreground service with removal of notification first
        stopForeground(STOP_FOREGROUND_REMOVE)

        // Then show the result notification
        notificationManager.notify(RESULT_NOTIFICATION_ID, notification)

        // Clear timeout and stop service
        timeoutHandler?.removeCallbacksAndMessages(null)
        stopSelf()
    }

    private fun handleSharedText(sharedText: String?) {
        serviceScope.launch {
            try {
                sharedText?.let {
                    sendSharedContentNotification(it)
                }
            } finally {
                timeoutHandler?.removeCallbacksAndMessages(null)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun sendSharedContentNotification(content: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)

        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Something went wrong")
            .setContentText("")
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(RESULT_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Share Handler Channel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for showing shared content"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    fun extractAppIdFromUrl(url: String): String? {
        val uri = Uri.parse(url)
        return uri.getQueryParameter("id")
    }

    override fun onDestroy() {
        super.onDestroy()
        timeoutHandler?.removeCallbacksAndMessages(null)
        timeoutHandler = null
        serviceScope.cancel()
    }
}