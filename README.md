# This is navHUD.
When you are driving, and don't want to be looking at the phone screen for directions or distractions, you may have thought of making an ESP32-based device to just show the current path/the next turn.
This is my attempt at solving that problem.

I have used ESP32 devkit (classic Bluetooth capable) connected to a 128x64 OLED module.
Along with that, I have the counterpart Android application.

## Workings:
The Android application gets the navigation data from the notification panel by Google Maps. This is parsed and transmitted over **Bluetooth Classic Serial Port Profile (SPP)** to the ESP32, which is mounted on the dashboard connected to a display.

The data is sent as newline-delimited frames with pipe (`|`) separated fields:
- Instruction (turn direction)
- Details (distance/street name)
- Sub-text (ETA/time)
- Optional: 48x48 monochrome bitmap icon (288 bytes)

## Setup Instructions:

### Android App:
1. Install the app on your Android device
2. **Pair the ESP32 device in Android Bluetooth Settings** before launching the app:
   - Go to **Settings → Bluetooth**
   - Turn on Bluetooth
   - Find your ESP32 device (should contain "navHUD" in the name)
   - Tap to pair (may require PIN, typically `1234` or `0000`)
3. Launch the navHUD app and grant **Bluetooth** and **Notification Listener** permissions
4. The app will automatically connect to the paired device matching "navHUD"
5. Start Google Maps navigation to see data streaming to the display

### ESP32 Firmware:
- The ESP32 must be programmed to accept SPP connections and parse newline-delimited frames
- Device name should contain "navHUD" for auto-discovery by the app
- Update the firmware in `navHUD_esp/` to listen on SPP UUID `00001101-0000-1000-8000-00805F9B34FB`

## Notes:
- **Bluetooth Classic** (not BLE) is required - ensure your ESP32 variant supports it (e.g., ESP32-WROOM, not ESP32-C3)
- The Android app connects to bonded devices only; manual pairing in Android Settings is required before first use
- Data format: `instruction|details|subText|[bitmap_data]\n` 
