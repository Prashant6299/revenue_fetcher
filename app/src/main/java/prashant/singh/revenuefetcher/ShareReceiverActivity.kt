package prashant.singh.revenuefetcher

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity

class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Forward the share intent to the service
        if (intent?.action == Intent.ACTION_SEND) {
            val serviceIntent = Intent(this, ShareHandlerService::class.java).apply {
                action = intent.action
                type = intent.type
                intent.extras?.let { putExtras(it) }
            }
            Log.d("TestRevenue:", "activity");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d("TestRevenue:", "activity 1");

                startForegroundService(serviceIntent)
            } else {
                Log.d("TestRevenue:", "activity 2");

                startService(serviceIntent)
            }
        }

        // Finish immediately
        finish()
    }
}