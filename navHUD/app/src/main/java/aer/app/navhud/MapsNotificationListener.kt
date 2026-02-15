package aer.app.navhud

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream

class MapsNotificationListener : NotificationListenerService() {

    private lateinit var bluetoothManager: BluetoothSppManager
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var navigationStopJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        bluetoothManager = (application as NavHudApplication).bluetoothManager

        // Observe the READY state to send data immediately upon connection
        serviceScope.launch {
            bluetoothManager.isReady.collect { isReady ->
                if (isReady) {
                    Log.d("MapsListener", "Bluetooth is ready. Checking for pending navigation data...")
                    val currentNavInfo = NavigationRepository.navInfo.value
                    if (currentNavInfo.isNavigating) {
                        Log.d("MapsListener", "Pending data found. Sending upon connection.")
                        val data = serializeForBluetooth(currentNavInfo.instruction, currentNavInfo.details, currentNavInfo.subText, currentNavInfo.icon)
                        bluetoothManager.sendData(data)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel() // Clean up the coroutine
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn?.packageName == "com.google.android.apps.maps") {
            navigationStopJob?.cancel()
            processNotification(sbn)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn?.packageName == "com.google.android.apps.maps") {
            navigationStopJob = serviceScope.launch {
                delay(2000L)
                Log.d("MapsListener", "Navigation stop delay finished. Sending Standby.")
                NavigationRepository.setNavigationStopped()
                if (bluetoothManager.isConnected.value) {
                    val standbyData = serializeForBluetooth("Standby State", "", "", null)
                    bluetoothManager.sendData(standbyData)
                }
            }
        }
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) {
        getActiveNotifications()?.firstOrNull { it.packageName == "com.google.android.apps.maps" }?.let {
            navigationStopJob?.cancel()
            processNotification(it)
        }
    }

    private fun toAsciiSafe(input: String?): String? {
        return input
            
            ?.replace('·', '|')
            //?.replace(Regex("[^\\x20-\\xA5]"), "")
            
    }

    private fun processNotification(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = toAsciiSafe(extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        val text = toAsciiSafe(extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        val subText = toAsciiSafe(extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString())
        val bitmap = getNotificationBitmap(notification)

        Log.d("MapsListener", "Auto-Update: $title - $text - $subText")

        val fullData = serializeForBluetooth(title, text, subText, bitmap)
        bluetoothManager.sendData(fullData)

        NavigationRepository.update(instruction = title, details = text, subText = subText, icon = bitmap)
    }

    @Serializable
    data class navData(val title: String?, val details: String?, val subText: String?, val icon: UByteArray?)

    private fun serializeForBluetooth(title: String?, details: String?, subText: String?, bitmap: Bitmap?): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val endOfMessage = "\n".toByteArray()
        Log.d("MapsListener", "Serializing for Bluetooth: $title - $details - $subText -Bitmap: ${bitmap != null}")

        var monochromeData: UByteArray? = null
        if (bitmap != null) {
            monochromeData = convertBitmapToMonochrome(bitmap)
        }

        val navdata = navData(title, details, subText, monochromeData)
        val jsonString = Json.encodeToString(navdata)

        
        Log.d("MapsListener", "Serialized JSON: $jsonString")

        outputStream.write(jsonString.toByteArray())

        outputStream.write(endOfMessage) 

        return outputStream.toByteArray()
    }

    private fun convertBitmapToMonochrome(bitmap: Bitmap): UByteArray {
        Log.d("MapsListener", "Bitmap original: ${bitmap.width}x${bitmap.height}")
        
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 90, 90, true)
        val monochromeData = UByteArray(90 * 90 / 8)
        
        var pixelsOn = 0
        for (i in monochromeData.indices) {
            var currentByte = 0
            for (j in 0..7) {
                val pixelIndex = i * 8 + j
                val x = pixelIndex % 90
                val y = pixelIndex / 90
                val alpha = scaledBitmap.getPixel(x, y) ushr 24
                
                if (alpha > 128) {
                    currentByte = currentByte or (1 shl (7 - j))
                    pixelsOn++
                }
            }
            monochromeData[i] = currentByte.toUByte()
        }
        
        Log.d("MapsListener", "Monochrome: $pixelsOn / 8100 píxeles encendidos")
        return monochromeData
    }

    private fun getNotificationBitmap(notification: Notification): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val icon = notification.getLargeIcon()
            if (icon != null) return iconToBitmap(icon)
        }
        @Suppress("DEPRECATION")
        val bitmap = notification.extras.get(Notification.EXTRA_LARGE_ICON)
        return if (bitmap is Bitmap) bitmap else null
    }

    private fun iconToBitmap(icon: Icon): Bitmap? {
        try {
            val drawable = icon.loadDrawable(this) ?: return null
            if (drawable is BitmapDrawable) return drawable.bitmap
            val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth.coerceAtLeast(1), drawable.intrinsicHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            return bitmap
        } catch (e: Exception) { return null }
    }
}
