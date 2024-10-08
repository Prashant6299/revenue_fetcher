package prashant.singh.revenuefetcher

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import prashant.singh.revenuefetcher.ui.theme.Revenue_fetcherTheme

class MainActivity : ComponentActivity() {

    // State to track whether the permission is granted or not
    private var isPermissionGranted = mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("NotificationPermission", "Granted")
            isPermissionGranted.value = true  // Update the UI state
        } else {
            Log.d("NotificationPermission", "Denied")
            isPermissionGranted.value = false  // Update the UI state
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check and request notification permission when app starts
        checkAndRequestNotificationPermission()

        // Set the content with Compose UI
        setContent {
            Revenue_fetcherTheme {
                NotificationPermissionUI(
                    isPermissionGranted = isPermissionGranted,
                    onRequestPermission = { checkAndRequestNotificationPermission() }  // Call permission check when the button is clicked
                )
            }
        }
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d("NotificationPermission", "Already granted")
                    isPermissionGranted.value = true
                }

                shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS) -> {
                    showNotificationPermissionRationale()
                }

                else -> {
                    requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // If the SDK version is below TIRAMISU (Android 13), no permission is required for notifications
            isPermissionGranted.value = true
        }
    }

    private fun showNotificationPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle("Notification Permission Required")
            .setMessage("This app needs notification permission to keep you updated about important events.")
            .setPositiveButton("Grant Permission") { dialog, _ ->
                dialog.dismiss()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            .setNegativeButton("Not Now") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }
}

@Composable
fun NotificationPermissionUI(
    isPermissionGranted: State<Boolean>,
    onRequestPermission: () -> Unit // Callback to trigger permission request
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isPermissionGranted.value) {
                    "Notification Permission Granted"
                } else {
                    "Notification Permission Denied"
                },
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Display button to request permission only if permission is not granted
            if (!isPermissionGranted.value) {
                Button(onClick = {
                    onRequestPermission()  // Call the function to request permission
                }) {
                    Text("Request Notification Permission")
                }
            }
        }
    }
}

@SuppressLint("UnrememberedMutableState")
@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    Revenue_fetcherTheme {
        NotificationPermissionUI(
            isPermissionGranted = mutableStateOf(false),
            onRequestPermission = {}
        )
    }
}
