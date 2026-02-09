package aer.app.navhud

import android.app.Application

class NavHudApplication : Application() {

    lateinit var bluetoothManager: BluetoothSppManager

    override fun onCreate() {
        super.onCreate()
        bluetoothManager = BluetoothSppManager(this)
    }
}