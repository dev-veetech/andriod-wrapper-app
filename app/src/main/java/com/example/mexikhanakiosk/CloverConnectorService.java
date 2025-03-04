package com.example.mexikhanakiosk;

import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CloverConnectorService extends Service implements CloverConnector.CloverConnectionListener {
    private static final String TAG = "CloverConnectorService";

    // Binder for client communication
    private final IBinder binder = new CloverServiceBinder();

    // Clover connector instance
    private CloverConnector cloverConnector;

    // List of discovered devices
    private List<BluetoothDevice> discoveredDevices = new ArrayList<>();

    // Listeners for Clover connection updates
    private List<CloverConnector.CloverConnectionListener> listeners = new ArrayList<>();

    // Bluetooth discovery receiver
    private final BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null && device.getName() != null && device.getName().contains("Clover")) {
                    if (!discoveredDevices.contains(device)) {
                        discoveredDevices.add(device);
                        Log.d(TAG, "Discovered new Clover device: " + device.getName());

                        // Notify all listeners
                        notifyDeviceDiscovered();
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Log.d(TAG, "Discovery finished. Found " + discoveredDevices.size() + " Clover devices.");
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "CloverConnectorService created");

        // Initialize the Clover connector
        cloverConnector = new CloverConnector(this, this);

        // Register for Bluetooth discovery events
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(discoveryReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "CloverConnectorService started");
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "CloverConnectorService destroyed");

        // Unregister receivers
        try {
            unregisterReceiver(discoveryReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver not registered
        }

        // Disconnect from Clover
        if (cloverConnector != null) {
            cloverConnector.disconnect();
        }
    }

    /**
     * Register a listener for Clover connection updates
     */
    public void registerListener(CloverConnector.CloverConnectionListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Unregister a listener
     */
    public void unregisterListener(CloverConnector.CloverConnectionListener listener) {
        listeners.remove(listener);
    }

    /**
     * Get paired Clover devices
     */
    public List<BluetoothDevice> getPairedCloverDevices() {
        if (cloverConnector != null) {
            return cloverConnector.getPairedCloverDevices();
        }
        return new ArrayList<>();
    }

    /**
     * Start scanning for Clover devices
     */
    public void startDeviceDiscovery() {
        discoveredDevices.clear();
        if (cloverConnector != null) {
            cloverConnector.startDeviceDiscovery();
        }
    }

    /**
     * Stop scanning for Clover devices
     */
    public void stopDeviceDiscovery() {
        if (cloverConnector != null) {
            cloverConnector.stopDeviceDiscovery();
        }
    }

    /**
     * Connect to a specific Clover device
     */
    public void connectToDevice(BluetoothDevice device) {
        if (cloverConnector != null) {
            cloverConnector.connectToDevice(device);
        }
    }

    /**
     * Test the Clover connection
     */
    public boolean testCloverConnection() {
        if (cloverConnector != null) {
            return cloverConnector.testCloverConnection();
        }
        return false;
    }

    /**
     * Disconnect from Clover
     */
    public void disconnect() {
        if (cloverConnector != null) {
            cloverConnector.disconnect();
        }
    }

    /**
     * Check if Bluetooth is enabled
     */
    public boolean isBluetoothEnabled() {
        if (cloverConnector != null) {
            return cloverConnector.isBluetoothEnabled();
        }
        return false;
    }

    /**
     * Get intent to enable Bluetooth
     */
    public Intent getBluetoothEnableIntent() {
        if (cloverConnector != null) {
            return cloverConnector.getBluetoothEnableIntent();
        }
        return new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
    }

    // Notify all listeners about discovered devices
    private void notifyDeviceDiscovered() {
        for (CloverConnector.CloverConnectionListener listener : listeners) {
            listener.onDeviceDiscovered(new ArrayList<>(discoveredDevices));
        }
    }

    // CloverConnectionListener implementation
    @Override
    public void onDeviceDiscovered(List<BluetoothDevice> devices) {
        for (CloverConnector.CloverConnectionListener listener : listeners) {
            listener.onDeviceDiscovered(devices);
        }
    }

    @Override
    public void onConnectionEstablished() {
        for (CloverConnector.CloverConnectionListener listener : listeners) {
            listener.onConnectionEstablished();
        }
    }

    @Override
    public void onConnectionFailed(String reason) {
        for (CloverConnector.CloverConnectionListener listener : listeners) {
            listener.onConnectionFailed(reason);
        }
    }

    @Override
    public void onDeviceDisconnected() {
        for (CloverConnector.CloverConnectionListener listener : listeners) {
            listener.onDeviceDisconnected();
        }
    }

    /**
     * Binder class for client communication
     */
    public class CloverServiceBinder extends Binder {
        public CloverConnectorService getService() {
            return CloverConnectorService.this;
        }
    }
}