#include <ArduinoBLE.h>

#define BLE_UUID_SERVICE                    "23937b16-acc8-11eb-8529-0242ac130003"
#define BLE_UUID_STRING_CHARACTERISTIC      "1A3AC131-31EF-758B-BC51-54A61958EF82"

BLEService bleService(BLE_UUID_SERVICE);
BLEStringCharacteristic bleStringCharacteristic( BLE_UUID_STRING_CHARACTERISTIC, BLERead | BLENotify, 100 );

static const String greeting = "Hello World!"; // Payload

// The setup function runs once when you press reset or power the board
void setup() {
  Serial.begin(11520);
  while (!Serial);

  pinMode(LED_BUILTIN, OUTPUT);
  
  initializeBLEConnection();
}

// The loop function runs over and over again forever
void loop() {
  digitalWrite(LED_BUILTIN, HIGH); delay(1000); // Blink LED light till connected with device
  digitalWrite(LED_BUILTIN, LOW); delay(1000);   
  
  BLEDevice central = BLE.central();  // Wait for a BLE central to connect
  Serial.println("- Discovering central device...");
  delay(500);
  if (central) {
    Serial.println("Connected to central device: " + central.address());
    
    while (central.connected()){
      Serial.println("SEND PAYLOAD"); // Keep looping while connected
      bleStringCharacteristic.writeValue(greeting); // Set/change greeting string - when changed, centeral device is notified.
      delay(1000);
    } 
  }
}

void initializeBLEConnection() {
  if (!BLE.begin()) {   // initialize BLE
    Serial.println("starting BLE failed!");
    while (1);
  }

  BLE.setLocalName("Nano33BLE");  // Set name for connection
  BLE.setAdvertisedService(bleService); // Advertise service
  bleService.addCharacteristic(bleStringCharacteristic); // Add characteristic to service
  BLE.addService(bleService); // Add service
  bleStringCharacteristic.writeValue(greeting); // Set initial value in characteristic
  BLE.advertise();  // Start advertising
  
  Serial.print("Peripheral device MAC: " + BLE.address());
  Serial.println("Waiting for connections...");
}
