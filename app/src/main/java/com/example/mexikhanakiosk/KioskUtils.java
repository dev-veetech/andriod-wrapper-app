package com.example.mexikhanakiosk;

import android.net.Uri;

public class KioskUtils {
    /**
     * Extracts the Kiosk ID from the WebView URL.
     */
    public static String getKioskIdFromUrl(String url) {
        try {
            Uri uri = Uri.parse(url);
            String pathSegment = uri.getLastPathSegment(); // Extract last segment (Kiosk ID)
            return pathSegment != null ? pathSegment : "default-kiosk-id"; // Default if null
        } catch (Exception e) {
            return "default-kiosk-id"; // Handle malformed URLs
        }
    }
}
