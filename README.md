# This is navHUD.
When you are driving, and dont want to be looking at the phone screen for directions or distractions, you may have thought of making an esp32/etc based device to just show the current path/the next turn.
This is my attempt at solving that problem.

I have used ESP32 devkit initially, and I will change it with an esp32-c3 soon when I get the module.
Along with that I have the counterpart Android application.

Workings:
The android application will get the navigation data from the notification panel by maps. This will then parse it and break it up into smaller chunks (limited by the BLE packet sizes) and then they will be transmitted to the esp, wihch will be on the dashboard connected to a display.

I have used the 128x64 oled module, 
