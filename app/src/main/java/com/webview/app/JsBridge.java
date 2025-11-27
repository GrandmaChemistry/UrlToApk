package com.webview.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import org.json.JSONException;
import org.json.JSONObject;

public class JsBridge {
    private final Activity activity;
    private final WebView webView;
    private final SharedPreferences prefs;
    private ProgressDialog loadingDialog;
    private boolean backButtonEnabled = true;

    public JsBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
        this.prefs = activity.getSharedPreferences("WebViewAppPrefs", Context.MODE_PRIVATE);
    }

    @JavascriptInterface
    public void showToast(String message) {
        activity.runOnUiThread(() -> 
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        );
    }

    @JavascriptInterface
    public void vibrate(int duration) {
        Vibrator vibrator;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vibratorManager = (VibratorManager) activity.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = vibratorManager.getDefaultVibrator();
        } else {
            vibrator = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
        }
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(duration);
            }
        }
    }

    @JavascriptInterface
    public String getDeviceInfo() {
        JSONObject info = new JSONObject();
        try {
            info.put("brand", Build.BRAND);
            info.put("model", Build.MODEL);
            info.put("device", Build.DEVICE);
            info.put("sdkVersion", Build.VERSION.SDK_INT);
            info.put("release", Build.VERSION.RELEASE);
            info.put("manufacturer", Build.MANUFACTURER);
            info.put("product", Build.PRODUCT);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return info.toString();
    }

    @JavascriptInterface
    public String getNetworkType() {
        ConnectivityManager cm = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network network = cm.getActiveNetwork();
                if (network != null) {
                    NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
                    if (capabilities != null) {
                        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                            return "wifi";
                        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                            return "cellular";
                        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                            return "ethernet";
                        }
                    }
                }
            } else {
                NetworkInfo networkInfo = cm.getActiveNetworkInfo();
                if (networkInfo != null && networkInfo.isConnected()) {
                    int type = networkInfo.getType();
                    if (type == ConnectivityManager.TYPE_WIFI) {
                        return "wifi";
                    } else if (type == ConnectivityManager.TYPE_MOBILE) {
                        return "cellular";
                    }
                }
            }
        }
        return "none";
    }

    @JavascriptInterface
    public void copyToClipboard(String text) {
        activity.runOnUiThread(() -> {
            ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("text", text);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(activity, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @JavascriptInterface
    public void openUrl(String url) {
        activity.runOnUiThread(() -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                activity.startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(activity, "无法打开链接", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @JavascriptInterface
    public void share(String title, String text, String url) {
        activity.runOnUiThread(() -> {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, title);
            String shareText = text;
            if (url != null && !url.isEmpty()) {
                shareText += "\n" + url;
            }
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
            activity.startActivity(Intent.createChooser(shareIntent, "分享到"));
        });
    }

    @JavascriptInterface
    public void setStatusBarColor(String color) {
        activity.runOnUiThread(() -> {
            try {
                int colorInt = Color.parseColor(color);
                Window window = activity.getWindow();
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                window.setStatusBarColor(colorInt);
                
                WindowInsetsControllerCompat windowInsetsController = 
                        WindowCompat.getInsetsController(window, window.getDecorView());
                double darkness = 1 - (0.299 * Color.red(colorInt) + 0.587 * Color.green(colorInt) + 0.114 * Color.blue(colorInt)) / 255;
                windowInsetsController.setAppearanceLightStatusBars(darkness < 0.5);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @JavascriptInterface
    public String getAppVersion() {
        try {
            PackageInfo pInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            return pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "1.0";
        }
    }

    @JavascriptInterface
    public void exitApp() {
        activity.runOnUiThread(() -> {
            new AlertDialog.Builder(activity)
                    .setTitle("退出应用")
                    .setMessage("确定要退出应用吗？")
                    .setPositiveButton("确定", (dialog, which) -> activity.finish())
                    .setNegativeButton("取消", null)
                    .show();
        });
    }

    @JavascriptInterface
    public void goBack() {
        activity.runOnUiThread(() -> {
            if (webView.canGoBack()) {
                webView.goBack();
            }
        });
    }

    @JavascriptInterface
    public void reload() {
        activity.runOnUiThread(() -> webView.reload());
    }

    @JavascriptInterface
    public void clearCache() {
        activity.runOnUiThread(() -> {
            webView.clearCache(true);
            webView.clearHistory();
            Toast.makeText(activity, "缓存已清除", Toast.LENGTH_SHORT).show();
        });
    }

    @JavascriptInterface
    public void setScreenOrientation(String orientation) {
        activity.runOnUiThread(() -> {
            switch (orientation.toLowerCase()) {
                case "portrait":
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    break;
                case "landscape":
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                    break;
                case "auto":
                default:
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                    break;
            }
        });
    }

    @JavascriptInterface
    public int getBatteryLevel() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = activity.registerReceiver(null, ifilter);
        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            return (int) ((level / (float) scale) * 100);
        }
        return -1;
    }

    @JavascriptInterface
    public boolean isWifiConnected() {
        ConnectivityManager cm = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network network = cm.getActiveNetwork();
                if (network != null) {
                    NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
                    return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                }
            } else {
                NetworkInfo networkInfo = cm.getActiveNetworkInfo();
                return networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_WIFI;
            }
        }
        return false;
    }

    @JavascriptInterface
    public void playSound(String soundName) {
        activity.runOnUiThread(() -> {
            ToneGenerator toneGen = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
            switch (soundName.toLowerCase()) {
                case "beep":
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150);
                    break;
                case "success":
                    toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 150);
                    break;
                case "error":
                    toneGen.startTone(ToneGenerator.TONE_PROP_NACK, 150);
                    break;
                default:
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150);
                    break;
            }
        });
    }

    @JavascriptInterface
    public void makeCall(String phone) {
        activity.runOnUiThread(() -> {
            try {
                Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone));
                activity.startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(activity, "无法拨打电话", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @JavascriptInterface
    public void sendSMS(String phone, String message) {
        activity.runOnUiThread(() -> {
            try {
                Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + phone));
                intent.putExtra("sms_body", message);
                activity.startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(activity, "无法发送短信", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @JavascriptInterface
    public void setLocalStorage(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }

    @JavascriptInterface
    public String getLocalStorage(String key) {
        return prefs.getString(key, null);
    }

    @JavascriptInterface
    public void removeLocalStorage(String key) {
        prefs.edit().remove(key).apply();
    }

    @JavascriptInterface
    public void clearLocalStorage() {
        prefs.edit().clear().apply();
    }

    @JavascriptInterface
    public void showLoading(String message) {
        activity.runOnUiThread(() -> {
            if (loadingDialog != null && loadingDialog.isShowing()) {
                loadingDialog.dismiss();
            }
            loadingDialog = new ProgressDialog(activity);
            loadingDialog.setMessage(message != null && !message.isEmpty() ? message : "加载中...");
            loadingDialog.setCancelable(false);
            loadingDialog.show();
        });
    }

    @JavascriptInterface
    public void hideLoading() {
        activity.runOnUiThread(() -> {
            if (loadingDialog != null && loadingDialog.isShowing()) {
                loadingDialog.dismiss();
                loadingDialog = null;
            }
        });
    }

    @JavascriptInterface
    public void showAlert(String title, String message) {
        activity.runOnUiThread(() -> {
            new AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("确定", (dialog, which) -> {
                        if (activity instanceof MainActivity) {
                            ((MainActivity) activity).executeJsCallback("_alertCallback", "true");
                        }
                    })
                    .show();
        });
    }

    @JavascriptInterface
    public void showConfirm(String title, String message) {
        activity.runOnUiThread(() -> {
            new AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("确定", (dialog, which) -> {
                        if (activity instanceof MainActivity) {
                            ((MainActivity) activity).executeJsCallback("_confirmCallback", "true");
                        }
                    })
                    .setNegativeButton("取消", (dialog, which) -> {
                        if (activity instanceof MainActivity) {
                            ((MainActivity) activity).executeJsCallback("_confirmCallback", "false");
                        }
                    })
                    .show();
        });
    }

    @JavascriptInterface
    public void enableBackButton(boolean enabled) {
        this.backButtonEnabled = enabled;
    }

    @JavascriptInterface
    public boolean isBackButtonEnabled() {
        return backButtonEnabled;
    }

    @JavascriptInterface
    public void setTitle(String title) {
        // Note: This app uses NoActionBar theme, so this method
        // is a no-op. Document title can be handled in WebView JS.
        activity.runOnUiThread(() -> {
            if (activity instanceof AppCompatActivity) {
                AppCompatActivity appCompatActivity = (AppCompatActivity) activity;
                if (appCompatActivity.getSupportActionBar() != null) {
                    appCompatActivity.getSupportActionBar().setTitle(title);
                }
            }
        });
    }

    @JavascriptInterface
    public void scanQRCode() {
        activity.runOnUiThread(() -> {
            if (activity instanceof MainActivity) {
                ((MainActivity) activity).startQRCodeScanner();
            }
        });
    }

    @JavascriptInterface
    public void getCurrentLocation() {
        activity.runOnUiThread(() -> {
            if (activity instanceof MainActivity) {
                ((MainActivity) activity).requestLocation();
            }
        });
    }

    @JavascriptInterface
    public void takeScreenshot() {
        Toast.makeText(activity, "截图功能暂不支持", Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void saveToGallery(String base64) {
        Toast.makeText(activity, "保存图片功能暂不支持", Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void getContacts() {
        Toast.makeText(activity, "获取联系人需要申请权限", Toast.LENGTH_SHORT).show();
    }
}
