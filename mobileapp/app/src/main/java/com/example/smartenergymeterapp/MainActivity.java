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
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
    private final String BASE_URL = "http://192.168.1.6:8080";  // CHANGE THIS TO SERVER IP!
    private static String prevRecognizedDevice = "";
    private int sumConsumption = 0;

    /** Views (UI) **/
    TextView stateBluetoothV;
    TextView bluetoothDevicesLabelV;
    TextView bluetoothDataV;
    Button pairButton;
    VideoView videoView;
    TextView consumption;
    TextView consumptionNumber;
    TextView serverStatus;

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
        bluetoothDevicesLabelV = findViewById(R.id.bluetooth_devices_label);
        bluetoothDataV = findViewById(R.id.bluetooth_data);
        ListView deviceListV = findViewById(R.id.device_list);
        videoView = findViewById(R.id.videoView);
        pairButton = findViewById(R.id.pair_button);
        consumption = findViewById(R.id.consumption); consumption.setVisibility(View.INVISIBLE); consumption.setText("Consumption");
        consumptionNumber = findViewById(R.id.consumption_number); consumptionNumber.setVisibility(View.INVISIBLE); consumptionNumber.setText("0wh");
        serverStatus = findViewById(R.id.server_status); serverStatus.setVisibility(View.INVISIBLE); serverStatus.setText("{}");

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
            requestData("blender");
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
                        pairButton.post(() -> pairButton.setVisibility(View.GONE));
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
            requestData(parsed[0]);
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
    private void requestData(String device) {

        // Send on change
        if(device.equals(prevRecognizedDevice)) {
            return;
        }
        int stopOrStartSavingMeasurementsOnServer = 0;//0 (returns consumption) or 1 (start)
        if (device.equals("blender") && prevRecognizedDevice.equals("tisina")) {
            stopOrStartSavingMeasurementsOnServer = 1; // start saving
        }
        if (device.equals("tisina") && prevRecognizedDevice.equals("blender")) {
            stopOrStartSavingMeasurementsOnServer = 0; // stop saving - gets response
        }

        prevRecognizedDevice = device;
        Long timestamp = (System.currentTimeMillis()/1000);
        JSONObject payload = new JSONObject();
        try {
            payload.put("userID", "test_4");
            payload.put("time", timestamp);
            payload.put("device", "blender");
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
                        Log.d("DEBUG", "RESPONSE: " + response.toString());
                        serverStatus.setText(response.toString());
                        if (response.has("calculated")){
                            try {
                                sumConsumption += response.getInt("calculated");
                                consumptionNumber.setText(sumConsumption + "Wh");
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
}


