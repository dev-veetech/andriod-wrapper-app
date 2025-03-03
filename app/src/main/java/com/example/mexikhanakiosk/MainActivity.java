package com.example.mexikhanakiosk;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;

public class MainActivity extends AppCompatActivity {
    private static final String KIOSK_URL = "https://combined-cms.getnook.ai/kiosk/23eaac68-c766-455e-b438-68482a5b0971";
    private static final String ADMIN_PIN = "1234";  // Change if needed

    private WebView myWebView;
    private boolean isAdminMode = false;
    private boolean isOffline = false; // Track offline state to prevent unnecessary reloads

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableFullScreen();
        setContentView(R.layout.activity_main);

        myWebView = findViewById(R.id.myWebView);
        setupWebView();
        loadWebView();
        monitorNetwork();

        // Enable kiosk mode (screen pinning)
        enableScreenPinning();

        // Detect long press for admin unlock
        myWebView.setOnLongClickListener(v -> {
            requestAdminAccess();
            return true;
        });
    }

    /**
     * Enables full-screen immersive mode.
     */
    private void enableFullScreen() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }

    /**
     * Configures WebView settings.
     */
    private void setupWebView() {
        WebSettings webSettings = myWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);

        // Add JavaScript interface to communicate with WebView
        myWebView.addJavascriptInterface(new WebAppInterface(this), "Android");

        myWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                showOfflinePage();
            }
        });
    }

    /**
     * JavaScript interface that exposes Android methods to JavaScript
     */
    public class WebAppInterface {
        private Context context;

        public WebAppInterface(Context context) {
            this.context = context;
        }

        @JavascriptInterface
        public void receiveOrderItems(String orderItemsJson) {
            runOnUiThread(() -> {
                try {
                    // Process the received order items
                    Toast.makeText(context, "Received order items: " + orderItemsJson, Toast.LENGTH_LONG).show();

                    // Here you can parse the JSON and handle the order items
                    processOrderItems(orderItemsJson);
                } catch (Exception e) {
                    Toast.makeText(context, "Error processing order: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    /**
     * Process the order items received from the WebView
     */
    private void processOrderItems(String orderItemsJson) {
        // TODO: Implement your order processing logic here
        // This could include:
        // - Parsing the JSON
        // - Saving to a database
        // - Sending to a printer
        // - Updating UI
        // - etc.

        try {
            JSONArray items = new JSONArray(orderItemsJson);
            int itemCount = items.length();

            // Log information about the order
            System.out.println("Received " + itemCount + " items in order");

            // Further processing...
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Loads either the kiosk URL or the offline page.
     */
    private void loadWebView() {
        if (isConnected()) {
            myWebView.loadUrl(KIOSK_URL);
            isOffline = false; // Reset offline flag when successfully loading
        } else {
            showOfflinePage();
        }
    }

    /**
     * Displays the offline page.
     */
    private void showOfflinePage() {
        if (!isOffline) { // Avoid unnecessary reload if already offline
            myWebView.loadUrl("file:///android_asset/offline.html");
            isOffline = true;
        }
    }

    /**
     * Checks if the device is connected to the internet.
     */
    private boolean isConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            Network activeNetwork = cm.getActiveNetwork();
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(activeNetwork);
            return capabilities != null && (
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        }
        return false;
    }

    /**
     * Monitors network connectivity and reloads the WebView when internet is restored.
     */
    private void monitorNetwork() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();

            cm.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    runOnUiThread(() -> {
                        if (isOffline) { // Only reload if previously offline
                            loadWebView();
                        }
                    });
                }
            });
        }
    }

    /**
     * Enables Android's built-in kiosk mode (screen pinning).
     */
    private void enableScreenPinning() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            startLockTask();
        }
    }

    /**
     * Prompts for admin PIN to disable kiosk mode.
     */
    private void requestAdminAccess() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Admin PIN");

        final EditText input = new EditText(this);
        builder.setView(input);

        builder.setPositiveButton("Unlock", (dialog, which) -> {
            String enteredPin = input.getText().toString();
            if (enteredPin.equals(ADMIN_PIN)) {
                exitKioskMode();
            } else {
                Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    /**
     * Exits kiosk mode.
     */
    private void exitKioskMode() {
        if (!isAdminMode) {
            isAdminMode = true;
            stopLockTask(); // Exits screen pinning
            Toast.makeText(this, "Admin Mode Activated. Press back to exit.", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Disables back button unless in admin mode.
     */
    @Override
    public void onBackPressed() {
        if (!isAdminMode) {
            return;
        }
        super.onBackPressed();
    }
}