package com.example.mexikhanakiosk;

import static java.lang.Thread.sleep;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import androidx.appcompat.app.AppCompatActivity;


public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_TIME_OUT = 10000; // 10 seconds
    private ProgressBar progressBar;
    private int progressStatus = 0;
    private final Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Full-screen immersive mode
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        // Prevent device from sleeping
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Set content view to splash screen layout
        setContentView(R.layout.activity_splash);

        // Initialize the progress bar
        progressBar = findViewById(R.id.progressBar);

        // Start progress animation
        new Thread(this::run).start();
    }

    private void run() {
        while (progressStatus < 100) {
            progressStatus += 1;
            handler.post(() -> progressBar.setProgress(progressStatus));

            try {
                sleep(SPLASH_TIME_OUT / 100); // Smoothly fills in 10s
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Move to MainActivity when progress completes
        Intent intent = new Intent(SplashActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
