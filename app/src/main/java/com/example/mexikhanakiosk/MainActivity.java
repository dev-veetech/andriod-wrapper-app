package com.example.mexikhanakiosk;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.mexikhanakiosk.CloverConnector;
import com.example.mexikhanakiosk.CloverConnectorService;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements CloverConnector.CloverConnectionListener {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1001;
    private static final int REQUEST_ENABLE_BT = 1002;

    // UI Elements
    private Button connectButton;
    private TextView statusTextView;

    // Clover service variables
    private CloverConnectorService cloverService;
    private boolean cloverServiceBound = false;
    private List<BluetoothDevice> discoveredDevices = new ArrayList<>();
    private AlertDialog deviceListDialog;
    private boolean isCloverConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_simple);

        // Initialize UI
        connectButton = findViewById(R.id.connectButton);
        statusTextView = findViewById(R.id.statusTextView);

        // Setup button click listener
        connectButton.setOnClickListener(v -> showCloverPairingDialog());

        // Update initial status
        updateStatus("Not connected to any Clover device");

        // Check and request Bluetooth permissions
        checkAndRequestBluetoothPermissions();

        // Start and bind to Clover service
        startAndBindCloverService();
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Bind to the Clover service if not already bound
        if (!cloverServiceBound) {
            startAndBindCloverService();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        // Unbind from the Clover service
        if (cloverServiceBound) {
            if (cloverService != null) {
                cloverService.unregisterListener(this);
            }
            unbindService(cloverServiceConnection);
            cloverServiceBound = false;
        }
    }

    /**
     * Check and request Bluetooth permissions
     */
    private void checkAndRequestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // For Android 12 and above
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.BLUETOOTH_CONNECT,
                                Manifest.permission.ACCESS_FINE_LOCATION
                        },
                        REQUEST_BLUETOOTH_PERMISSIONS);
            }
        } else {
            // For Android 11 and below
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.BLUETOOTH,
                                Manifest.permission.BLUETOOTH_ADMIN,
                                Manifest.permission.ACCESS_FINE_LOCATION
                        },
                        REQUEST_BLUETOOTH_PERMISSIONS);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            boolean allPermissionsGranted = true;

            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }

            if (allPermissionsGranted) {
                updateStatus("Bluetooth permissions granted");
                // Check if Bluetooth is enabled
                if (cloverServiceBound && !cloverService.isBluetoothEnabled()) {
                    // Request to enable Bluetooth
                    Intent enableBtIntent = cloverService.getBluetoothEnableIntent();
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                }
            } else {
                updateStatus("Bluetooth permissions denied");
                Toast.makeText(this, "Bluetooth permissions are required for Clover integration", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                // Bluetooth is now enabled
                updateStatus("Bluetooth enabled");
            } else {
                // User declined to enable Bluetooth
                updateStatus("Bluetooth not enabled");
                Toast.makeText(this, "Clover integration requires Bluetooth to be enabled", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Start and bind to the Clover service
     */
    private void startAndBindCloverService() {
        Intent intent = new Intent(this, CloverConnectorService.class);
        startService(intent);
        bindService(intent, cloverServiceConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * Service connection for binding to CloverConnectorService
     */
    private final ServiceConnection cloverServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            CloverConnectorService.CloverServiceBinder binder = (CloverConnectorService.CloverServiceBinder) service;
            cloverService = binder.getService();
            cloverServiceBound = true;

            // Register this activity as a listener for Clover connection events
            cloverService.registerListener(MainActivity.this);

            // Check if Bluetooth is enabled
            if (!cloverService.isBluetoothEnabled()) {
                Intent enableBtIntent = cloverService.getBluetoothEnableIntent();
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            } else {
                updateStatus("Ready to connect to Clover");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            cloverServiceBound = false;
            updateStatus("Clover service disconnected");
        }
    };

    /**
     * Show a dialog to pair with Clover
     */
    private void showCloverPairingDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Clover Pairing");

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_clover_pairing, null);
        ListView deviceListView = view.findViewById(R.id.deviceListView);
        Button scanButton = view.findViewById(R.id.scanButton);

        // Create adapter for device list
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        deviceListView.setAdapter(adapter);

        builder.setView(view);
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        deviceListDialog = builder.create();
        deviceListDialog.show();

        // Set up scan button
        scanButton.setOnClickListener(v -> {
            // Start discovery
            if (cloverServiceBound) {
                updateStatus("Scanning for Clover devices...");
                scanButton.setText("Scanning...");
                scanButton.setEnabled(false);
                cloverService.startDeviceDiscovery();
            }
        });

        // Set up device list click listener
        deviceListView.setOnItemClickListener((parent, view1, position, id) -> {
            if (position < discoveredDevices.size()) {
                BluetoothDevice selectedDevice = discoveredDevices.get(position);

                // Stop discovery
                if (cloverServiceBound) {
                    cloverService.stopDeviceDiscovery();
                }

                // Connect to device
                connectToCloverDevice(selectedDevice);

                // Dismiss dialog
                deviceListDialog.dismiss();
            }
        });

        // Get already paired devices
        if (cloverServiceBound) {
            updateStatus("Getting paired Clover devices...");
            List<BluetoothDevice> pairedDevices = cloverService.getPairedCloverDevices();
            discoveredDevices.clear();
            discoveredDevices.addAll(pairedDevices);

            // Update the adapter
            updateDeviceListAdapter(adapter);

            if (pairedDevices.isEmpty()) {
                updateStatus("No paired Clover devices found. Try scanning.");
            } else {
                updateStatus("Found " + pairedDevices.size() + " paired Clover devices");
            }
        }
    }

    /**
     * Connect to a Clover device
     */
    @SuppressLint("MissingPermission")
    private void connectToCloverDevice(BluetoothDevice device) {
        if (cloverServiceBound) {
            updateStatus("Connecting to " + device.getName() + "...");
            cloverService.connectToDevice(device);
        }
    }

    /**
     * Update the device list adapter
     */
    @SuppressLint("MissingPermission")
    private void updateDeviceListAdapter(ArrayAdapter<String> adapter) {
        adapter.clear();
        for (BluetoothDevice device : discoveredDevices) {
            adapter.add(device.getName() + "\n" + device.getAddress());
        }
        adapter.notifyDataSetChanged();
    }

    /**
     * Update the status TextView
     */
    private void updateStatus(String status) {
        Log.d(TAG, status);
        runOnUiThread(() -> {
            statusTextView.setText(status);
        });
    }

    // CloverConnectionListener implementations

    @Override
    public void onDeviceDiscovered(List<BluetoothDevice> devices) {
        runOnUiThread(() -> {
            discoveredDevices.clear();
            discoveredDevices.addAll(devices);

            updateStatus("Found " + devices.size() + " Clover devices");

            // Update the adapter if dialog is showing
            if (deviceListDialog != null && deviceListDialog.isShowing()) {
                ListView deviceListView = deviceListDialog.findViewById(R.id.deviceListView);
                if (deviceListView != null) {
                    ArrayAdapter<String> adapter = (ArrayAdapter<String>) deviceListView.getAdapter();
                    updateDeviceListAdapter(adapter);
                }

                // Reset scan button
                Button scanButton = deviceListDialog.findViewById(R.id.scanButton);
                if (scanButton != null) {
                    scanButton.setText("Scan for Devices");
                    scanButton.setEnabled(true);
                }
            }
        });
    }

    @Override
    public void onConnectionEstablished() {
        runOnUiThread(() -> {
            isCloverConnected = true;
            updateStatus("Connected to Clover device");
            Toast.makeText(this, "Connected to Clover device", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onConnectionFailed(String reason) {
        runOnUiThread(() -> {
            isCloverConnected = false;
            updateStatus("Connection failed: " + reason);
            Toast.makeText(this, "Connection failed: " + reason, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onDeviceDisconnected() {
        runOnUiThread(() -> {
            isCloverConnected = false;
            updateStatus("Clover device disconnected");
            Toast.makeText(this, "Clover device disconnected", Toast.LENGTH_SHORT).show();
        });
    }
}