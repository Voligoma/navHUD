#include <Arduino.h>
#include <SPI.h>
#include <TFT_eSPI.h>
#include "BluetoothSerial.h"
#include "string.h"
#include <ArduinoJson.h>
#include "symbols2.h"
#include "esp_spp_api.h"

TFT_eSPI tft = TFT_eSPI();
u_int32_t t1 = 0;

#include "Free_Fonts.h"

#define TS_MINX 320
#define TS_MAXX 3800
#define TS_MINY 320
#define TS_MAXY 3900

#define BACKLIGHT_PIN 13

String title = "";
String details = "";
String subText = "";

volatile u_int8_t bluetooth_status = 0; // 0=false, 1 = connected, 2 = no change

uint8_t bitmapData[1013];
uint8_t old_bitmapData[1013];

BluetoothSerial BT;
bool getPixel(int x, int y, const uint8_t *data);

void clear_indication_text()
{
  tft.fillRect(0, 90, 240, 90, TFT_GOOGLE_GREEN);
}

void clear_bitmap_area()
{
  tft.fillRect(75, 0, 90, 90, TFT_GOOGLE_GREEN);
}

void clear_bluetooth_icon()
{
  tft.fillRect(0, 0, 30, 30, TFT_GOOGLE_GREEN);
}

void btCallback(esp_spp_cb_event_t event, esp_spp_cb_param_t *param)
{ // Tiene que rapido, si no se enoja el watchdog
  switch (event)
  {
  case ESP_SPP_SRV_OPEN_EVT:
    bluetooth_status = 1;
    break;
  case ESP_SPP_CLOSE_EVT:
    bluetooth_status = 0;
    break;
  default:
    break;
  }
}

void displayBitmap(int x, int y)
{
  // Dibuja el bitmap de 90x90 píxeles
  for (int py = 0; py < 90; py++)
  {
    for (int px = 0; px < 90; px++)
    {
      if (getPixel(px, py, bitmapData))
      {
        tft.drawPixel(x + px, y + py, TFT_WHITE);
      }
    }
  }
}

bool getPixel(int x, int y, const uint8_t *data)
{
  int pixelIndex = y * 90 + x;
  int byteIndex = pixelIndex / 8;
  int bitIndex = 7 - (pixelIndex % 8);
  return (data[byteIndex] >> bitIndex) & 1;
}

void setup(void)
{
  Serial.begin(115200);
  Serial.println("\n\nStarting...");

  pinMode(BACKLIGHT_PIN, OUTPUT);
  digitalWrite(BACKLIGHT_PIN, LOW);

  tft.init();
  tft.fillScreen(TFT_BLACK);
  tft.fillRect(0, 0, 240, 90, TFT_GOOGLE_GREEN);
  tft.drawBitmap(0, 0, epd_bitmap_bluetooth_disabled_90dp_FFFFFF_FILL0_wght400_GRAD0_opsz48, 30, 30, TFT_WHITE, TFT_GOOGLE_GREEN);
  digitalWrite(BACKLIGHT_PIN, HIGH);
  tft.setTextDatum(MC_DATUM);
  tft.setTextColor(TFT_WHITE);
  tft.setTextFont(FONT2);

  BT.begin("Buan-navHUD"); // Nombre del dispositivo Bluetooth
  BT.register_callback(btCallback);
  BT.setPin("1234"); // Establece el PIN para emparejamiento
  Serial.println("El dispositivo Bluetooth está listo para emparejarse.");
}

//====================================================================

void loop()
{

  if (bluetooth_status != 2)
  {
    if (bluetooth_status == 1)
    {
      clear_bluetooth_icon();
      tft.drawBitmap(0, 0, epd_bitmap_bluetooth_90dp_FFFFFF_FILL0_wght400_GRAD0_opsz48, 30, 30, TFT_WHITE, TFT_GOOGLE_GREEN);
    }
    else
    {
      clear_bluetooth_icon();
      tft.drawBitmap(0, 0, epd_bitmap_bluetooth_disabled_90dp_FFFFFF_FILL0_wght400_GRAD0_opsz48, 30, 30, TFT_WHITE, TFT_GOOGLE_GREEN);
    }
    bluetooth_status = 2; // No change until next event
  }

  if (tft.getTouchRawZ() > 400)
  {
    uint16_t x, y;
    tft.getTouchRaw(&x, &y);
    x = map(x, TS_MINX, TS_MAXX, 240, 0);
    y = map(y, TS_MINY, TS_MAXY, 0, 320);
    Serial.print("X: ");
    Serial.print(x);
    Serial.print(" Y: ");
    Serial.println(y);
    tft.drawCircle(x, y, 5, TFT_RED);
  }
  /*if (millis() - t1 >= 1000)
  {
    t1 = millis();
    BT.println("OK"); // health check
  }*/
  if (BT.available())
  {
    String bt_data = BT.readStringUntil('\n'); // Lee datos recibidos por Bluetooth
    Serial.print("BT recibido: ");
    // Serial.println(bt_data);
    Serial.print("Longitud total: ");
    Serial.println(bt_data.length());

    JsonDocument navData;
    DeserializationError error = deserializeJson(navData, bt_data);
    if (error)
    {
      Serial.print(F("deserializeJson() failed: "));
      Serial.println(error.f_str());
    }
    else
    {
      title = navData["title"].as<String>();
      details = navData["details"].as<String>();
      subText = navData["subText"].as<String>();

      JsonArray bitmapArray = navData["icon"].as<JsonArray>();
      u_int i = 0;
      for (JsonVariant v : bitmapArray)
      {
        bitmapData[i] = v.as<uint8_t>();
        /*Serial.print(v.as<uint8_t>());
        Serial.print(" ");*/
        i++;
      }
      if (title == "Standby State")
      {
        clear_indication_text();
        tft.setTextSize(2);
        tft.drawString("Standby", 120, 112);
        tft.setTextSize(1);
        tft.drawString("Esperando datos de navegacion", 120, 135);
        clear_bitmap_area();
        tft.drawBitmap(75, 0, epd_bitmap_location_off_90dp_FFFFFF_FILL0_wght400_GRAD0_opsz48, 90, 90, TFT_WHITE, TFT_GOOGLE_GREEN);
      }
      else
      {
        clear_indication_text();
        tft.setTextSize(2);
        tft.drawString(title, 120, 112);
        tft.setTextSize(1);
        tft.drawString(details, 120, 135);
        tft.drawString(subText, 120, 158);

        if (memcmp(bitmapData, old_bitmapData, sizeof(bitmapData)) != 0)
        {
          clear_bitmap_area();
          displayBitmap(75, 0);
          memcpy(old_bitmapData, bitmapData, sizeof(bitmapData));
          Serial.println("Bitmap cambiado, redibujando...");
        }
        else
        {
          Serial.println("Bitmap sin cambios, no se redibuja.");
        }
      }
    }
  }
}
