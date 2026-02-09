package aer.app.navhud

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import aer.app.navhud.ui.theme.NavHUDTheme

class MainActivity : ComponentActivity() {

    private lateinit var bluetoothManager: BluetoothSppManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allPermissionsGranted = permissions.all { it.value }
        if (allPermissionsGranted) {
            bluetoothManager.startConnectionLoop()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bluetoothManager = (application as NavHudApplication).bluetoothManager
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Request necessary permissions on startup
        requestBluetoothPermissions()

        setContent {
            NavHUDTheme {
                val navInfo by NavigationRepository.navInfo.collectAsState()
                val isBluetoothConnected by bluetoothManager.isConnected.collectAsState()
                val lifecycleOwner = LocalLifecycleOwner.current

                var isNotificationEnabled by remember { mutableStateOf(false) }

                LaunchedEffect(lifecycleOwner) {
                    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                        isNotificationEnabled = isNotificationServiceEnabled(this@MainActivity)
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavigationDashboard(
                        navInfo = navInfo,
                        isBluetoothConnected = isBluetoothConnected,
                        showNotificationButton = !isNotificationEnabled,
                        onOpenNotificationSettings = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun requestBluetoothPermissions() {
        // For SPP with bonded devices, only BLUETOOTH_CONNECT is needed on Android 12+
        // Older versions only need BLUETOOTH/BLUETOOTH_ADMIN (declared in manifest)
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            emptyArray() // No runtime permissions needed for Android < 12
        }

        val allPermissionsGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allPermissionsGranted || requiredPermissions.isEmpty()) {
            bluetoothManager.startConnectionLoop()
        } else {
            requestPermissionLauncher.launch(requiredPermissions)
        }
    }

    private fun isNotificationServiceEnabled(context: Context): Boolean {
        val pkgName = context.packageName
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat?.contains(pkgName) ?: false
    }
}

@Composable
fun NavigationDashboard(
    navInfo: NavInfo,
    isBluetoothConnected: Boolean,
    showNotificationButton: Boolean,
    onOpenNotificationSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Maps Navigation HUD", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))

        navInfo.icon?.let {
            Image(bitmap = it.asImageBitmap(), contentDescription = "Turn Icon", modifier = Modifier.size(150.dp))
        } ?: Spacer(modifier = Modifier.height(150.dp))

        Spacer(modifier = Modifier.height(24.dp))
        Log.d("TEST", navInfo.instruction)
        Text(
            text = navInfo.instruction,
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = navInfo.details, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)

        Spacer(modifier = Modifier.height(8.dp))
        Text(text = navInfo.subText, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = if (isBluetoothConnected) "Bluetooth Connected" else "Bluetooth Disconnected",
            color = if (isBluetoothConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (showNotificationButton) {
            Button(onClick = onOpenNotificationSettings, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("Grant Notification Access")
            }
        }
    }
}
