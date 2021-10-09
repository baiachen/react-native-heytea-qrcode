package com.heyteago.qrcode;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.google.zxing.Result;
import com.heyteago.qrcode.activity.CaptureActivity;

public class RNHeyteaQRCodeModule extends ReactContextBaseJavaModule {

    private final ReactApplicationContext reactContext;
    private static Promise sPromise;

    public RNHeyteaQRCodeModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return "HeyTeaQRCode";
    }

    public static void setQRCodeResult(@Nullable Result result) {
        if (sPromise != null) {
            if (result == null) {
                sPromise.resolve(null);
                sPromise = null;
                return;
            }
            WritableMap map = Arguments.createMap();
            map.putString("result", result.getText());
            String scanType = "";
            switch (result.getBarcodeFormat()) {
                case AZTEC:
                    scanType = "AZTEC";
                    break;
                case CODABAR:
                    scanType = "CODABAR";
                    break;
                case CODE_39:
                    scanType = "CODE_39";
                    break;
                case CODE_93:
                    scanType = "CODE_93";
                    break;
                case CODE_128:
                    scanType = "CODE_128";
                    break;
                case DATA_MATRIX:
                    scanType = "DATA_MATRIX";
                    break;
                case EAN_8:
                    scanType = "EAN_8";
                    break;
                case EAN_13:
                    scanType = "EAN_13";
                    break;
                case ITF:
                    scanType = "ITF";
                    break;
                case MAXICODE:
                    scanType = "MAXICODE";
                    break;
                case PDF_417:
                    scanType = "PDF_417";
                    break;
                case QR_CODE:
                    scanType = "QR_CODE";
                    break;
                case RSS_14:
                    scanType = "RSS_14";
                    break;
                case RSS_EXPANDED:
                    scanType = "RSS_EXPANDED";
                    break;
                case UPC_A:
                    scanType = "UPC_A";
                    break;
                case UPC_E:
                    scanType = "UPC_E";
                    break;
                case UPC_EAN_EXTENSION:
                    scanType = "UPC_EAN_EXTENSION";
                    break;
            }
            map.putString("scanType", scanType);
            sPromise.resolve(map);
            sPromise = null;
        }
    }

    @ReactMethod
    public void scanQRCode(Promise promise) {
        if (getCurrentActivity() != null) {
            if (ContextCompat.checkSelfPermission(getCurrentActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                promise.reject(new Throwable("No camera permission"));
                return;
            }
            sPromise = promise;
            Intent intent = new Intent(getCurrentActivity(), CaptureActivity.class);
            getCurrentActivity().startActivity(intent);
        }
    }
}
