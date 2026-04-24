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
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.SharedPreferences;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
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
    private RadioGroup autoPlayRadioGroup;
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
        rootView.setBackgroundColor(0xFF0A0F14); // CameraActivity ile aynı arka plan
        
        // Geri Dön Butonu Bar (CameraActivity ile aynı tasarım)
        LinearLayout backButtonBar = new LinearLayout(this);
        backButtonBar.setOrientation(LinearLayout.HORIZONTAL);
        backButtonBar.setBackgroundColor(0xFF1C2630); // CameraActivity ile aynı renk
        backButtonBar.setPadding(16, 16, 16, 16);
        backButtonBar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        
        // Geri dön butonu (CameraActivity ile aynı tasarım)
        Button btnBack = new Button(this);
        btnBack.setText("← Geri Dön");
        btnBack.setTextSize(16);
        btnBack.setTextColor(0xFFFFFFFF);
        android.graphics.drawable.GradientDrawable backBtnBg = new android.graphics.drawable.GradientDrawable();
        backBtnBg.setColor(0xFF3DAEA8);
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
        scrollView.setBackgroundColor(0xFF0A0F14);
        scrollView.setFillViewport(true);
        
        LinearLayout mainContainer = new LinearLayout(this);
        mainContainer.setOrientation(LinearLayout.VERTICAL);
        mainContainer.setPadding(24, 24, 24, 24);
        mainContainer.setBackgroundColor(0xFF0A0F14);
        
        // Başlık
        TextView titleText = new TextView(this);
        titleText.setText("Hoşgeldin Ses Test");
        titleText.setTextSize(24);
        titleText.setTextColor(0xFFFFFFFF);
        titleText.setTypeface(null, android.graphics.Typeface.BOLD);
        titleText.setPadding(0, 0, 0, 24);
        mainContainer.addView(titleText);
        
        // Dosya seç butonu
        btnSelectFile = new Button(this);
        btnSelectFile.setText("Dosya Seç");
        btnSelectFile.setTextSize(16);
        btnSelectFile.setTextColor(0xFFFFFFFF);
        android.graphics.drawable.GradientDrawable selectBtnBg = new android.graphics.drawable.GradientDrawable();
        selectBtnBg.setColor(0xFF3DAEA8);
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
        filePathLabel.setTextColor(0xFFB0B0B0);
        filePathLabel.setPadding(0, 0, 0, 8);
        mainContainer.addView(filePathLabel);
        
        tvFilePath = new TextView(this);
        tvFilePath.setText("Henüz dosya seçilmedi");
        tvFilePath.setTextSize(14);
        tvFilePath.setTextColor(0xFFFFFFFF);
        tvFilePath.setPadding(16, 12, 16, 12);
        android.graphics.drawable.GradientDrawable filePathBg = new android.graphics.drawable.GradientDrawable();
        filePathBg.setColor(0xFF151C24);
        filePathBg.setCornerRadius(8);
        filePathBg.setStroke(1, 0xFF2A3A47);
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
        btnPlay.setTextColor(0xFFFFFFFF);
        android.graphics.drawable.GradientDrawable playBtnBg = new android.graphics.drawable.GradientDrawable();
        playBtnBg.setColor(0xFF4CAF50);
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
        btnStop.setTextColor(0xFFFFFFFF);
        android.graphics.drawable.GradientDrawable stopBtnBg = new android.graphics.drawable.GradientDrawable();
        stopBtnBg.setColor(0xFFF44336);
        stopBtnBg.setCornerRadius(8);
        btnStop.setBackground(stopBtnBg);
        btnStop.setPadding(24, 16, 24, 16);
        btnStop.setEnabled(false);
        btnStop.setOnClickListener(v -> stopAudio());
        LinearLayout.LayoutParams stopBtnParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        buttonsContainer.addView(btnStop, stopBtnParams);
        
        mainContainer.addView(buttonsContainer);
        
        // Açılışta otomatik çal RadioGroup
        TextView autoPlayTitle = new TextView(this);
        autoPlayTitle.setText("Açılışta Otomatik Çal");
        autoPlayTitle.setTextSize(15);
        autoPlayTitle.setTextColor(0xE6FFFFFF); // %90 opaklık
        autoPlayTitle.setTypeface(null, android.graphics.Typeface.NORMAL);
        autoPlayTitle.setPadding(0, 24, 0, 8);
        mainContainer.addView(autoPlayTitle);
        
        TextView autoPlayDesc = new TextView(this);
        autoPlayDesc.setText("Araç açıldığında otomatik çal");
        autoPlayDesc.setTextSize(13);
        autoPlayDesc.setTextColor(0xAAFFFFFF); // %67 opaklık
        autoPlayDesc.setPadding(0, 0, 0, 12);
        mainContainer.addView(autoPlayDesc);
        
        autoPlayRadioGroup = new RadioGroup(this);
        autoPlayRadioGroup.setOrientation(LinearLayout.VERTICAL);
        autoPlayRadioGroup.setPadding(20, 0, 20, 0);
        
        // RadioButton 1: Açık
        LinearLayout option1Container = new LinearLayout(this);
        option1Container.setOrientation(LinearLayout.HORIZONTAL);
        option1Container.setPadding(16, 16, 16, 16);
        option1Container.setGravity(android.view.Gravity.CENTER_VERTICAL);
        option1Container.setClickable(true);
        option1Container.setFocusable(true);
        
        LinearLayout iconCircle1 = new LinearLayout(this);
        iconCircle1.setOrientation(LinearLayout.VERTICAL);
        iconCircle1.setBackgroundColor(0xFF1A2330);
        iconCircle1.setGravity(android.view.Gravity.CENTER);
        iconCircle1.setPadding(10, 10, 10, 10);
        
        TextView icon1 = new TextView(this);
        icon1.setText("✓");
        icon1.setTextSize(20);
        icon1.setTextColor(0xFFFFFFFF);
        iconCircle1.addView(icon1);
        
        LinearLayout.LayoutParams iconCircle1Params = new LinearLayout.LayoutParams(48, 48);
        iconCircle1Params.setMargins(0, 0, 12, 0);
        option1Container.addView(iconCircle1, iconCircle1Params);
        
        LinearLayout textColumn1 = new LinearLayout(this);
        textColumn1.setOrientation(LinearLayout.VERTICAL);
        
        TextView title1 = new TextView(this);
        title1.setText("Açık");
        title1.setTextColor(0xFFFFFFFF);
        title1.setTextSize(16);
        title1.setTypeface(null, android.graphics.Typeface.NORMAL);
        textColumn1.addView(title1);
        
        TextView desc1 = new TextView(this);
        desc1.setText("Araç açıldığında otomatik çal");
        desc1.setTextColor(0xAAFFFFFF);
        desc1.setTextSize(13);
        desc1.setPadding(0, 2, 0, 0);
        textColumn1.addView(desc1);
        
        LinearLayout.LayoutParams textParams1 = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        option1Container.addView(textColumn1, textParams1);
        
        RadioButton radio1 = new RadioButton(this);
        radio1.setId(1);
        radio1.setClickable(false);
        radio1.setFocusable(false);
        option1Container.addView(radio1);
        
        option1Container.setOnClickListener(v -> {
            autoPlayRadioGroup.check(1);
            saveAutoPlaySetting(true);
        });
        
        autoPlayRadioGroup.addView(option1Container);
        
        // Ayırıcı çizgi
        android.view.View divider1 = new android.view.View(this);
        divider1.setBackgroundColor(0x1FFFFFFF);
        autoPlayRadioGroup.addView(divider1, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        
        // RadioButton 2: Kapalı
        LinearLayout option2Container = new LinearLayout(this);
        option2Container.setOrientation(LinearLayout.HORIZONTAL);
        option2Container.setPadding(16, 16, 16, 16);
        option2Container.setGravity(android.view.Gravity.CENTER_VERTICAL);
        option2Container.setClickable(true);
        option2Container.setFocusable(true);
        
        LinearLayout iconCircle2 = new LinearLayout(this);
        iconCircle2.setOrientation(LinearLayout.VERTICAL);
        iconCircle2.setBackgroundColor(0xFF1A2330);
        iconCircle2.setGravity(android.view.Gravity.CENTER);
        iconCircle2.setPadding(10, 10, 10, 10);
        
        TextView icon2 = new TextView(this);
        icon2.setText("✗");
        icon2.setTextSize(20);
        icon2.setTextColor(0xFFFFFFFF);
        iconCircle2.addView(icon2);
        
        LinearLayout.LayoutParams iconCircle2Params = new LinearLayout.LayoutParams(48, 48);
        iconCircle2Params.setMargins(0, 0, 12, 0);
        option2Container.addView(iconCircle2, iconCircle2Params);
        
        LinearLayout textColumn2 = new LinearLayout(this);
        textColumn2.setOrientation(LinearLayout.VERTICAL);
        
        TextView title2 = new TextView(this);
        title2.setText("Kapalı");
        title2.setTextColor(0xFFFFFFFF);
        title2.setTextSize(16);
        title2.setTypeface(null, android.graphics.Typeface.NORMAL);
        textColumn2.addView(title2);
        
        TextView desc2 = new TextView(this);
        desc2.setText("Otomatik çalma kapalı");
        desc2.setTextColor(0xAAFFFFFF);
        desc2.setTextSize(13);
        desc2.setPadding(0, 2, 0, 0);
        textColumn2.addView(desc2);
        
        LinearLayout.LayoutParams textParams2 = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        option2Container.addView(textColumn2, textParams2);
        
        RadioButton radio2 = new RadioButton(this);
        radio2.setId(0);
        radio2.setClickable(false);
        radio2.setFocusable(false);
        option2Container.addView(radio2);
        
        option2Container.setOnClickListener(v -> {
            autoPlayRadioGroup.check(0);
            saveAutoPlaySetting(false);
        });
        
        autoPlayRadioGroup.addView(option2Container);
        
        mainContainer.addView(autoPlayRadioGroup);
        
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
        
        // Kaydedilmiş otomatik çal ayarını yükle
        boolean autoPlayEnabled = prefs.getBoolean("welcomeAudioAutoPlay", false);
        if (autoPlayEnabled) {
            autoPlayRadioGroup.check(1);
        } else {
            autoPlayRadioGroup.check(0);
        }
    }
}

