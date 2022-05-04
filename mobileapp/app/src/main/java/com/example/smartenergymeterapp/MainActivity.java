package com.example.smartenergymeterapp;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;


public class MainActivity extends AppCompatActivity {
    private BluetoothAdapter bluetoothAdapter; // PART 1: Enable Bluetooth
    private BluetoothLeScanner bluetoothLeScanner; // PART 2: Scan BLE devices
    private boolean scanning;
    private Handler handler;
    private final int PERMISSION_CODE = 100;
    private static final long SCAN_PERIOD = 10000;  // Stops scanning after 10 seconds.
    ArrayList<BluetoothDevice> devicesDiscoveredList;

    BluetoothGatt bluetoothGatt;    // PART 3: Connect  to selected device
    public static final UUID serviceUUID = UUID.fromString("23937b16-acc8-11eb-8529-0242ac130003");
    public static final UUID characteristicUUID = UUID.fromString("1A3AC131-31EF-758B-BC51-54A61958EF82");
    public static final UUID clientCharConfigUUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    /* HTTP REQUESTS */
    String userId = UUID.randomUUID().toString();
    private final String BASE_URL = "http://192.168.8.102:8080";  // CHANGE THIS TO SERVER IP!
    //private final String BASE_URL = "http://130.162.44.178:8080";  // deployed server ip
    private String DETECTING_DEVICE = "fen";
    private final int bufferLength = 3;
    private String[] lastFewReadings = new String[bufferLength]; // ["fen", "fen", "fen"] --> for removing outliers to send "fen"
    private float sumConsumption = 0;
    private float addMockConsumption = 0.67f;// Mocks consumption of 1 second of running hairdryer (W)

    /** Views (UI) **/
    TextView stateBluetoothLabelV;
    TextView stateBluetoothV;
    TextView bluetoothDataLabelV;
    TextView bluetoothDevicesLabelV;
    TextView bluetoothDataV;
    Button pairButton;
    VideoView videoView;
    ListView deviceListV;
    TextView consumption;
    TextView consumptionNumber;
    TextView serverStatus;
    TextView userLabelV;
    TextView userIdV;
    ImageView hairdryerIcon;

    /** Permissions **/
    String[] permissions = new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
    };

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Views
        stateBluetoothV = findViewById(R.id.bluetooth_state);
        stateBluetoothLabelV = findViewById(R.id.bluetooth_state_label);
        bluetoothDevicesLabelV = findViewById(R.id.bluetooth_devices_label);
        bluetoothDataV = findViewById(R.id.bluetooth_data);
        deviceListV = findViewById(R.id.device_list);
        videoView = findViewById(R.id.videoView);
        //pairButton = findViewById(R.id.pair_button);
        consumption = findViewById(R.id.consumption); consumption.setVisibility(View.INVISIBLE); consumption.setText("Consumption");
        consumptionNumber = findViewById(R.id.consumption_number); consumptionNumber.setVisibility(View.INVISIBLE); consumptionNumber.setText("0wh");
        serverStatus = findViewById(R.id.server_status); serverStatus.setVisibility(View.INVISIBLE); serverStatus.setText("");
        userLabelV = findViewById(R.id.user_label);
        userIdV = findViewById(R.id.userId); userIdV.setText(userId);
        hairdryerIcon = findViewById(R.id.imageView); hairdryerIcon.setImageResource(R.drawable.hairdryer);hairdryerIcon.setVisibility(View.INVISIBLE);
        bluetoothDataLabelV = findViewById(R.id.bluetooth_data_label);

        // Init
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        devicesDiscoveredList = new ArrayList<>();

        requestPermissions();
        findBLEdevices();

        // Event listeners
        deviceListV.setOnItemClickListener((parent, view, position, l) -> {
            if (bluetoothGatt != null) {
                bluetoothGatt.close();
                bluetoothGatt.disconnect();
                stateBluetoothV.setText("Disconnected");
                bluetoothGatt = null;
            } else {
                connectToDeviceSelected(position);
            }
        });
        consumption.setOnClickListener((l)-> {
            requestData(DETECTING_DEVICE, 1000);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bluetoothAdapter == null) {
            stateBluetoothV.setText("Not supported");
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            stateBluetoothV.setText("Not enabled");
            promptEnableBluetooth();
        } else {
            if (bluetoothGatt == null) {
                stateBluetoothV.setText("On");
                scanLeDevices();
            }
        }
        resumePlayingVideo();
    }

    private void resumePlayingVideo() {
        String uriPath = "android.resource://"+ getPackageName() +"/"+ R.raw.bracelet_video;
        Uri uri = Uri.parse(uriPath);
        videoView.setVideoURI(uri);
        videoView.start();
        videoView.setOnPreparedListener(mediaPlayer -> mediaPlayer.setLooping(true));
    }

    /** PART 1: ENABLE BLUETOOTH FROM APP **/
    private void promptEnableBluetooth() {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        activityResultLauncher.launch(enableBtIntent);
    }

    ActivityResultLauncher<Intent> activityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        stateBluetoothV.setText("On");
                        scanLeDevices();
                    }
                    if (result.getResultCode() == Activity.RESULT_CANCELED) {
                        stateBluetoothV.setText("Not enabled");
                    }
                }
            });

    /** PART 2: FIND BLE DEVICES **/
    /* https://developer.android.com/guide/topics/connectivity/bluetooth/find-ble-devices */
    private void findBLEdevices() {
        if (bluetoothAdapter == null) {
            stateBluetoothV.setText("Error");
            return;
        }
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        handler = new Handler();
        scanLeDevices();
    }

    @SuppressLint("MissingPermission")
    private void scanLeDevices() {
        if (!scanning) {
            // Stops scanning after a predefined scan period.
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    scanning = false;
                    bluetoothLeScanner.stopScan(leScanCallback);
                }
            }, SCAN_PERIOD);

            // Start scanning.
            scanning = true;
            bluetoothLeScanner.startScan(leScanCallback);
        } else {
            scanning = false;
            bluetoothLeScanner.stopScan(leScanCallback);
        }
    }

    /** Callback - called when device discovered **/
    private final ScanCallback leScanCallback = new ScanCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice found_device = result.getDevice();
            boolean found_same = false;

            if (found_device == null || found_device.getName() == null) {
                return;
            }

            Log.d("DEBUG", "Found device: " + found_device.getName());

            /* Append to devicesDiscoveredList */
            for (int i = 0; i < devicesDiscoveredList.size(); i++) {
                BluetoothDevice tmp = devicesDiscoveredList.get(i);
                if (tmp.getName().equals(found_device.getName())) {
                    found_same = true;
                }
            }
            if (!found_same) {
                devicesDiscoveredList.add(found_device);
                showDeviceList();
            }
        }
    };

    /** Print devices to UI */
    @SuppressLint("MissingPermission")
    private void showDeviceList() {
        ArrayList<HashMap<String, String>> data = new ArrayList<>();
        for (int i = 0; i < devicesDiscoveredList.size(); i++) {
            HashMap<String, String> e = new HashMap<>();
            e.put("device_name", devicesDiscoveredList.get(i).getName());
            e.put("device_status", " ");
            data.add(e);
        }

        ListView lv = findViewById(R.id.device_list);
        SimpleAdapter adapter = new SimpleAdapter(this,
                data,
                R.layout.list_devices,
                new String[]{"device_name", "device_status"},
                new int[]{R.id.device_name, R.id.device_status}
        );
        lv.setAdapter(adapter);
    }

    /** Clicking on label starts scanning again **/
    public void onBluetoothDevicesLabelClick(View v) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        bluetoothLeScanner.stopScan(leScanCallback);
        scanLeDevices();
    }

    @SuppressLint("MissingPermission")
    public void onPairButtonClick(View v) {
        bluetoothLeScanner.stopScan(leScanCallback);
        scanLeDevices();
    }

    /** PART 3: Connect to selected device ***/
    /*https://developer.android.com/guide/topics/connectivity/bluetooth/connect-gatt-server*/

    /* Connect by pressing on device name */
    @SuppressLint("MissingPermission")
    public void connectToDeviceSelected(int deviceNumber) {
        if (devicesDiscoveredList == null || devicesDiscoveredList.get(deviceNumber) == null) {
            return;
        }
        stateBluetoothV.setText("Connecting...");
        bluetoothGatt = (devicesDiscoveredList.get(deviceNumber)).connectGatt(this, false, gattCallback);
    }

    /* Callback - called when changes on bluetoothGatt */
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
            switch (newState) {
                case BluetoothProfile.STATE_DISCONNECTED:
                    Log.d("DEBUG", "disconnected " + newState);
                    break;
                case BluetoothProfile.STATE_CONNECTED:
                    Log.d("DEBUG", "connected " + newState);
                    bluetoothGatt.discoverServices();
                    break;
                default:
                    break;
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService bluetoothGattService = getSupportedGattService(serviceUUID); // Get service
                BluetoothGattCharacteristic bluetoothGattServiceCharacteristic = bluetoothGattService.getCharacteristic(characteristicUUID); // Get characteristic

                if (bluetoothGatt != null && isCharacteristicReadable(bluetoothGattServiceCharacteristic)) {
                    bluetoothGatt.setCharacteristicNotification(bluetoothGattServiceCharacteristic, true); // Be notified when characteristic changes

                    if (characteristicUUID.equals(bluetoothGattServiceCharacteristic.getUuid())) {
                        BluetoothGattDescriptor descriptor = bluetoothGattServiceCharacteristic.getDescriptor(clientCharConfigUUID);
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        bluetoothGatt.writeDescriptor(descriptor);

                        bluetoothGatt.readCharacteristic(bluetoothGattServiceCharacteristic); // Start receiving data from characteristic


                        showToast("Connected."); stateBluetoothV.post(() -> stateBluetoothV.setText("Connected."));  // Update UI
                        videoView.post(() -> videoView.setVisibility(View.GONE));
                        //pairButton.post(() -> pairButton.setVisibility(View.GONE));
                        consumption.post(() -> consumption.setVisibility(View.VISIBLE));
                        consumptionNumber.post(() -> consumptionNumber.setVisibility(View.VISIBLE));
                        serverStatus.post(() -> serverStatus.setVisibility(View.VISIBLE));


                    } else {
                        showToast("Error connecting BLE service.");
                    }
                }
            } else {
                showToast("No services discovered."); Log.w("WARNING", "onServicesDiscovered received: " + status);
            }
        }

        /** PART 4 RECEIVE DATA FROM GATT CHARACTERISTIC (ONE//FIRST TIME!)**/
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                String s = new String(characteristic.getValue(), StandardCharsets.UTF_8);
                Log.d("DEBUG", "Received: " + s);
            } else {
                Log.d("DEBUG", "Characteristic reading was not successful");
            }
        }

        /** part 5: RECEIVE DATA CHANGES FROM BLE DEVICE **/
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            String s = new String(characteristic.getValue(), StandardCharsets.UTF_8);
            displayData(s);
            String[] parsed = s.split(" ");
            requestData(parsed[0],  Integer.parseInt(parsed[1]));
        }

        public void displayData(String data){
            // Display on element bluetoothDataV
            bluetoothDataV.post(() -> bluetoothDataV.setText(data));
        }

        //public void
    };


    public BluetoothGattService getSupportedGattService(UUID serviceUUID) {
        if (bluetoothGatt == null) return null;
        return bluetoothGatt.getService(serviceUUID);
    }



    /** Utils **/
    public boolean isCharacteristicReadable(BluetoothGattCharacteristic pChar) {
        return ((pChar.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) != 0);
    }
    public static String bytesToHex(byte[] bytes) {
        char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }
    // Handler for displaying toasts from worker threads (e.g. onStateChange))
    public void showToast(final String toast)
    {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, toast, Toast.LENGTH_SHORT).show());
    }
    public static float round(float d, int decimalPlace) {
        return BigDecimal.valueOf(d).setScale(decimalPlace,BigDecimal.ROUND_HALF_UP).floatValue();
    }


    /** Permissions **/
    private void requestPermissions() {
        ActivityCompat.requestPermissions(MainActivity.this, permissions, PERMISSION_CODE); // Ask for permissions at runtime
    }

    /* Callback - called when user changes permissions (e.g. allowing location services) */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CODE) {
            // Checking whether user granted the permission or not.
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("DEBUG", "User granted permissions");
                findBLEdevices();
            } else {
                Log.d("DEBUG", "User denied permissions");
            }
        }
    }








    /** HTTP REQUEST GET/POST**/
    boolean stopOrStartSavingMeasurementsOnServer = false; // 0 (returns consumption) or 1 (start)
    private void requestData(String device, int loudness) {
        addDeviceToLastFewReadings(device);
        //Log.d("DEBUG", Arrays.toString(lastFewReadings));
        if(isAllEqual(lastFewReadings) && lastFewReadings[0].equals(DETECTING_DEVICE)) {
            // ["fen","fen","fen"]
            stopOrStartSavingMeasurementsOnServer = true; // start saving if last 3 readings where the same

        }
        if(isAllEqual(lastFewReadings) && lastFewReadings[0].equals("tisina")) {
            stopOrStartSavingMeasurementsOnServer = false; // stop saving
        }


        Long timestamp = (System.currentTimeMillis()/1000);
        JSONObject payload = new JSONObject();
        try {
            payload.put("userID", userId);
            payload.put("loudness", loudness);
            payload.put("device", device);
            payload.put("start", stopOrStartSavingMeasurementsOnServer);
        } catch (JSONException e) {
            Log.e("ERROR", e.toString());
        }
        Log.d("DEBUG", "POST: " + payload);

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, BASE_URL,
                payload,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        //Log.d("DEBUG", "RESPONSE: " + response.toString());
                        if (response.has("calculation") && response.has("measurment")){
                            try {
                                if (response.getInt("measurment") == 0) {
                                    String capDevice = DETECTING_DEVICE.substring(0, 1).toUpperCase() + DETECTING_DEVICE.substring(1);

                                    // FINISHED RESPSONSE
                                    int receivedConsumption = response.getInt("calculation");
                                    sumConsumption += receivedConsumption;
                                    /* Mock implementation - delete later */
                                    if(receivedConsumption == 0){
                                        sumConsumption += addMockConsumption;
                                    }

                                    sumConsumption = round(sumConsumption, 2);
                                    consumptionNumber.setText(sumConsumption + "Wh");

                                    Log.d("DEBUG", "receivedConsumption: " + receivedConsumption);
                                    serverStatus.setText("Hairdryer");
                                    hairdryerIcon.setVisibility(View.VISIBLE);


                                    /*toast*/
                                    if(receivedConsumption>0){
                                        showToast(capDevice + " consumed " + receivedConsumption);
                                    }
                                }
                                else {
                                    serverStatus.setText("");
                                    hairdryerIcon.setVisibility(View.INVISIBLE);
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        serverStatus.setText(error.toString());
                        Log.d("DEBUG", "onErrorResponse " + error.toString());

                    }
                });

        RequestQueue requestQueue = Volley.newRequestQueue(MainActivity.this);
        requestQueue.add(jsonObjectRequest);
    }

    /* BUFFER BEFORE SENDING REQUEST */
    // Buffer type ["device", "device", "device"]
    private int indexReadings = 0;
    private void addDeviceToLastFewReadings(String device) {
        lastFewReadings[indexReadings] = device;

        indexReadings++;
        if (indexReadings == bufferLength) {
            indexReadings = 0;
        }
    }
    public static boolean isAllEqual(String[] a){
        for(int i=1; i<a.length; i++){
            if(!a[0].equals(a[i])){
                return false;
            }
        }
        return true;
    }

    public void onDetailButtonClick(View view) {
        toggleVisibiltyOfDetailData();
    }

    private void toggleVisibiltyOfDetailData() {
        toggleView(stateBluetoothV);
        toggleView(bluetoothDevicesLabelV);
        toggleView(stateBluetoothLabelV);
        toggleView(bluetoothDataV);
        toggleView(bluetoothDataLabelV);
        toggleView(userLabelV);
        toggleView(userIdV);
        toggleView(deviceListV);
    }
    public void toggleView(View view){
        if(view.getVisibility()==View.INVISIBLE)
            view.setVisibility(View.VISIBLE);
        else if(view.getVisibility()==View.VISIBLE)
            view.setVisibility(View.INVISIBLE);
    }

}


