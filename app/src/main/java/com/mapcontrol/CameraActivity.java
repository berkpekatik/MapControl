package com.mapcontrol;

import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.util.Size;
import android.content.Intent;
import android.view.Surface;
import android.view.TextureView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.mapcontrol.bean.CameraBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class CameraActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener {
    private static final String TAG = CameraActivity.class.getSimpleName();
    private static final String ACTION_LOG = "com.mapcontrol.LOG_MESSAGE";
    private static final String EXTRA_LOG_MESSAGE = "log_message";
    
    // View references (findViewById ile alınacak)
    private TextureView tvSurfaceCamera1;
    private TextureView tvSurfaceCamera2;
    private TextureView tvSurfaceCamera3;
    private TextureView tvSurfaceCamera4;
    private TextView cameraText1;
    private TextView cameraText2;
    private TextView cameraText3;
    private TextView cameraText4;
    private LinearLayout llItem3;
    
    private CameraManager cameraManager;
    private Handler handler;
    private HandlerThread handlerThread;
    private int mNumCameraDevices;
    private Size outputSize;
    private final List<String> mCameraDevices = new ArrayList();
    private final List<CameraBean> mCameraBeans = new ArrayList();
    private boolean isTextureAvailable0 = false;
    private boolean isTextureAvailable1 = false;
    private boolean isTextureAvailable2 = false;
    private boolean isTextureAvailable3 = false;
    private boolean isCameraAvailable0 = false;
    private boolean isCameraAvailable1 = false;
    private boolean isCameraAvailable2 = false;
    private boolean isCameraAvailable3 = false;
    CameraManager.AvailabilityCallback availabilityCallback = new AvailabilityCallback();

    class AvailabilityCallback extends CameraManager.AvailabilityCallback {
        AvailabilityCallback() {
        }

        @Override
        public void onCameraAvailable(@NonNull String str) {
            super.onCameraAvailable(str);
            CameraActivity.this.log("[INFO] Kamera kullanılabilir: " + str);
            try {
                if (str.equals("0")) {
                    CameraActivity.this.isCameraAvailable0 = true;
                    CameraActivity.this.initCamera("DMS");
                } else if (str.equals("1")) {
                    CameraActivity.this.isCameraAvailable1 = true;
                    CameraActivity.this.initCamera("OMS");
                } else if (str.equals("2")) {
                    CameraActivity.this.isCameraAvailable2 = true;
                    CameraActivity.this.initCamera("DVR");
                } else if (str.equals("3")) {
                    CameraActivity.this.isCameraAvailable3 = true;
                    CameraActivity.this.initCamera("AVM4");
                }
            } catch (CameraAccessException e) {
                CameraActivity.this.log("[ERROR] Kamera başlatma hatası: " + str + " - " + e.getMessage());
            }
        }

        @Override
        public void onCameraUnavailable(@NonNull String str) {
            super.onCameraUnavailable(str);
        }
    }

    class CameraStateCallback extends CameraDevice.StateCallback {
        final String cameraId;

        CameraStateCallback(String str) {
            this.cameraId = str;
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            CameraActivity.this.log("[WARN] Kamera bağlantısı kesildi: " + cameraDevice.getId());
            if (cameraDevice.getId().equals("0")) {
                CameraActivity.this.isCameraAvailable0 = false;
            } else if (cameraDevice.getId().equals("1")) {
                CameraActivity.this.isCameraAvailable1 = false;
            } else if (cameraDevice.getId().equals("2")) {
                CameraActivity.this.isCameraAvailable2 = false;
            } else if (cameraDevice.getId().equals("3")) {
                CameraActivity.this.isCameraAvailable3 = false;
            }
            CameraActivity.this.closeCamera(cameraDevice);
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i2) {
            CameraActivity.this.log("[ERROR] Kamera hatası: " + cameraDevice.getId() + " - Hata kodu: " + i2);
            CameraActivity.this.closeCamera(cameraDevice);
        }

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            CameraActivity.this.log("[INFO] Kamera açıldı: " + cameraDevice.getId());
            try {
                CameraBean cameraBean = new CameraBean(this.cameraId, cameraDevice, new CameraBean.LogListener() {
                    @Override
                    public void onLog(String message) {
                        CameraActivity.this.log(message);
                    }
                });
                CameraActivity.this.mCameraBeans.add(cameraBean);
                CameraActivity.this.createCameraPreview(cameraBean);
            } catch (CameraAccessException e) {
                CameraActivity.this.log("[ERROR] Kamera önizleme oluşturma hatası: " + e.getMessage());
            }
        }
    }

    private void closeCamera(CameraDevice cameraDevice) {
        if (cameraDevice != null) {
            cameraDevice.close();
            log("[INFO] Kamera kapatıldı: " + cameraDevice.getId());
        }
    }

    private void createCameraPreview(CameraBean cameraBean) throws CameraAccessException {
        TextureView textureView;
        String cameraName = cameraBean.getCameraName();
        cameraName.hashCode();
        switch (cameraName) {
            case "DMS":
            case "AVM1":
                textureView = this.tvSurfaceCamera1;
                break;
            case "DVR":
            case "AVM3":
                textureView = this.tvSurfaceCamera3;
                break;
            case "OMS":
            case "AVM2":
                textureView = this.tvSurfaceCamera2;
                break;
            case "AVM4":
                textureView = this.tvSurfaceCamera4;
                break;
            default:
                return;
        }
        CameraDevice cameraDevice = cameraBean.getCameraDevice();
        SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(this.outputSize.getWidth(), this.outputSize.getHeight());
        Surface surface = new Surface(surfaceTexture);
        try {
            cameraBean.setCaptureRequestBuilder(cameraDevice.createCaptureRequest(1));
        } catch (CameraAccessException e2) {
            e2.printStackTrace();
        }
        cameraBean.getCaptureRequestBuilder().addTarget(surface);
        try {
            cameraDevice.createCaptureSession(new SessionConfiguration(0, Collections.singletonList(new OutputConfiguration(surface)), new ThreadPoolExecutor(2, 4, 2L, TimeUnit.SECONDS, new LinkedBlockingQueue(), new ThreadFactory() {
                @Override
                public final Thread newThread(Runnable runnable) {
                    return CameraActivity.lambda$createCameraPreview$1(runnable);
                }
            }), cameraBean.getCaptureStateCallback()));
        } catch (CameraAccessException e3) {
            e3.printStackTrace();
        }
    }

    private void initCamera(String str) throws CameraAccessException {
        String str2;
        str.hashCode();
        switch (str) {
            case "DMS":
            case "AVM1":
                setTitleName(this.cameraText1, str);
                str2 = "0";
                break;
            case "DVR":
            case "AVM3":
                setTitleName(this.cameraText3, str);
                str2 = "2";
                break;
            case "OMS":
            case "AVM2":
                setTitleName(this.cameraText2, str);
                str2 = "1";
                break;
            case "AVM4":
                setTitleName(this.cameraText4, str);
                str2 = "3";
                break;
            default:
                str2 = "";
                break;
        }
        log("[DEBUG] Kamera başlatılıyor: cameraId = " + str2);
        if (ContextCompat.checkSelfPermission(this, "android.permission.CAMERA") != 0 || ContextCompat.checkSelfPermission(this, "android.permission.WRITE_EXTERNAL_STORAGE") != 0 || ContextCompat.checkSelfPermission(this, "android.permission.RECORD_AUDIO") != 0) {
            requestPermissions(new String[]{"android.permission.CAMERA", "android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.RECORD_AUDIO"}, 2);
            return;
        }
        try {
            this.outputSize = ((StreamConfigurationMap) this.cameraManager.getCameraCharacteristics(str2).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)).getOutputSizes(SurfaceTexture.class)[0];
            log("[DEBUG] Kamera çıktı boyutu: " + this.outputSize.toString());
            if ((str2.equals("0") && this.isTextureAvailable0 && this.isCameraAvailable0) || ((str2.equals("1") && this.isTextureAvailable1 && this.isCameraAvailable1) || ((str2.equals("2") && this.isTextureAvailable2 && this.isCameraAvailable2) || (str2.equals("3") && this.isTextureAvailable3 && this.isCameraAvailable3)))) {
                this.cameraManager.openCamera(str2, new CameraStateCallback(str), this.handler);
            }
        } catch (Exception e2) {
            e2.printStackTrace();
        }
    }

    private void initCameraManager() throws CameraAccessException {
        if (this.cameraManager == null) {
            this.cameraManager = (CameraManager) getApplicationContext().getSystemService("camera");
        }
        updateCameraDevices();
        this.cameraManager.registerAvailabilityCallback(this.availabilityCallback, this.handler);
    }

    private void initHandler() {
        log("[DEBUG] Handler başlatılıyor");
        HandlerThread handlerThread = new HandlerThread(TAG);
        this.handlerThread = handlerThread;
        handlerThread.start();
        this.handler = new Handler(this.handlerThread.getLooper());
    }

    private static void lambda$createCameraPreview$0(Runnable runnable) throws SecurityException, IllegalArgumentException {
        Process.setThreadPriority(10);
        runnable.run();
    }

    private static Thread lambda$createCameraPreview$1(final Runnable runnable) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public final void run() throws SecurityException, IllegalArgumentException {
                CameraActivity.lambda$createCameraPreview$0(runnable);
            }
        });
        if (thread.isDaemon()) {
            thread.setDaemon(false);
        }
        return thread;
    }

    private void setTitleName(TextView textView, String str) {
        runOnUiThread(() -> {
            textView.setText(str);
        });
    }

    private boolean updateCameraDevices() throws CameraAccessException {
        try {
            String[] cameraIdList = this.cameraManager.getCameraIdList();
            HashSet hashSet = new HashSet(Arrays.asList(cameraIdList));
            for (int i2 = 0; i2 < this.mCameraDevices.size(); i2++) {
                if (!hashSet.contains(this.mCameraDevices.get(i2))) {
                    this.mCameraDevices.set(i2, null);
                    this.mNumCameraDevices--;
                }
            }
            hashSet.removeAll(this.mCameraDevices);
            for (String str : cameraIdList) {
                if (hashSet.contains(str)) {
                    this.mCameraDevices.add(str);
                    this.mNumCameraDevices++;
                }
            }
            log("[INFO] Kamera cihazları güncellendi: " + this.mNumCameraDevices + " kamera - " + Arrays.toString(this.mCameraDevices.toArray()));
            return true;
        } catch (CameraAccessException e2) {
            log("[ERROR] Kamera cihaz listesi alınamadı: " + e2.getMessage());
            return false;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_camera);
        setTitle("Kamera Test");
        
        // findViewById ile view'ları al
        tvSurfaceCamera1 = findViewById(R.id.tv_surface_camera1);
        tvSurfaceCamera2 = findViewById(R.id.tv_surface_camera2);
        tvSurfaceCamera3 = findViewById(R.id.tv_surface_camera3);
        tvSurfaceCamera4 = findViewById(R.id.tv_surface_camera4);
        cameraText1 = findViewById(R.id.camera_text1);
        cameraText2 = findViewById(R.id.camera_text2);
        cameraText3 = findViewById(R.id.camera_text3);
        cameraText4 = findViewById(R.id.camera_text4);
        llItem3 = findViewById(R.id.ll_item3);
        
        // Geri dön butonu
        android.widget.Button btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                finish(); // Activity'yi kapat ve MainActivity'ye dön
            });
        }
        
        initHandler();
        try {
            initCameraManager();
        } catch (CameraAccessException e) {
            log("[ERROR] Kamera yöneticisi başlatma hatası: " + e.getMessage());
        }
        tvSurfaceCamera1.setSurfaceTextureListener(this);
        tvSurfaceCamera2.setSurfaceTextureListener(this);
        tvSurfaceCamera3.setSurfaceTextureListener(this);
        // 4. kamerayı aktif et
        tvSurfaceCamera4.setSurfaceTextureListener(this);
        // llItem3'ü gizle (5. ve 6. kamera için)
        llItem3.setVisibility(android.view.View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraManager != null) {
            cameraManager.unregisterAvailabilityCallback(this.availabilityCallback);
        }
        for (int i2 = 0; i2 < this.mCameraBeans.size(); i2++) {
            closeCamera(this.mCameraBeans.get(i2).getCameraDevice());
            this.mCameraBeans.get(i2).setCameraDevice(null);
        }
        if (this.handlerThread != null) {
            this.handlerThread.quit();
        }
    }

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int i2, int i3) {
        try {
            if (surfaceTexture == this.tvSurfaceCamera1.getSurfaceTexture()) {
                this.isTextureAvailable0 = true;
                initCamera("DMS");
            } else if (surfaceTexture == this.tvSurfaceCamera2.getSurfaceTexture()) {
                this.isTextureAvailable1 = true;
                initCamera("OMS");
            } else if (surfaceTexture == this.tvSurfaceCamera3.getSurfaceTexture()) {
                this.isTextureAvailable2 = true;
                initCamera("DVR");
            } else if (surfaceTexture == this.tvSurfaceCamera4.getSurfaceTexture()) {
                // 4. kamerayı aktif et
                this.isTextureAvailable3 = true;
                initCamera("AVM4");
            }
        } catch (CameraAccessException e) {
            log("[ERROR] onSurfaceTextureAvailable'da kamera başlatma hatası: " + e.getMessage());
        }
    }
    
    /**
     * MapControl log sistemine mesaj gönder (MainActivity'deki log görünümüne)
     */
    private void log(String msg) {
        Intent intent = new Intent(ACTION_LOG);
        intent.putExtra(EXTRA_LOG_MESSAGE, msg);
        sendBroadcast(intent);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
        if (surfaceTexture == this.tvSurfaceCamera1.getSurfaceTexture()) {
            this.isTextureAvailable0 = false;
        } else if (surfaceTexture == this.tvSurfaceCamera2.getSurfaceTexture()) {
            this.isTextureAvailable1 = false;
        } else if (surfaceTexture == this.tvSurfaceCamera3.getSurfaceTexture()) {
            this.isTextureAvailable2 = false;
        } else if (surfaceTexture == this.tvSurfaceCamera4.getSurfaceTexture()) {
            // 4. kamera için
            this.isTextureAvailable3 = false;
        }
        return false;
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int i2, int i3) {
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {
    }
}

