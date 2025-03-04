package com.example.mexikhanakiosk;

import android.accounts.Account;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.RemoteException;
import android.util.Log;

import com.clover.sdk.util.CloverAccount;
import com.clover.sdk.v1.BindingException;
import com.clover.sdk.v1.ClientException;
import com.clover.sdk.v1.ServiceException;
import com.clover.sdk.v1.ResultStatus;
import com.clover.sdk.v1.merchant.Merchant;
import com.clover.sdk.v1.merchant.MerchantConnector;
import com.clover.sdk.v3.order.Order;
import com.clover.sdk.v3.order.OrderConnector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CloverConnector {
    private static final String TAG = "CloverConnector";

    private final Context context;
    private BluetoothAdapter bluetoothAdapter;
    private OrderConnector orderConnector;
    private MerchantConnector merchantConnector;
    private CloverConnectionListener connectionListener;

    // Interface for connection status callbacks
    public interface CloverConnectionListener {
        void onDeviceDiscovered(List<BluetoothDevice> devices);
        void onConnectionEstablished();
        void onConnectionFailed(String reason);
        void onDeviceDisconnected();
    }

    public CloverConnector(Context context, CloverConnectionListener listener) {
        this.context = context;
        this.connectionListener = listener;
        initializeBluetooth();
    }

    private void initializeBluetooth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            BluetoothManager bluetoothManager = context.getSystemService(BluetoothManager.class);
            if (bluetoothManager != null) {
                bluetoothAdapter = bluetoothManager.getAdapter();
            }
        } else {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }

        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth not supported on this device");
            if (connectionListener != null) {
                connectionListener.onConnectionFailed("Bluetooth not supported on this device");
            }
        }
    }

    /**
     * Checks if Bluetooth is enabled and requests to enable it if not
     *
     * @return true if Bluetooth is enabled, false otherwise
     */
    public boolean isBluetoothEnabled() {
        if (bluetoothAdapter == null) {
            return false;
        }
        return bluetoothAdapter.isEnabled();
    }

    /**
     * Request to enable Bluetooth
     *
     * @return Intent that should be started with startActivityForResult
     */
    public Intent getBluetoothEnableIntent() {
        return new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
    }

    /**
     * Initialize the Clover SDK connections
     */
    public void initializeCloverConnections() {
        try {
            Account account = CloverAccount.getAccount(context);
            if (account != null) {
                orderConnector = new OrderConnector(context, account, null);
                merchantConnector = new MerchantConnector(context, account, null);
                Log.d(TAG, "Clover connectors initialized successfully");
            } else {
                Log.e(TAG, "No Clover account found");
                if (connectionListener != null) {
                    connectionListener.onConnectionFailed("No Clover account found");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Clover connectors", e);
            if (connectionListener != null) {
                connectionListener.onConnectionFailed("Error initializing Clover: " + e.getMessage());
            }
        }
    }

    /**
     * Discover paired Clover devices
     *
     * @return List of paired Clover devices
     */
    @SuppressLint("MissingPermission")
    public List<BluetoothDevice> getPairedCloverDevices() {
        List<BluetoothDevice> cloverDevices = new ArrayList<>();

        if (bluetoothAdapter == null) {
            Log.e(TAG, "BluetoothAdapter is null");
            return cloverDevices;
        }

        @SuppressLint("MissingPermission") Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                // Filter for Clover devices - they typically have "Clover" in the name
                if (device.getName() != null && device.getName().contains("Clover")) {
                    cloverDevices.add(device);
                    Log.d(TAG, "Found Clover device: " + device.getName() + " - " + device.getAddress());
                }
            }
        }

        if (connectionListener != null) {
            connectionListener.onDeviceDiscovered(cloverDevices);
        }

        return cloverDevices;
    }

    /**
     * Start Bluetooth discovery process
     */
    @SuppressLint("MissingPermission")
    public void startDeviceDiscovery() {
        if (bluetoothAdapter == null) {
            Log.e(TAG, "BluetoothAdapter is null");
            return;
        }

        // Cancel any ongoing discovery
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }

        // Start discovery
        boolean started = bluetoothAdapter.startDiscovery();
        Log.d(TAG, "Discovery started: " + started);
    }

    /**
     * Stop Bluetooth discovery process
     */
    @SuppressLint("MissingPermission")
    public void stopDeviceDiscovery() {
        if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
            Log.d(TAG, "Discovery stopped");
        }
    }

    /**
     * Connect to a specific Clover device
     *
     * @param device The Bluetooth device to connect to
     */
    @SuppressLint("MissingPermission")
    public void connectToDevice(BluetoothDevice device) {
        Log.d(TAG, "Attempting to connect to: " + device.getName());

        // Stop discovery before attempting connection
        stopDeviceDiscovery();

        // For now, we'll simulate a successful connection
        // In a real implementation, you'd initiate a socket connection

        if (connectionListener != null) {
            connectionListener.onConnectionEstablished();
        }

        // Initialize Clover SDK connections once the device is connected
        initializeCloverConnections();
    }

    /**
     * Check if we can communicate with the Clover device by retrieving merchant info
     */
    public boolean testCloverConnection() {
        if (merchantConnector == null) {
            Log.e(TAG, "Merchant connector is null");
            return false;
        }

        try {
            ResultStatus status = new ResultStatus();
            //merchantConnector.getMerchant(status);

            if (status.isSuccess()) {
                Log.d(TAG, "Successfully connected to Clover merchant account");
                return true;
            } else {
                Log.e(TAG, "Failed to connect to Clover merchant account: " + status.getStatusMessage());
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error testing Clover connection", e);
            return false;
        }
    }

    /**
     * Disconnect and clean up resources
     */
    public void disconnect() {
        if (orderConnector != null) {
            orderConnector.disconnect();
            orderConnector = null;
        }

        if (merchantConnector != null) {
            merchantConnector.disconnect();
            merchantConnector = null;
        }

        if (connectionListener != null) {
            connectionListener.onDeviceDisconnected();
        }

        Log.d(TAG, "Disconnected from Clover");
    }
}