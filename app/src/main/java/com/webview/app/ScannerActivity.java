package com.webview.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import java.util.List;

public class ScannerActivity extends AppCompatActivity {

    public static final String EXTRA_SCAN_RESULT = "scan_result";
    public static final int RESULT_CANCELLED = 0;
    public static final int RESULT_SUCCESS = 1;
    public static final int RESULT_ERROR = 2;

    private static final int CAMERA_PERMISSION_REQUEST = 100;

    private DecoratedBarcodeView barcodeView;
    private boolean isScanning = true;

    private final BarcodeCallback callback = new BarcodeCallback() {
        @Override
        public void barcodeResult(BarcodeResult result) {
            if (result != null && result.getText() != null && isScanning) {
                isScanning = false;
                barcodeView.pause();
                
                Intent resultIntent = new Intent();
                resultIntent.putExtra(EXTRA_SCAN_RESULT, result.getText());
                setResult(RESULT_SUCCESS, resultIntent);
                finish();
            }
        }

        @Override
        public void possibleResultPoints(List<ResultPoint> resultPoints) {
            // Not used
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner);

        barcodeView = findViewById(R.id.barcode_scanner);
        barcodeView.setStatusText("将二维码放入框内扫描");

        if (hasCameraPermission()) {
            startScanning();
        } else {
            requestCameraPermission();
        }
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                CAMERA_PERMISSION_REQUEST);
    }

    private void startScanning() {
        barcodeView.decodeContinuous(callback);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanning();
            } else {
                Toast.makeText(this, "需要相机权限才能扫描二维码", Toast.LENGTH_SHORT).show();
                Intent resultIntent = new Intent();
                resultIntent.putExtra(EXTRA_SCAN_RESULT, "PERMISSION_DENIED");
                setResult(RESULT_ERROR, resultIntent);
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasCameraPermission()) {
            barcodeView.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        barcodeView.pause();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_SCAN_RESULT, "CANCELLED");
        setResult(RESULT_CANCELLED, resultIntent);
        finish();
    }
}
