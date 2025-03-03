package com.example.mexikhanakiosk;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Handler;
import android.util.Log;
import android.webkit.WebView;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.mexikhanakiosk.KioskUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class KioskStatusManager {
    private static final String SUPABASE_URL = "https://your-supabase-url.supabase.co/rest/v1/kiosks";
    private static final String SUPABASE_API_KEY = "your-supabase-api-key"; // Replace with actual API key
    private static final int UPDATE_INTERVAL = 30000; // 30 seconds

    private final Context context;
    private final WebView webView;
    private final RequestQueue requestQueue;
    private final Handler handler = new Handler();
    private final String kioskId;  // Dynamically extracted Kiosk ID

    public KioskStatusManager(Context context, WebView webView) {
        this.context = context;
        this.webView = webView;
        this.requestQueue = Volley.newRequestQueue(context);
        this.kioskId = KioskUtils.getKioskIdFromUrl(webView.getUrl());

        Log.d("KioskStatus", "Initialized with Kiosk ID: " + kioskId);
    }

    /**
     * Get the current battery level of the device.
     */
    public int getBatteryLevel() {
        BatteryManager batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    /**
     * Check if the device is connected to the internet.
     */
    public boolean isConnected() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
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
     * Send the battery & WiFi status to Supabase.
     */
    private void sendKioskStatus() {
        if (kioskId == null) {
            Log.e("KioskStatus", "Kiosk ID is null, skipping status update.");
            return;
        }

        int batteryLevel = getBatteryLevel();
        String wifiStatus = isConnected() ? "online" : "offline";

        JSONObject postData = new JSONObject();
        try {
            postData.put("id", kioskId);
            postData.put("battery", batteryLevel);
            postData.put("wifi_status", wifiStatus);
        } catch (JSONException e) {
            Log.e("KioskStatus", "Kiosk ID is null, skipping status update.");

            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, SUPABASE_URL, postData,
                response -> Log.d("KioskStatus", "Status updated successfully"),
                error -> Log.e("KioskStatus", "Error updating status: " + error.toString())
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("apikey", SUPABASE_API_KEY);
                headers.put("Content-Type", "application/json");
                return headers;
            }
        };


        requestQueue.add(request);
    }

    /**
     * Check if the admin has set the kiosk to "offline" and update the WebView accordingly.
     */
    private void checkAppStatus() {
        if (kioskId == null) {
            Log.e("KioskStatus", "Kiosk ID is null, skipping status update.");
            return;
        }

        String url = SUPABASE_URL + "?id=eq." + kioskId;

        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        if (response.length() > 0) {
                            JSONObject kioskData = response.getJSONObject(0);
                            String appStatus = kioskData.optString("app_status", "online");

                            Log.d("KioskStatus", "Fetched App Status: " + appStatus);

                            if (appStatus.equals("offline")) {
                                webView.post(() -> webView.loadUrl("file:///android_asset/offline.html"));
                            } else {
                                webView.post(() -> webView.loadUrl("https://combined-cms.getnook.ai/kiosk/" + kioskId));
                            }
                        } else {
                            Log.e("KioskStatus", "No kiosk data found for ID: " + kioskId);
                        }
                    } catch (JSONException e) {
                        Log.e("KioskStatus", "JSON parsing error", e);
                    }
                },
                error -> Log.e("KioskStatus", "Failed to fetch app status", error)) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("apikey", SUPABASE_API_KEY);
                headers.put("Content-Type", "application/json");
                return headers;
            }
        };

        requestQueue.add(request);
    }

    /**
     * Start periodic updates (Battery, WiFi, and Admin Command checks).
     */
    public void startStatusUpdates() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                sendKioskStatus();
                checkAppStatus();
                handler.postDelayed(this, UPDATE_INTERVAL);
            }
        }, UPDATE_INTERVAL);
    }

    /**
     * Stop periodic updates.
     */
    public void stopStatusUpdates() {
        handler.removeCallbacksAndMessages(null);
    }
}
