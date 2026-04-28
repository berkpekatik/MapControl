package com.mapcontrol.ui.activity;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.SharedPreferences;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AudioTestActivity extends AppCompatActivity {
    private TextView tvFilePath;
    private Button btnSelectFile;
    private Button btnPlay;
    private Button btnStop;
    private MediaPlayer mediaPlayer;
    private String selectedFilePath;
    private Handler handler;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Hoşgeldin Ses Test");
        
        handler = new Handler(Looper.getMainLooper());
        prefs = getSharedPreferences("MapControlPrefs", MODE_PRIVATE);
        
        // Ana container
        LinearLayout rootView = new LinearLayout(this);
        rootView.setOrientation(LinearLayout.VERTICAL);
        rootView.setBackgroundColor(ContextCompat.getColor(this, R.color.backgroundPage));
        
        // Geri Dön Butonu Bar (CameraActivity ile aynı tasarım)
        LinearLayout backButtonBar = new LinearLayout(this);
        backButtonBar.setOrientation(LinearLayout.HORIZONTAL);
        backButtonBar.setBackgroundColor(ContextCompat.getColor(this, R.color.surfaceBar));
        backButtonBar.setPadding(16, 16, 16, 16);
        backButtonBar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        
        // Geri dön butonu (CameraActivity ile aynı tasarım)
        Button btnBack = new Button(this);
        btnBack.setText("← Geri Dön");
        btnBack.setTextSize(16);
        btnBack.setTextColor(ContextCompat.getColor(this, R.color.textPrimary));
        android.graphics.drawable.GradientDrawable backBtnBg = new android.graphics.drawable.GradientDrawable();
        backBtnBg.setColor(ContextCompat.getColor(this, R.color.buttonPrimary));
        backBtnBg.setCornerRadius(0); // Köşe yuvarlatma yok
        btnBack.setBackground(backBtnBg);
        btnBack.setPadding(20, 12, 20, 12);
        btnBack.setMinHeight((int)(48 * getResources().getDisplayMetrics().density)); // 48dp
        btnBack.setMinWidth((int)(120 * getResources().getDisplayMetrics().density)); // 120dp
        btnBack.setElevation(4 * getResources().getDisplayMetrics().density); // 4dp elevation
        btnBack.setOnClickListener(v -> finish());
        backButtonBar.addView(btnBack);
        
        rootView.addView(backButtonBar);
        
        // İçerik container (ScrollView)
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(ContextCompat.getColor(this, R.color.backgroundPage));
        scrollView.setFillViewport(true);
        
        LinearLayout mainContainer = new LinearLayout(this);
        mainContainer.setOrientation(LinearLayout.VERTICAL);
        mainContainer.setPadding(24, 24, 24, 24);
        mainContainer.setBackgroundColor(ContextCompat.getColor(this, R.color.backgroundPage));
        
        // Başlık
        TextView titleText = new TextView(this);
        titleText.setText("Hoşgeldin Ses Test");
        titleText.setTextSize(24);
        titleText.setTextColor(ContextCompat.getColor(this, R.color.textPrimary));
        titleText.setTypeface(null, android.graphics.Typeface.BOLD);
        titleText.setPadding(0, 0, 0, 24);
        mainContainer.addView(titleText);
        
        // Dosya seç butonu
        btnSelectFile = new Button(this);
        btnSelectFile.setText("Dosya Seç");
        btnSelectFile.setTextSize(16);
        btnSelectFile.setTextColor(ContextCompat.getColor(this, R.color.textPrimary));
        android.graphics.drawable.GradientDrawable selectBtnBg = new android.graphics.drawable.GradientDrawable();
        selectBtnBg.setColor(ContextCompat.getColor(this, R.color.buttonPrimary));
        selectBtnBg.setCornerRadius(8);
        btnSelectFile.setBackground(selectBtnBg);
        btnSelectFile.setPadding(24, 16, 24, 16);
        btnSelectFile.setOnClickListener(v -> selectAudioFile());
        LinearLayout.LayoutParams selectBtnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        selectBtnParams.setMargins(0, 0, 0, 16);
        mainContainer.addView(btnSelectFile, selectBtnParams);
        
        // Dosya yolu gösterimi
        TextView filePathLabel = new TextView(this);
        filePathLabel.setText("Seçilen Dosya:");
        filePathLabel.setTextSize(14);
        filePathLabel.setTextColor(ContextCompat.getColor(this, R.color.textSecondary));
        filePathLabel.setPadding(0, 0, 0, 8);
        mainContainer.addView(filePathLabel);
        
        tvFilePath = new TextView(this);
        tvFilePath.setText("Henüz dosya seçilmedi");
        tvFilePath.setTextSize(14);
        tvFilePath.setTextColor(ContextCompat.getColor(this, R.color.textPrimary));
        tvFilePath.setPadding(16, 12, 16, 12);
        android.graphics.drawable.GradientDrawable filePathBg = new android.graphics.drawable.GradientDrawable();
        filePathBg.setColor(ContextCompat.getColor(this, R.color.surfaceCard));
        filePathBg.setCornerRadius(8);
        filePathBg.setStroke(1, ContextCompat.getColor(this, R.color.outline));
        tvFilePath.setBackground(filePathBg);
        LinearLayout.LayoutParams filePathParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        filePathParams.setMargins(0, 0, 0, 24);
        mainContainer.addView(tvFilePath, filePathParams);
        
        // Butonlar container (Çal - Durdur)
        LinearLayout buttonsContainer = new LinearLayout(this);
        buttonsContainer.setOrientation(LinearLayout.HORIZONTAL);
        buttonsContainer.setPadding(0, 0, 0, 0);
        
        // Çal butonu
        btnPlay = new Button(this);
        btnPlay.setText("Çal");
        btnPlay.setTextSize(16);
        btnPlay.setTextColor(ContextCompat.getColor(this, R.color.textPrimary));
        android.graphics.drawable.GradientDrawable playBtnBg = new android.graphics.drawable.GradientDrawable();
        playBtnBg.setColor(ContextCompat.getColor(this, R.color.buttonSuccessBright));
        playBtnBg.setCornerRadius(8);
        btnPlay.setBackground(playBtnBg);
        btnPlay.setPadding(24, 16, 24, 16);
        btnPlay.setEnabled(false);
        btnPlay.setOnClickListener(v -> playAudio());
        LinearLayout.LayoutParams playBtnParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        playBtnParams.setMargins(0, 0, 8, 0);
        buttonsContainer.addView(btnPlay, playBtnParams);
        
        // Durdur butonu
        btnStop = new Button(this);
        btnStop.setText("Durdur");
        btnStop.setTextSize(16);
        btnStop.setTextColor(ContextCompat.getColor(this, R.color.textPrimary));
        android.graphics.drawable.GradientDrawable stopBtnBg = new android.graphics.drawable.GradientDrawable();
        stopBtnBg.setColor(ContextCompat.getColor(this, R.color.statusErrorBright));
        stopBtnBg.setCornerRadius(8);
        btnStop.setBackground(stopBtnBg);
        btnStop.setPadding(24, 16, 24, 16);
        btnStop.setEnabled(false);
        btnStop.setOnClickListener(v -> stopAudio());
        LinearLayout.LayoutParams stopBtnParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        buttonsContainer.addView(btnStop, stopBtnParams);
        
        mainContainer.addView(buttonsContainer);
        
        // Açılışta otomatik çal (OEM segmented)
        TextView autoPlayTitle = new TextView(this);
        autoPlayTitle.setText("Açılışta Otomatik Çal");
        autoPlayTitle.setTextSize(15);
        autoPlayTitle.setTextColor(ContextCompat.getColor(this, R.color.textPrimary87));
        autoPlayTitle.setTypeface(null, android.graphics.Typeface.NORMAL);
        autoPlayTitle.setPadding(0, 24, 0, 8);
        mainContainer.addView(autoPlayTitle);
        
        TextView autoPlayDesc = new TextView(this);
        autoPlayDesc.setText("Araç açıldığında otomatik çal");
        autoPlayDesc.setTextSize(13);
        autoPlayDesc.setTextColor(ContextCompat.getColor(this, R.color.textHint));
        autoPlayDesc.setPadding(0, 0, 0, 12);
        mainContainer.addView(autoPlayDesc);
        
        boolean autoPlayInitial = prefs.getBoolean("welcomeAudioAutoPlay", false);
        LinearLayout autoPlayBlock = new LinearLayout(this);
        autoPlayBlock.setOrientation(LinearLayout.VERTICAL);
        autoPlayBlock.setPadding(0, 0, 0, 0);
        UiStyles.addBinarySegmentedControl(this, autoPlayBlock,
                null,
                "Açık", "Kapalı",
                "Araç açıldığında otomatik çal.",
                "Otomatik çalma kapalı.",
                autoPlayInitial,
                this::saveAutoPlaySetting);
        mainContainer.addView(autoPlayBlock);
        
        // Kaydedilmiş ayarları yükle
        loadSavedSettings();
        
        scrollView.addView(mainContainer);
        
        // ScrollView'ı rootView'a ekle
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f); // Weight 1 ile kalan alanı kaplar
        rootView.addView(scrollView, scrollParams);
        
        setContentView(rootView);
    }
    
    private void selectAudioFile() {
        // Download klasöründeki ses dosyalarını bul
        List<File> audioFiles = getAudioFilesFromDownload();
        
        if (audioFiles.isEmpty()) {
            new AlertDialog.Builder(this)
                .setTitle("Ses Dosyası Bulunamadı")
                .setMessage("Download klasöründe ses dosyası bulunamadı.\n\nDesteklenen formatlar: mp3, wav, m4a, ogg, flac, aac")
                .setPositiveButton("Tamam", null)
                .show();
            return;
        }
        
        // Dosya listesi dialog'u oluştur
        showAudioFileDialog(audioFiles);
    }
    
    private List<File> getAudioFilesFromDownload() {
        List<File> audioFiles = new ArrayList<>();
        
        // Ses dosyası uzantıları
        List<String> audioExtensions = Arrays.asList(".mp3", ".wav", ".m4a", ".ogg", ".flac", ".aac", ".wma", ".amr");
        
        try {
            // Download klasörünü al
            File downloadDir;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // Android 10+ için MediaStore kullanılabilir ama basit yol için Environment kullanıyoruz
                downloadDir = new File(android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS).getAbsolutePath());
            } else {
                downloadDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS);
            }
            
            if (downloadDir == null || !downloadDir.exists() || !downloadDir.isDirectory()) {
                // Alternatif yol deneyelim
                downloadDir = new File("/storage/emulated/0/Download");
                if (!downloadDir.exists()) {
                    downloadDir = new File("/sdcard/Download");
                }
            }
            
            if (downloadDir != null && downloadDir.exists() && downloadDir.isDirectory()) {
                File[] files = downloadDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile()) {
                            String fileName = file.getName().toLowerCase();
                            for (String ext : audioExtensions) {
                                if (fileName.endsWith(ext)) {
                                    audioFiles.add(file);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            
            // Dosya adına göre sırala
            Collections.sort(audioFiles, (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
            
        } catch (Exception e) {
            Toast.makeText(this, "Download klasörü okunamadı: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        
        return audioFiles;
    }
    
    private void showAudioFileDialog(List<File> audioFiles) {
        // Dosya adlarını al
        String[] fileNames = new String[audioFiles.size()];
        for (int i = 0; i < audioFiles.size(); i++) {
            fileNames[i] = audioFiles.get(i).getName();
        }
        
        // Dialog oluştur
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Ses Dosyası Seç (" + audioFiles.size() + " dosya)");
        
        builder.setItems(fileNames, (dialog, which) -> {
            File selectedFile = audioFiles.get(which);
            selectedFilePath = Uri.fromFile(selectedFile).toString();
            tvFilePath.setText(selectedFile.getName());
            btnPlay.setEnabled(true);
            btnStop.setEnabled(false);
            
            // Dosya yolunu SharedPreferences'a kaydet
            saveSelectedFilePath(selectedFile.getAbsolutePath());
            
            Toast.makeText(this, "Dosya seçildi: " + selectedFile.getName(), Toast.LENGTH_SHORT).show();
        });
        
        builder.setNegativeButton("İptal", null);
        builder.show();
    }
    
    
    private void playAudio() {
        if (selectedFilePath == null || selectedFilePath.isEmpty()) {
            Toast.makeText(this, "Lütfen önce bir dosya seçin", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            // Eğer zaten çalıyorsa durdur
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }
            
            // Yeni MediaPlayer oluştur
            mediaPlayer = new MediaPlayer();
            Uri audioUri = Uri.parse(selectedFilePath);
            
            // File URI ise direkt path kullan
            if (audioUri.getScheme() != null && audioUri.getScheme().equals("file")) {
                File audioFile = new File(audioUri.getPath());
                if (audioFile.exists()) {
                    mediaPlayer.setDataSource(audioFile.getAbsolutePath());
                } else {
                    throw new IOException("Dosya bulunamadı: " + audioFile.getAbsolutePath());
                }
            } else {
                // Content URI ise
                mediaPlayer.setDataSource(this, audioUri);
            }
            mediaPlayer.prepare();
            
            // Çalma bitince butonları güncelle
            mediaPlayer.setOnCompletionListener(mp -> {
                handler.post(() -> {
                    btnPlay.setEnabled(true);
                    btnStop.setEnabled(false);
                    Toast.makeText(AudioTestActivity.this, "Ses çalma tamamlandı", Toast.LENGTH_SHORT).show();
                });
            });
            
            // Hata durumunda
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                handler.post(() -> {
                    Toast.makeText(AudioTestActivity.this, "Ses çalma hatası: " + what, Toast.LENGTH_SHORT).show();
                    btnPlay.setEnabled(true);
                    btnStop.setEnabled(false);
                });
                return true;
            });
            
            mediaPlayer.start();
            btnPlay.setEnabled(false);
            btnStop.setEnabled(true);
            Toast.makeText(this, "Ses çalınıyor...", Toast.LENGTH_SHORT).show();
            
        } catch (IOException e) {
            Toast.makeText(this, "Dosya açılamadı: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            btnPlay.setEnabled(true);
            btnStop.setEnabled(false);
        } catch (Exception e) {
            Toast.makeText(this, "Hata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            btnPlay.setEnabled(true);
            btnStop.setEnabled(false);
        }
    }
    
    private void stopAudio() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayer = null;
            btnPlay.setEnabled(true);
            btnStop.setEnabled(false);
            Toast.makeText(this, "Ses durduruldu", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAudio();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Uygulama arka plana geçince sesi durdur
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }
    
    private void saveSelectedFilePath(String filePath) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("welcomeAudioFilePath", filePath);
        editor.apply();
    }
    
    private void saveAutoPlaySetting(boolean enabled) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("welcomeAudioAutoPlay", enabled);
        editor.apply();
    }
    
    private void loadSavedSettings() {
        // Kaydedilmiş dosya yolunu yükle
        String savedFilePath = prefs.getString("welcomeAudioFilePath", null);
        if (savedFilePath != null && !savedFilePath.isEmpty()) {
            File file = new File(savedFilePath);
            if (file.exists()) {
                selectedFilePath = Uri.fromFile(file).toString();
                tvFilePath.setText(file.getName());
                btnPlay.setEnabled(true);
            }
        }
        
    }
}

