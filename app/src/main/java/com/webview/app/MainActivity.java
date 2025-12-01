package com.webview.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST = 1001;
    private static final int STORAGE_PERMISSION_REQUEST = 1002;
    private static final long LOCATION_UPDATE_INTERVAL = 10000; // 10 seconds
    private static final long LOCATION_MIN_UPDATE_INTERVAL = 5000; // 5 seconds
    private static final long BACK_PRESS_EXIT_INTERVAL = 1000; // 1 second for double tap exit

    private WebView webView;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> filePathCallback;
    private JsBridge jsBridge;
    private FusedLocationProviderClient fusedLocationClient;
    private long lastBackPressTime = 0;
    private Toast exitToast;
    private boolean keyListenerEnabled = false;
    private boolean exitListenerEnabled = false;
    private boolean isFromSplash = false;
    private boolean pageLoadComplete = false;
    private Handler progressHandler = new Handler(Looper.getMainLooper());
    private String pendingSaveBase64 = null;

    private final ActivityResultLauncher<Intent> fileChooserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (filePathCallback != null) {
                    Uri[] results = null;
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String dataString = result.getData().getDataString();
                        if (dataString != null) {
                            results = new Uri[]{Uri.parse(dataString)};
                        }
                    }
                    filePathCallback.onReceiveValue(results);
                    filePathCallback = null;
                }
            }
    );

    private final ActivityResultLauncher<Intent> qrCodeScannerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                String scanResult = null;
                String status = "cancelled";
                
                if (result.getData() != null) {
                    scanResult = result.getData().getStringExtra(ScannerActivity.EXTRA_SCAN_RESULT);
                }
                
                if (result.getResultCode() == ScannerActivity.RESULT_SUCCESS && scanResult != null) {
                    status = "success";
                } else if (result.getResultCode() == ScannerActivity.RESULT_ERROR) {
                    status = "error";
                    if ("PERMISSION_DENIED".equals(scanResult)) {
                        status = "permission_denied";
                    }
                }
                
                final String finalStatus = status;
                final String finalResult = scanResult;
                
                runOnUiThread(() -> {
                    try {
                        JSONObject response = new JSONObject();
                        response.put("status", finalStatus);
                        response.put("result", finalResult != null ? finalResult : "");
                        executeJsCallback("_qrCallback", response.toString());
                    } catch (Exception e) {
                        executeJsCallback("_qrCallback", "{\"status\":\"error\",\"result\":\"\"}");
                    }
                });
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Check if launched from splash screen
        isFromSplash = getIntent().getBooleanExtra("FROM_SPLASH", false);
        
        setThemeColor();
        setContentView(R.layout.activity_main);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        initViews();
        initWebView();
        
        // Restore state if savedInstanceState is not null
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            loadUrl();
        }
    }
    
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) {
            webView.saveState(outState);
        }
    }

    private void setThemeColor() {
        try {
            String colorStr = BuildConfig.THEME_COLOR;
            int color = Color.parseColor(colorStr);
            
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(color);
            
            WindowInsetsControllerCompat windowInsetsController = 
                    WindowCompat.getInsetsController(window, window.getDecorView());
            windowInsetsController.setAppearanceLightStatusBars(isColorLight(color));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isColorLight(int color) {
        double darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return darkness < 0.5;
    }

    private void initViews() {
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView() {
        WebSettings webSettings = webView.getSettings();
        
        // Enable JavaScript
        webSettings.setJavaScriptEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        
        // DOM storage
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        
        // Cache settings
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        
        // Media settings
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        
        // Mixed content
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        
        // Viewport settings
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        
        // User agent
        String userAgent = webSettings.getUserAgentString();
        webSettings.setUserAgentString(userAgent + " WebViewApp/1.0");
        
        // Geolocation
        webSettings.setGeolocationEnabled(true);
        
        // Cookie settings
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);
        
        // Initialize JS Bridge
        jsBridge = new JsBridge(this, webView);
        webView.addJavascriptInterface(jsBridge, "AndroidBridge");
        
        // WebViewClient
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                
                // Handle external links
                if (url.startsWith("tel:") || url.startsWith("mailto:") || 
                    url.startsWith("sms:") || url.startsWith("geo:")) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    try {
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "无法打开链接", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
                
                // Handle market links
                if (url.startsWith("market:") || url.startsWith("intent:")) {
                    try {
                        Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                        startActivity(intent);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return true;
                }
                
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                pageLoadComplete = false;
                showProgressBar();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                pageLoadComplete = true;
                hideProgressBar();
                
                // Notify SplashActivity that page load is complete
                SplashActivity.onWebViewLoadComplete();
                
                // Inject JS Bridge helper
                injectJsBridgeHelper();
            }
        });
        
        // WebChromeClient
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                
                // Ensure progress bar is visible while loading
                if (newProgress < 100 && !pageLoadComplete) {
                    showProgressBar();
                }
                
                // Hide progress bar when complete
                if (newProgress >= 100) {
                    hideProgressBar();
                }
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                MainActivity.this.filePathCallback = filePathCallback;
                Intent intent = fileChooserParams.createIntent();
                try {
                    fileChooserLauncher.launch(intent);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "无法选择文件", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> request.grant(request.getResources()));
            }
        });
    }

    private void injectJsBridgeHelper() {
        String jsCode = "javascript:(function() {" +
                "if (window.NativeBridge) return;" +
                "window.NativeBridge = {" +
                "  showToast: function(message) { AndroidBridge.showToast(message); }," +
                "  vibrate: function(duration) { AndroidBridge.vibrate(duration); }," +
                "  getDeviceInfo: function() { return JSON.parse(AndroidBridge.getDeviceInfo()); }," +
                "  getExtendedDeviceInfo: function() { return JSON.parse(AndroidBridge.getExtendedDeviceInfo()); }," +
                "  getNetworkType: function() { return AndroidBridge.getNetworkType(); }," +
                "  copyToClipboard: function(text) { AndroidBridge.copyToClipboard(text); }," +
                "  getClipboardContent: function() { return AndroidBridge.getClipboardContent(); }," +
                "  openUrl: function(url) { AndroidBridge.openUrl(url); }," +
                "  share: function(title, text, url) { AndroidBridge.share(title, text, url); }," +
                "  scanQRCode: function(callback) { window._qrCallback = callback; AndroidBridge.scanQRCode(); }," +
                "  getCurrentLocation: function(callback) { window._locationCallback = callback; AndroidBridge.getCurrentLocation(); }," +
                "  setStatusBarColor: function(color) { AndroidBridge.setStatusBarColor(color); }," +
                "  getAppVersion: function() { return AndroidBridge.getAppVersion(); }," +
                "  exitApp: function() { AndroidBridge.exitApp(); }," +
                "  goBack: function() { AndroidBridge.goBack(); }," +
                "  reload: function() { AndroidBridge.reload(); }," +
                "  clearCache: function() { AndroidBridge.clearCache(); }," +
                "  setScreenOrientation: function(orientation) { AndroidBridge.setScreenOrientation(orientation); }," +
                "  getBatteryLevel: function() { return AndroidBridge.getBatteryLevel(); }," +
                "  isWifiConnected: function() { return AndroidBridge.isWifiConnected(); }," +
                "  playSound: function(soundName) { AndroidBridge.playSound(soundName); }," +
                "  takeScreenshot: function(callback) { window._screenshotCallback = callback; AndroidBridge.takeScreenshot(); }," +
                "  takeFullScreenshot: function(callback) { window._fullScreenshotCallback = callback; AndroidBridge.takeFullScreenshot(); }," +
                "  saveToGallery: function(base64, callback) { window._saveGalleryCallback = callback; AndroidBridge.saveToGallery(base64); }," +
                "  getContacts: function(callback) { window._contactsCallback = callback; AndroidBridge.getContacts(); }," +
                "  makeCall: function(phone) { AndroidBridge.makeCall(phone); }," +
                "  sendSMS: function(phone, message) { AndroidBridge.sendSMS(phone, message); }," +
                "  setLocalStorage: function(key, value) { AndroidBridge.setLocalStorage(key, value); }," +
                "  getLocalStorage: function(key) { return AndroidBridge.getLocalStorage(key); }," +
                "  removeLocalStorage: function(key) { AndroidBridge.removeLocalStorage(key); }," +
                "  clearLocalStorage: function() { AndroidBridge.clearLocalStorage(); }," +
                "  showLoading: function(message) { AndroidBridge.showLoading(message); }," +
                "  hideLoading: function() { AndroidBridge.hideLoading(); }," +
                "  showAlert: function(title, message, callback) { window._alertCallback = callback; AndroidBridge.showAlert(title, message); }," +
                "  showConfirm: function(title, message, callback) { window._confirmCallback = callback; AndroidBridge.showConfirm(title, message); }," +
                "  enableBackButton: function(enabled) { AndroidBridge.enableBackButton(enabled); }," +
                "  setTitle: function(title) { AndroidBridge.setTitle(title); }," +
                "  openSystemSettings: function() { AndroidBridge.openSystemSettings(); }," +
                "  openAppSettings: function() { AndroidBridge.openAppSettings(); }," +
                "  enterFullscreen: function() { AndroidBridge.enterFullscreen(); }," +
                "  exitFullscreen: function() { AndroidBridge.exitFullscreen(); }," +
                "  isFullscreenMode: function() { return AndroidBridge.isFullscreenMode(); }," +
                "  registerKeyListener: function(callback) { window._keyEventCallback = callback; AndroidBridge.registerKeyListener(); }," +
                "  unregisterKeyListener: function() { window._keyEventCallback = null; AndroidBridge.unregisterKeyListener(); }," +
                "  registerExitListener: function(callback) { window._exitEventCallback = callback; AndroidBridge.registerExitListener(); }," +
                "  unregisterExitListener: function() { window._exitEventCallback = null; AndroidBridge.unregisterExitListener(); }," +
                "  getGrantedPermissions: function() { return JSON.parse(AndroidBridge.getGrantedPermissions()); }" +
                "};" +
                "console.log('NativeBridge initialized');" +
                "if (window.onNativeBridgeReady) { window.onNativeBridgeReady(); }" +
                "document.dispatchEvent(new Event('NativeBridgeReady'));" +
                "})()";
        webView.evaluateJavascript(jsCode, null);
    }

    private void loadUrl() {
        String url = BuildConfig.APP_URL;
        webView.loadUrl(url);
    }

    /**
     * Start QR code scanner activity
     */
    public void startQRCodeScanner() {
        Intent intent = new Intent(this, ScannerActivity.class);
        qrCodeScannerLauncher.launch(intent);
    }

    /**
     * Request current location with permission handling
     */
    public void requestLocation() {
        if (hasLocationPermission()) {
            getLocation();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_PERMISSION_REQUEST);
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    private void getLocation() {
        // Check if location services are enabled
        android.location.LocationManager locationManager = (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
        boolean isGpsEnabled = false;
        boolean isNetworkEnabled = false;
        
        try {
            isGpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);
        } catch (Exception ex) {
            // GPS provider not available
        }
        
        try {
            isNetworkEnabled = locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER);
        } catch (Exception ex) {
            // Network provider not available
        }
        
        if (!isGpsEnabled && !isNetworkEnabled) {
            sendLocationErrorToJs("error", "位置服务未开启，请在系统设置中开启位置服务");
            return;
        }
        
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATE_INTERVAL)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(LOCATION_MIN_UPDATE_INTERVAL)
                .setMaxUpdates(1)
                .build();

        // Create a timeout handler
        final android.os.Handler timeoutHandler = new android.os.Handler(Looper.getMainLooper());
        final AtomicBoolean locationReceived = new AtomicBoolean(false);
        
        LocationCallback locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (locationReceived.getAndSet(true)) return; // Already handled
                timeoutHandler.removeCallbacksAndMessages(null);
                
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    sendLocationToJs(location, "success");
                } else {
                    sendLocationErrorToJs("error", "无法获取位置信息");
                }
                fusedLocationClient.removeLocationUpdates(this);
            }
        };
        
        // Set timeout for location request (10 seconds)
        final LocationCallback finalLocationCallback = locationCallback;
        timeoutHandler.postDelayed(() -> {
            if (!locationReceived.getAndSet(true)) {
                fusedLocationClient.removeLocationUpdates(finalLocationCallback);
                sendLocationErrorToJs("error", "获取位置超时");
            }
        }, 10000);

        // First try to get last known location
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null && !locationReceived.getAndSet(true)) {
                        timeoutHandler.removeCallbacksAndMessages(null);
                        sendLocationToJs(location, "success");
                    } else if (!locationReceived.get()) {
                        // Request fresh location
                        try {
                            fusedLocationClient.requestLocationUpdates(locationRequest, finalLocationCallback, Looper.getMainLooper());
                        } catch (Exception e) {
                            if (!locationReceived.getAndSet(true)) {
                                timeoutHandler.removeCallbacksAndMessages(null);
                                sendLocationErrorToJs("error", "请求位置更新失败: " + e.getMessage());
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    if (!locationReceived.get()) {
                        // Request fresh location on failure
                        try {
                            fusedLocationClient.requestLocationUpdates(locationRequest, finalLocationCallback, Looper.getMainLooper());
                        } catch (Exception ex) {
                            if (!locationReceived.getAndSet(true)) {
                                timeoutHandler.removeCallbacksAndMessages(null);
                                sendLocationErrorToJs("error", "请求位置更新失败: " + ex.getMessage());
                            }
                        }
                    }
                });
    }

    private void sendLocationToJs(Location location, String status) {
        runOnUiThread(() -> {
            try {
                JSONObject response = new JSONObject();
                response.put("status", status);
                response.put("latitude", location.getLatitude());
                response.put("longitude", location.getLongitude());
                response.put("accuracy", location.getAccuracy());
                response.put("altitude", location.getAltitude());
                response.put("speed", location.getSpeed());
                response.put("timestamp", location.getTime());
                executeJsCallback("_locationCallback", response.toString());
            } catch (Exception e) {
                sendLocationErrorToJs("error", e.getMessage());
            }
        });
    }

    private void sendLocationErrorToJs(String status, String message) {
        runOnUiThread(() -> {
            try {
                JSONObject response = new JSONObject();
                response.put("status", status);
                response.put("message", message != null ? message : "Unknown error");
                executeJsCallback("_locationCallback", response.toString());
            } catch (Exception e) {
                executeJsCallback("_locationCallback", "{\"status\":\"error\",\"message\":\"Unknown error\"}");
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            // Check if either location permission was granted
            boolean permissionGranted = false;
            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) {
                    permissionGranted = true;
                    break;
                }
            }
            
            if (permissionGranted) {
                getLocation();
            } else {
                sendLocationErrorToJs("permission_denied", "位置权限被拒绝");
            }
        } else if (requestCode == STORAGE_PERMISSION_REQUEST) {
            // Check if storage permission was granted
            boolean permissionGranted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            
            if (permissionGranted && pendingSaveBase64 != null) {
                // Permission granted, proceed with saving
                jsBridge.saveToGalleryInternal(pendingSaveBase64);
                pendingSaveBase64 = null;
            } else {
                // Permission denied
                sendStoragePermissionDenied();
                pendingSaveBase64 = null;
            }
        }
    }
    
    /**
     * Request storage permission for saving images
     */
    public void requestStoragePermissionForSave(String base64) {
        pendingSaveBase64 = base64;
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                STORAGE_PERMISSION_REQUEST);
    }
    
    /**
     * Check if storage permission is granted
     */
    public boolean hasStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Android 10+ doesn't need storage permission for MediaStore
            return true;
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }
    
    private void sendStoragePermissionDenied() {
        runOnUiThread(() -> {
            try {
                org.json.JSONObject response = new org.json.JSONObject();
                response.put("success", false);
                response.put("message", "用户拒绝存储权限");
                executeJsCallback("_saveGalleryCallback", response.toString());
            } catch (Exception e) {
                executeJsCallback("_saveGalleryCallback", "{\"success\":false,\"message\":\"用户拒绝存储权限\"}");
            }
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Notify JavaScript about key events if listener is enabled
        if (keyListenerEnabled) {
            notifyKeyEvent(keyCode, "keydown");
        }
        
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // In fullscreen mode, directly exit the app instead of exiting fullscreen first
            if (jsBridge != null && jsBridge.isFullscreenMode()) {
                handleBackExit();
                return true;
            }
            
            if (webView.canGoBack()) {
                webView.goBack();
                return true;
            } else {
                handleBackExit();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void notifyKeyEvent(int keyCode, String eventType) {
        runOnUiThread(() -> {
            try {
                JSONObject response = new JSONObject();
                response.put("eventType", eventType);
                
                String keyName;
                switch (keyCode) {
                    case KeyEvent.KEYCODE_BACK:
                        keyName = "back";
                        break;
                    case KeyEvent.KEYCODE_HOME:
                        keyName = "home";
                        break;
                    case KeyEvent.KEYCODE_APP_SWITCH:
                        keyName = "task";
                        break;
                    case KeyEvent.KEYCODE_MENU:
                        keyName = "menu";
                        break;
                    default:
                        keyName = "key_" + keyCode;
                        break;
                }
                response.put("key", keyName);
                response.put("keyCode", keyCode);
                
                executeJsCallback("_keyEventCallback", response.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void handleBackExit() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastBackPressTime <= BACK_PRESS_EXIT_INTERVAL) {
            // Double tap within 0.5 seconds, exit the app
            if (exitToast != null) {
                exitToast.cancel();
            }
            // Notify exit listener before exiting
            if (exitListenerEnabled) {
                notifyExitEvent();
            }
            finish();
        } else {
            // First tap, show toast message at bottom
            lastBackPressTime = currentTime;
            if (exitToast != null) {
                exitToast.cancel();
            }
            exitToast = Toast.makeText(this, R.string.exit_toast_message, Toast.LENGTH_SHORT);
            exitToast.setGravity(Gravity.BOTTOM, 0, 100);
            exitToast.show();
        }
    }

    private void notifyExitEvent() {
        runOnUiThread(() -> {
            try {
                JSONObject response = new JSONObject();
                response.put("event", "exit");
                response.put("timestamp", System.currentTimeMillis());
                executeJsCallback("_exitEventCallback", response.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void setKeyListenerEnabled(boolean enabled) {
        this.keyListenerEnabled = enabled;
    }

    public void setExitListenerEnabled(boolean enabled) {
        this.exitListenerEnabled = enabled;
    }

    public void executeJsCallback(String callbackName, String data) {
        runOnUiThread(() -> {
            String js = "javascript:if(window." + callbackName + "){window." + callbackName + "(" + data + ");}";
            webView.evaluateJavascript(js, null);
        });
    }

    private void showProgressBar() {
        progressHandler.removeCallbacksAndMessages(null);
        if (progressBar.getVisibility() != View.VISIBLE) {
            progressBar.setVisibility(View.VISIBLE);
        }
    }

    private void hideProgressBar() {
        // Delay hiding progress bar slightly to ensure smooth transition
        progressHandler.removeCallbacksAndMessages(null);
        progressHandler.postDelayed(() -> {
            progressBar.setVisibility(View.GONE);
            progressBar.setProgress(0);
        }, 200);
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
    }

    @Override
    protected void onDestroy() {
        if (progressHandler != null) {
            progressHandler.removeCallbacksAndMessages(null);
        }
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
