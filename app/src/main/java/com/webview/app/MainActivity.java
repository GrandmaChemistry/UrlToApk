package com.webview.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
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

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST = 1001;

    private WebView webView;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> filePathCallback;
    private JsBridge jsBridge;
    private FusedLocationProviderClient fusedLocationClient;

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
        
        setThemeColor();
        setContentView(R.layout.activity_main);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        initViews();
        initWebView();
        loadUrl();
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
        webSettings.setAppCacheEnabled(true);
        
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
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                
                // Inject JS Bridge helper
                injectJsBridgeHelper();
            }
        });
        
        // WebChromeClient
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
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
                "  getNetworkType: function() { return AndroidBridge.getNetworkType(); }," +
                "  copyToClipboard: function(text) { AndroidBridge.copyToClipboard(text); }," +
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
                "  setTitle: function(title) { AndroidBridge.setTitle(title); }" +
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
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(5000)
                .setMaxUpdates(1)
                .build();

        LocationCallback locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    sendLocationToJs(location, "success");
                } else {
                    sendLocationErrorToJs("error", "无法获取位置信息");
                }
                fusedLocationClient.removeLocationUpdates(this);
            }
        };

        // First try to get last known location
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        sendLocationToJs(location, "success");
                    } else {
                        // Request fresh location
                        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
                    }
                })
                .addOnFailureListener(e -> {
                    // Request fresh location on failure
                    fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
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
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLocation();
            } else {
                sendLocationErrorToJs("permission_denied", "位置权限被拒绝");
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webView.canGoBack()) {
                webView.goBack();
                return true;
            } else {
                showExitConfirmDialog();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void showExitConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle("退出应用")
                .setMessage("确定要退出应用吗？")
                .setPositiveButton("确定", (dialog, which) -> finish())
                .setNegativeButton("取消", null)
                .show();
    }

    public void executeJsCallback(String callbackName, String data) {
        runOnUiThread(() -> {
            String js = "javascript:if(window." + callbackName + "){window." + callbackName + "(" + data + ");}";
            webView.evaluateJavascript(js, null);
        });
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
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
