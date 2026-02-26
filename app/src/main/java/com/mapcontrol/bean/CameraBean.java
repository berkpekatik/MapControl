package com.mapcontrol.bean;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.os.Handler;
import android.os.HandlerThread;
import androidx.annotation.NonNull;

public class CameraBean {
    private static final String TAG = "CameraBean";
    private CameraCaptureSession cameraCaptureSession;
    private CameraDevice cameraDevice;
    private String cameraName;
    private CaptureRequest.Builder captureRequestBuilder;
    private LogListener logListener;
    
    public interface LogListener {
        void onLog(String message);
    }
    CameraCaptureSession.StateCallback captureStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
            CameraBean.this.log("[WARN] Kamera capture session yapılandırılamadı");
        }

        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
            CameraBean.this.log("[INFO] Kamera capture session yapılandırıldı");
            CameraBean.this.cameraCaptureSession = cameraCaptureSession;
            try {
                CameraBean.this.updatePreview();
            } catch (CameraAccessException e) {
                CameraBean.this.log("[ERROR] Önizleme güncelleme hatası: " + e.getMessage());
            }
        }
    };
    private Handler handler;

    public CameraBean(String str, CameraDevice cameraDevice, LogListener logListener) {
        this.cameraName = "";
        this.cameraName = str;
        this.cameraDevice = cameraDevice;
        this.logListener = logListener;
        HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        this.handler = new Handler(handlerThread.getLooper());
    }

    private void updatePreview() throws CameraAccessException {
        if (this.cameraDevice == null) {
            log("[ERROR] updatePreview hatası: cameraDevice null");
            return;
        }
        this.captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, 1);
        try {
            this.cameraCaptureSession.setRepeatingRequest(this.captureRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession cameraCaptureSession, @NonNull CaptureRequest captureRequest, @NonNull TotalCaptureResult totalCaptureResult) {
                    super.onCaptureCompleted(cameraCaptureSession, captureRequest, totalCaptureResult);
                }

                @Override
                public void onCaptureFailed(@NonNull CameraCaptureSession cameraCaptureSession, @NonNull CaptureRequest captureRequest, @NonNull CaptureFailure captureFailure) {
                    super.onCaptureFailed(cameraCaptureSession, captureRequest, captureFailure);
                }

                @Override
                public void onCaptureProgressed(@NonNull CameraCaptureSession cameraCaptureSession, @NonNull CaptureRequest captureRequest, @NonNull CaptureResult captureResult) {
                    super.onCaptureProgressed(cameraCaptureSession, captureRequest, captureResult);
                    // Capture progress log'u çok sık olduğu için sadece debug modda logluyoruz
                    // CameraBean.this.log("[DEBUG] Capture progress");
                }

                @Override
                public void onCaptureStarted(@NonNull CameraCaptureSession cameraCaptureSession, @NonNull CaptureRequest captureRequest, long j2, long j3) {
                    super.onCaptureStarted(cameraCaptureSession, captureRequest, j2, j3);
                }
            }, this.handler);
        } catch (CameraAccessException e2) {
            e2.printStackTrace();
        }
    }

    public CameraDevice getCameraDevice() {
        return this.cameraDevice;
    }

    public String getCameraName() {
        return this.cameraName;
    }

    public CaptureRequest.Builder getCaptureRequestBuilder() {
        return this.captureRequestBuilder;
    }

    public CameraCaptureSession.StateCallback getCaptureStateCallback() {
        return this.captureStateCallback;
    }

    public void setCameraDevice(CameraDevice cameraDevice) {
        this.cameraDevice = cameraDevice;
    }

    public void setCaptureRequestBuilder(CaptureRequest.Builder builder) {
        this.captureRequestBuilder = builder;
    }
    
    private void log(String message) {
        if (logListener != null) {
            logListener.onLog(message);
        }
    }
}

