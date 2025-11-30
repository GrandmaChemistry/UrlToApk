package com.webview.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.io.InputStream;

public class SplashActivity extends AppCompatActivity {

    private static final long FADE_IN_DURATION = 300; // 300ms fade in
    private static final long FADE_OUT_DURATION = 500; // 500ms fade out
    private static final long DISPLAY_AFTER_LOAD_DURATION = 1000; // 1 second after load
    private static final long MAX_SPLASH_DURATION = 5000; // Maximum 5 seconds

    private ImageView splashImage;
    private LinearLayout defaultSplashContainer;
    private ConstraintLayout splashContainer;
    private Handler handler;
    private boolean isFinishingAnimation = false;
    private static SplashActivity instance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Hide status bar for splash screen
        hideStatusBar();
        
        setContentView(R.layout.activity_splash);
        
        instance = this;
        handler = new Handler(Looper.getMainLooper());

        splashImage = findViewById(R.id.splashImage);
        defaultSplashContainer = findViewById(R.id.defaultSplashContainer);
        splashContainer = findViewById(R.id.splashContainer);
        TextView appName = findViewById(R.id.appName);

        // Set app name dynamically
        appName.setText(BuildConfig.APP_NAME);

        // Start with invisible content for fade in effect
        splashContainer.setAlpha(0f);

        // Check if custom splash image exists
        loadSplashImage();

        // Fade in animation
        startFadeInAnimation();

        // Start MainActivity in background
        startMainActivity();
    }

    private void hideStatusBar() {
        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            controller.hide(WindowInsetsCompat.Type.statusBars());
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
        
        // Make status bar transparent
        window.setStatusBarColor(Color.TRANSPARENT);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
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
            }
        } catch (Exception e) {
            // No custom splash image, use default (app icon)
            splashImage.setVisibility(View.GONE);
            defaultSplashContainer.setVisibility(View.VISIBLE);
        }
    }

    private void startFadeInAnimation() {
        AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
        fadeIn.setDuration(FADE_IN_DURATION);
        fadeIn.setFillAfter(true);
        fadeIn.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                splashContainer.setAlpha(1f);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
        splashContainer.startAnimation(fadeIn);
    }

    private void startMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("FROM_SPLASH", true);
        startActivity(intent);
        
        // Fallback timer - finish splash after max duration
        handler.postDelayed(this::triggerFadeOutAndFinish, MAX_SPLASH_DURATION);
    }

    /**
     * Called by MainActivity when WebView has finished loading
     */
    public static void onWebViewLoadComplete() {
        if (instance != null && !instance.isFinishing() && !instance.isFinishingAnimation) {
            instance.handler.postDelayed(instance::triggerFadeOutAndFinish, DISPLAY_AFTER_LOAD_DURATION);
        }
    }

    private void triggerFadeOutAndFinish() {
        if (isFinishing() || isFinishingAnimation) return;
        isFinishingAnimation = true;
        
        // Remove any pending callbacks
        handler.removeCallbacksAndMessages(null);
        
        fadeOutAndFinish();
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
    protected void onDestroy() {
        super.onDestroy();
        if (instance == this) {
            instance = null;
        }
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }

    @Override
    public void onBackPressed() {
        // Disable back button during splash
    }
}
