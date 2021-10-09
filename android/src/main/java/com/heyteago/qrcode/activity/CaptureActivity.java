package com.heyteago.qrcode.activity;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.appcompat.widget.Toolbar;


import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.heyteago.qrcode.Constants;
import com.heyteago.qrcode.R;
import com.heyteago.qrcode.RNHeyteaQRCodeModule;
import com.heyteago.qrcode.Utils;
import com.heyteago.qrcode.camera.CameraManager;
import com.heyteago.qrcode.decoding.CaptureActivityHandler;
import com.heyteago.qrcode.decoding.InactivityTimer;
import com.google.zxing.RGBLuminanceSource;
import com.heyteago.qrcode.view.ViewfinderView;

import java.io.IOException;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;


import pub.devrel.easypermissions.EasyPermissions;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import android.util.Base64;

/**
 * Initial the camera
 **/
public class CaptureActivity extends AppCompatActivity implements Callback, View.OnClickListener, EasyPermissions.PermissionCallbacks {

    private static final int REQUEST_CODE_SCAN_GALLERY = 100;

    private CaptureActivityHandler handler;
    private ViewfinderView viewfinderView;
    private ImageButton back;
    private boolean hasSurface;
    private Vector<BarcodeFormat> decodeFormats;
    private String characterSet;
    private InactivityTimer inactivityTimer;
    private MediaPlayer mediaPlayer;
    private boolean playBeep;
    private static final float BEEP_VOLUME = 0.10f;
    private boolean vibrate;
    private ProgressDialog mProgress;
    private String photo_path;
    private Bitmap scanBitmap;
    private LinearLayoutCompat flashLightLayout;   // 打开闪光灯
    private LinearLayoutCompat albumLayout; // 打开相册
    private AppCompatImageView flashLightIv; // 闪光灯图片
    private TextView flashLightTv;// 闪光灯文字

    private boolean isFlashOn = false;


    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_scanner);
        CameraManager.init(getApplication());
        viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_content);
        flashLightLayout = (LinearLayoutCompat) findViewById(R.id.flashLightLayout);
        albumLayout = (LinearLayoutCompat) findViewById(R.id.albumLayout);
        back = (ImageButton) findViewById(R.id.btn_back);
        flashLightIv = findViewById(R.id.flashLightIv);
        flashLightTv = findViewById(R.id.flashLightTv);
        flashLightLayout.setOnClickListener(this);
        albumLayout.setOnClickListener(this);
        back.setOnClickListener(this);
        hasSurface = false;
        inactivityTimer = new InactivityTimer(this);

        //添加toolbar
        addToolbar();
    }

    private void addToolbar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }


    /**
     * 手动选择照片
     *
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(final int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_CODE_SCAN_GALLERY:
//                    Log.i("saoma", data.getData()+"");
                    //获取选中图片的路径
                    photo_path = Utils.getRealPathFromUri(this, data.getData());
                    if(photo_path == null){
                        photo_path = _getRealPathFromUri(getApplication(), data.getData());
                    }
                    mProgress = new ProgressDialog(CaptureActivity.this);
                    mProgress.setMessage("正在扫描...");
                    mProgress.setCancelable(false);
                    mProgress.show();
//                    Log.i("saomaphoto_path", photo_path);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            Result result = scanningImage(photo_path);
                            if (result != null) {
                                Intent resultIntent = new Intent();
                                Bundle bundle = new Bundle();
                                bundle.putString(Constants.INTENT_EXTRA_KEY_QR_SCAN, result.getText());
                                resultIntent.putExtras(bundle);
                                CaptureActivity.this.setResult(RESULT_OK, resultIntent);
                                mProgress.dismiss();
                                RNHeyteaQRCodeModule.setQRCodeResult(result);
                                finish();
                            } else {
                                mProgress.dismiss();
                                if (handler != null) {
                                    Message m = handler.obtainMessage();
                                    m.what = R.id.decode_failed;
                                    m.obj = "Scan failed!";
                                    handler.sendMessage(m);
                                }
                            }
                        }
                    }).start();
                    break;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }


    @Override
    public void onBackPressed() {
        super.onBackPressed();
        RNHeyteaQRCodeModule.setQRCodeResult(null);
    }

    public static String _getRealPathFromUri(Context context, Uri contentUri) {
        Cursor cursor = null;
        try {
            String[] proj = { MediaStore.Images.Media.DATA };
            cursor = context.getContentResolver().query(contentUri, proj, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    // /**
    //  * 扫描二维码图片的方法
    //  *
    //  * @param path
    //  * @return
    //  */
    // public Result scanningImage(String path) {
    //     if (TextUtils.isEmpty(path)) {
    //         return null;
    //     }
    //     String reg = ".+(.jpg|.bmp|.jpeg|.png|.gif|.JPG|.BMP|.JPEG|.PNG|.GIF)$";
    //     Pattern pattern = Pattern.compile(reg);
    //     Matcher matcher = pattern.matcher(path);
    //     if (!matcher.find()) {
    //         return null;
    //     }
    //     if (!new File(path).exists()) {
    //         return null;
    //     }
    //     Hashtable<DecodeHintType, String> hints = new Hashtable<>();
    //     hints.put(DecodeHintType.CHARACTER_SET, "UTF8"); //设置二维码内容的编码

    //     BitmapFactory.Options options = new BitmapFactory.Options();
    //     options.inJustDecodeBounds = true; // 先获取原大小
    //     scanBitmap = BitmapFactory.decodeFile(path, options);

    //     options.inJustDecodeBounds = false; // 获取新的大小
    //     int sampleSize = (int) (options.outHeight / (float) 200);
    //     if (sampleSize <= 0)
    //         sampleSize = 1;
    //     options.inSampleSize = sampleSize;
    //     scanBitmap = BitmapFactory.decodeFile(path, options);

    //     try {
    //         RGBLuminanceSource source = new RGBLuminanceSource(scanBitmap);
    //         BinaryBitmap bitmap1 = new BinaryBitmap(new HybridBinarizer(source));
    //         QRCodeReader reader = new QRCodeReader();
    //         return reader.decode(bitmap1, hints);
    //     } catch (NotFoundException e) {
    //         e.printStackTrace();
    //     } catch (ChecksumException e) {
    //         e.printStackTrace();
    //     } catch (FormatException e) {
    //         e.printStackTrace();
    //     }
    //     return null;
    // }

    /**
     * 扫描二维码图片的方法
     *
     * @param path
     * @return
     */
    public Result scanningImage(String path) {
         if (TextUtils.isEmpty(path)) {
             return null;
         }
//         String reg = ".+(.jpg|.bmp|.jpeg|.png|.gif|.JPG|.BMP|.JPEG|.PNG|.GIF)$";
//         Pattern pattern = Pattern.compile(reg);
//         Matcher matcher = pattern.matcher(path);
//         if (!matcher.find()) {
//             return null;
//         }
//        Log.i("saoma1",path);
//         if (!new File(path).exists()) {
//             return null;
//         }

        // Hashtable<DecodeHintType, String> hints = new Hashtable<DecodeHintType, String>();
        // hints.put(DecodeHintType.CHARACTER_SET, "utf-8"); // 设置二维码内容的编码
        // BitmapFactory.Options options = new BitmapFactory.Options();
        // options.inJustDecodeBounds = true; // 先获取原大小
        // options.inJustDecodeBounds = false; // 获取新的大小

        // int sampleSize = (int) (options.outHeight / (float) 200);

        // if (sampleSize <= 0)
        //     sampleSize = 1;
        // options.inSampleSize = sampleSize;
        // Bitmap scanBitmap = null;
        // if (path.startsWith("http://")||path.startsWith("https://")) {
        //     scanBitmap = this.getbitmap(path);
        // } else {
        //     scanBitmap = BitmapFactory.decodeFile(path, options);
        // }
        // if (scanBitmap == null) {
        //     return null;
        // }
        // int[] intArray = new int[scanBitmap.getWidth()*scanBitmap.getHeight()];
        // scanBitmap.getPixels(intArray, 0, scanBitmap.getWidth(), 0, 0, scanBitmap.getWidth(), scanBitmap.getHeight());

        // RGBLuminanceSource source = new RGBLuminanceSource(scanBitmap.getWidth(), scanBitmap.getHeight(), intArray);
        // BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        // QRCodeReader reader = new QRCodeReader();
        // try {
        //     Result result = reader.decode(bitmap, hints);
        //     if (result == null) {
        //         return null;
        //     } else {
        //         return result;
        //     }

        // } catch (Exception e) {
        //     return null;
        // }

//        String fileUrl = path.replace("file://","");
        Result result = scannLumin(path);
        if(result != null){
            return result;
        }
        result = _scanningImage(path);
        if(result != null){
            return result;
        }
        result = decodeBarcodeRGB(path);
        if(result != null){
            return result;
        }
        result = decodeBarcodeYUV(path);
        if(result != null){
            return result;
        }
        return null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.scanner_view);
        SurfaceHolder surfaceHolder = surfaceView.getHolder();
        if (hasSurface) {
            initCamera(surfaceHolder);
        } else {
            surfaceHolder.addCallback(this);
            surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }
        decodeFormats = null;
        characterSet = null;

        playBeep = true;
        AudioManager audioService = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioService.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) {
            playBeep = false;
        }
        initBeepSound();
        vibrate = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        inactivityTimer.shutdown();
        if (handler != null) {
            handler.quitSynchronously();
            handler = null;
        }
        CameraManager.get().closeDriver();
        super.onDestroy();
    }

    /**
     * Handler scan result
     * 扫码后得到的结果
     *
     * @param result
     * @param barcode
     */
    public void handleDecode(Result result, Bitmap barcode) {
        inactivityTimer.onActivity();
        playBeepSoundAndVibrate();
        String resultString = result.getText();
        if (TextUtils.isEmpty(resultString)) {
            Toast.makeText(CaptureActivity.this, "Scan failed!", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent resultIntent = new Intent();
        Bundle bundle = new Bundle();
        bundle.putString(Constants.INTENT_EXTRA_KEY_QR_SCAN, resultString);
        // 不能使用Intent传递大于40kb的bitmap，可以使用一个单例对象存储这个bitmap
//            bundle.putParcelable("bitmap", barcode);
//            Logger.d("saomiao",resultString);
        resultIntent.putExtras(bundle);
        this.setResult(RESULT_OK, resultIntent);
        RNHeyteaQRCodeModule.setQRCodeResult(result);
        CaptureActivity.this.finish();
    }

    private void initCamera(SurfaceHolder surfaceHolder) {
        try {
            CameraManager.get().openDriver(surfaceHolder);
        } catch (IOException ioe) {
            return;
        } catch (RuntimeException e) {
            return;
        }
        if (handler == null) {
            handler = new CaptureActivityHandler(this, decodeFormats,
                    characterSet);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {

    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (!hasSurface) {
            hasSurface = true;
            initCamera(holder);
        }

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        hasSurface = false;

    }

    public ViewfinderView getViewfinderView() {
        return viewfinderView;
    }

    public Handler getHandler() {
        return handler;
    }

    public void drawViewfinder() {
        viewfinderView.drawViewfinder();

    }

    private void initBeepSound() {
        if (playBeep && mediaPlayer == null) {
            // The volume on STREAM_SYSTEM is not adjustable, and users found it
            // too loud,
            // so we now play on the music stream.
            setVolumeControlStream(AudioManager.STREAM_MUSIC);
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.setOnCompletionListener(beepListener);

            AssetFileDescriptor file = getResources().openRawResourceFd(
                    R.raw.beep);
            try {
                mediaPlayer.setDataSource(file.getFileDescriptor(),
                        file.getStartOffset(), file.getLength());
                file.close();
                mediaPlayer.setVolume(BEEP_VOLUME, BEEP_VOLUME);
                mediaPlayer.prepare();
            } catch (IOException e) {
                mediaPlayer = null;
            }
        }
    }

    private static final long VIBRATE_DURATION = 200L;

    private void playBeepSoundAndVibrate() {
        if (playBeep && mediaPlayer != null) {
            mediaPlayer.start();
        }
        if (vibrate) {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            vibrator.vibrate(VIBRATE_DURATION);
        }
    }

    /**
     * When the beep has finished playing, rewind to queue up another one.
     */
    private final OnCompletionListener beepListener = new OnCompletionListener() {
        public void onCompletion(MediaPlayer mediaPlayer) {
            mediaPlayer.seekTo(0);
        }
    };

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_back) {
            finish();
        } else if (id == R.id.flashLightLayout) {
            try {
                boolean isSuccess = CameraManager.get().setFlashLight(!isFlashOn);
                if (!isSuccess) {
                    Toast.makeText(CaptureActivity.this, "暂时无法开启闪光灯", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (isFlashOn) {
                    // 关闭闪光灯
                    isFlashOn = false;

                } else {
                    // 开启闪光灯
                    isFlashOn = true;
                }
                switchFlashImg(isFlashOn);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (id == R.id.albumLayout) {
            this.selectPhoto();
        }
    }

    /**
     * @param flashState 切换闪光灯图片
     */
    public void switchFlashImg(Boolean flashState) {

        if (flashState) {
            flashLightIv.setImageResource(R.drawable.ic_open);
            flashLightTv.setText("关闭闪光灯");
        } else {
            flashLightIv.setImageResource(R.drawable.ic_close);
            flashLightTv.setText("打开闪光灯");
        }

    }


    /**
     * 相册选择图片
     */
    private void selectPhoto() {
        String[] perms = {Manifest.permission.READ_EXTERNAL_STORAGE,};
        if (EasyPermissions.hasPermissions(this, perms)) {
            navigatePhoto();
        } else {
            EasyPermissions.requestPermissions(this, "Select QRCode pic need storage permission", 0x666, perms);
        }
    }

    private void navigatePhoto() {
        Intent innerIntent = new Intent(Intent.ACTION_GET_CONTENT); // "android.intent.action.GET_CONTENT"
        innerIntent.setType("image/*");
        Intent wrapperIntent = Intent.createChooser(innerIntent, "Select QRCode pic");
        startActivityForResult(wrapperIntent, REQUEST_CODE_SCAN_GALLERY);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    public void onPermissionsGranted(int requestCode, @NonNull List<String> perms) {
        if (requestCode == 0x666) {
            navigatePhoto();
        }
    }

    @Override
    public void onPermissionsDenied(int requestCode, @NonNull List<String> perms) {

    }

    public static Bitmap getbitmap(String imageUri) {
        Bitmap bitmap = null;
        try {
            URL myFileUrl = new URL(imageUri);
            HttpURLConnection conn = (HttpURLConnection) myFileUrl.openConnection();
            conn.setDoInput(true);
            conn.connect();
            InputStream is = conn.getInputStream();
            bitmap = BitmapFactory.decodeStream(is);
            is.close();
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
            bitmap = null;
        } catch (IOException e) {
            e.printStackTrace();
            bitmap = null;
        }
        return bitmap;
    }

    public Result scannLumin(String path) {

        Result re = null;
        try {
            Bitmap barcode = fromToFileOrBase64(path, null);
            if(barcode == null) {
                return null;
            }
            int width = barcode.getWidth();
            int height = barcode.getHeight();
            int[] data = new int[width * height];
            barcode.getPixels(data, 0, width, 0, 0, width, height);    //得到像素
            RGBLuminanceSource source = new RGBLuminanceSource(width, height, data);   //RGBLuminanceSource对象
            BinaryBitmap bitmap1 = new BinaryBitmap(new HybridBinarizer(source));
            QRCodeReader reader = new QRCodeReader();
            //得到结果
            re = reader.decode(bitmap1);
        } catch (NotFoundException e) {
            e.printStackTrace();
        } catch (ChecksumException e) {
            e.printStackTrace();
        } catch (FormatException e) {
            e.printStackTrace();
        }
        return re;

    }

    public static Bitmap base64ToBitmap(String base64Data) {
        try {
            byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        }catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }

    public static Bitmap fromToFileOrBase64(String fileOrBase64, BitmapFactory.Options options) {
        try {
            Log.i("saomiao",fileOrBase64+"");
            if (fileOrBase64.toLowerCase().indexOf(";base64,") > 0) {
                return base64ToBitmap(fileOrBase64);
            }
            BitmapFactory.Options opts = null;
            if (options == null) {
                opts = new BitmapFactory.Options();
                opts.inSampleSize = 1;
            } else {
                opts = options;
            }

            Bitmap barcode = BitmapFactory.decodeFile(fileOrBase64, opts);
            return barcode;

        }catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

      /**
     * 扫描二维码图片的方法
     * @param path
     * @return
     */
    public Result _scanningImage(String path) {

        try {
            if (path == null || path.length() == 0) {
                return null;
            }
            Hashtable<DecodeHintType, String> hints = new Hashtable<>();
            hints.put(DecodeHintType.CHARACTER_SET, "UTF8"); //设置二维码内容的编码

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true; // 先获取原大小
            Bitmap scanBitmap = fromToFileOrBase64(path,  options);
            options.inJustDecodeBounds = false; // 获取新的大小
            int sampleSize = (int) (options.outHeight / (float) 200);
            if (sampleSize <= 0)
                sampleSize = 1;
            options.inSampleSize = sampleSize;
            scanBitmap = BitmapFactory.decodeFile(path, options);
            if(scanBitmap == null){
                return null;
            }
            int width=scanBitmap.getWidth();
            int height=scanBitmap.getHeight();
            int[] pixels=new int[width*height];
            scanBitmap.getPixels(pixels,0,width,0,0,width,height);//获取图片像素点
            RGBLuminanceSource source = new RGBLuminanceSource(scanBitmap.getWidth(),scanBitmap.getHeight(),pixels);
            BinaryBitmap bitmap1 = new BinaryBitmap(new HybridBinarizer(source));
            QRCodeReader reader = new QRCodeReader();
            return reader.decode(bitmap1, hints);
        } catch (NotFoundException e) {
            e.printStackTrace();
        } catch (ChecksumException e) {
            e.printStackTrace();
        } catch (FormatException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

        /**
     * 解析二维码（使用解析RGB编码数据的方式）
     *
     * @param path
     * @return
     */
    public static Result decodeBarcodeRGB(String path) {
        try {

            Bitmap barcode = fromToFileOrBase64(path, null);
            if(barcode == null){
                return null;
            }
            Result result = decodeBarcodeRGB(barcode);
            barcode.recycle();
            barcode = null;
            return result;
        }catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }

    }

    /**
     * 解析二维码 （使用解析RGB编码数据的方式）
     *
     * @param barcode
     * @return
     */
    public static Result decodeBarcodeRGB(Bitmap barcode) {

        int width = barcode.getWidth();
        int height = barcode.getHeight();
        int[] data = new int[width * height];
        barcode.getPixels(data, 0, width, 0, 0, width, height);
        RGBLuminanceSource source = new RGBLuminanceSource(width, height, data);
        BinaryBitmap bitmap1 = new BinaryBitmap(new HybridBinarizer(source));
        QRCodeReader reader = new QRCodeReader();
        Result result = null;
        try {
            result = reader.decode(bitmap1);
        } catch (NotFoundException e) {
            e.printStackTrace();
        } catch (ChecksumException e) {
            e.printStackTrace();
        } catch (FormatException e) {
            e.printStackTrace();
        }
        barcode.recycle();
        barcode = null;
        return result;
    }

    /**
     * 解析二维码（使用解析YUV编码数据的方式）
     *
     * @param path
     * @return
     */
    public static Result decodeBarcodeYUV(String path) {
        try{
            if (path == null || path.length() == 0) {
                return null;
            }
            Bitmap barcode = fromToFileOrBase64(path, null);
            if (null == barcode) {
                return null;
            }
            Result result = decodeBarcodeYUV(barcode);
            barcode.recycle();
            barcode = null;
            return result;
        }catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    /**
     * 解析二维码（使用解析YUV编码数据的方式）
     *
     * @param barcode
     * @return
     */
    public static Result decodeBarcodeYUV(Bitmap barcode) {
        if (null == barcode) {
            return null;
        }
        int width = barcode.getWidth();
        int height = barcode.getHeight();
        //以argb方式存放图片的像素
        int[] argb = new int[width * height];
        barcode.getPixels(argb, 0, width, 0, 0, width, height);
        //将argb转换为yuv
        byte[] yuv = new byte[width * height * 3 / 2];
        encodeYUV420SP(yuv, argb, width, height);
        //解析YUV编码方式的二维码
        Result result = decodeBarcodeYUV(yuv, width, height);

        barcode.recycle();
        barcode = null;
        return result;
    }

    /**
     * 解析二维码（使用解析YUV编码数据的方式）
     *
     * @param yuv
     * @param width
     * @param height
     * @return
     */
    private static Result decodeBarcodeYUV(byte[] yuv, int width, int height) {
        long start = System.currentTimeMillis();
        MultiFormatReader multiFormatReader = new MultiFormatReader();
        multiFormatReader.setHints(null);

        Result rawResult = null;
        PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(yuv, width, height, 0, 0,
                width, height, false);
        if (source != null) {
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            try {
                rawResult = multiFormatReader.decodeWithState(bitmap);
            } catch (ReaderException re) {
                re.printStackTrace();
            } finally {
                multiFormatReader.reset();
                multiFormatReader = null;
            }
        }
        long end = System.currentTimeMillis();
        return rawResult;
    }


    /**
     * RGB转YUV的公式是:
     * Y=0.299R+0.587G+0.114B;
     * U=-0.147R-0.289G+0.436B;
     * V=0.615R-0.515G-0.1B;
     *
     * @param yuv
     * @param argb
     * @param width
     * @param height
     */
    private static void encodeYUV420SP(byte[] yuv, int[] argb, int width, int height) {
        // 帧图片的像素大小
        final int frameSize = width * height;
        // ---YUV数据---
        int Y, U, V;
        // Y的index从0开始
        int yIndex = 0;
        // UV的index从frameSize开始
        int uvIndex = frameSize;
        // ---颜色数据---
        int R, G, B;
        int rgbIndex = 0;
        // ---循环所有像素点，RGB转YUV---
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                R = (argb[rgbIndex] & 0xff0000) >> 16;
                G = (argb[rgbIndex] & 0xff00) >> 8;
                B = (argb[rgbIndex] & 0xff);
                //
                rgbIndex++;
                // well known RGB to YUV algorithm
                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                U = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128;
                V = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128;
                Y = Math.max(0, Math.min(Y, 255));
                U = Math.max(0, Math.min(U, 255));
                V = Math.max(0, Math.min(V, 255));
                // NV21 has a plane of Y and interleaved planes of VU each sampled by a factor of 2
                // meaning for every 4 Y pixels there are 1 V and 1 U. Note the sampling is every other
                // pixel AND every other scan line.
                // ---Y---
                yuv[yIndex++] = (byte) Y;
                // ---UV---
                if ((j % 2 == 0) && (i % 2 == 0)) {
                    //
                    yuv[uvIndex++] = (byte) V;
                    //
                    yuv[uvIndex++] = (byte) U;
                }
            }
        }
    }
//    private void requestWritePermission(){
//        if(checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED){
//            ActivityCompat.requestPermissions(this,new String[]{ Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
//        }
//    }
}