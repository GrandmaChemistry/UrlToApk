package com.webview.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import java.io.InputStream;

public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DISPLAY_TIME = 1000; // 1 second display after WebView loads
    private static final long FADE_OUT_DURATION = 500; // 500ms fade out

    private ImageView splashImage;
    private LinearLayout defaultSplashContainer;
    private ConstraintLayout splashContainer;
    private boolean hasSplashImage = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        splashImage = findViewById(R.id.splashImage);
        defaultSplashContainer = findViewById(R.id.defaultSplashContainer);
        splashContainer = findViewById(R.id.splashContainer);
        TextView appName = findViewById(R.id.appName);

        // Set app name dynamically
        appName.setText(BuildConfig.APP_NAME);

        // Check if custom splash image exists
        loadSplashImage();

        // Start MainActivity immediately but keep splash visible
        startMainActivity();
    }

    private void loadSplashImage() {
        try {
            // Try to load splash image from assets
            InputStream inputStream = getAssets().open("splash_image.png");
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            inputStream.close();

            if (bitmap != null) {
                splashImage.setImageBitmap(bitmap);
                splashImage.setVisibility(View.VISIBLE);
                defaultSplashContainer.setVisibility(View.GONE);
                hasSplashImage = true;
            }
        } catch (Exception e) {
            // No custom splash image, use default (app icon)
            hasSplashImage = false;
            splashImage.setVisibility(View.GONE);
            defaultSplashContainer.setVisibility(View.VISIBLE);
        }
    }

    private void startMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("FROM_SPLASH", true);
        startActivity(intent);
        
        // Don't finish yet - will be finished when MainActivity signals ready
        // For now, add a fallback timer
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            fadeOutAndFinish();
        }, 3000); // Maximum 3 seconds splash display
    }

    public void fadeOutAndFinish() {
        if (isFinishing()) return;

        AlphaAnimation fadeOut = new AlphaAnimation(1.0f, 0.0f);
        fadeOut.setDuration(FADE_OUT_DURATION);
        fadeOut.setFillAfter(true);
        fadeOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                finish();
                overridePendingTransition(0, 0);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });

        splashContainer.startAnimation(fadeOut);
    }

    @Override
    public void onBackPressed() {
        // Disable back button during splash
    }
}
