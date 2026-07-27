package com.mapcontrol.ui.builder;

import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.mapcontrol.R;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Açılış sesi sekmesi — {@link MainActivity} içindeki ana içerik alanında kullanılır.
 */
public class WelcomeSoundTabBuilder {

    private final AppCompatActivity activity;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ScrollView scrollView;
    private TextView tvFilePath;
    private Button btnSelectFile;
    private Button btnPlay;
    private Button btnStop;
    private MediaPlayer mediaPlayer;
    private String selectedFilePath;

    public WelcomeSoundTabBuilder(AppCompatActivity activity, SharedPreferences prefs) {
        this.activity = activity;
        this.prefs = prefs;
        rebuild();
    }

    /** Light/dark geçişinde ekranı taze renklerle yeniden kurar. */
    public void rebuild() {
        onHostPause();
        boolean autoPlayInitial = prefs.getBoolean("welcomeAudioAutoPlay", false);
        WelcomeSoundScreenBuilder.Screen screen = WelcomeSoundScreenBuilder.buildTabScrollView(
                activity, autoPlayInitial, this::saveAutoPlaySetting);
        this.scrollView = screen.scrollView;
        this.tvFilePath = screen.tvFilePath;
        this.btnSelectFile = screen.btnSelectFile;
        this.btnPlay = screen.btnPlay;
        this.btnStop = screen.btnStop;

        btnSelectFile.setOnClickListener(v -> selectAudioFile());
        btnPlay.setOnClickListener(v -> playAudio());
        btnStop.setOnClickListener(v -> stopAudio());

        loadSavedSettings();
    }

    public ScrollView getScrollView() {
        return scrollView;
    }

    /** Başka sekmeye veya uygulama arka planına geçerken çağrılır. */
    public void onHostPause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    /** Aktivite yok edilirken oynatıcıyı serbest bırakır. */
    public void releaseAudioFully() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception ignored) {
            }
            mediaPlayer = null;
        }
    }

    private void selectAudioFile() {
        List<File> audioFiles = getAudioFilesFromDownload();
        if (audioFiles.isEmpty()) {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.welcome_sound_no_files_title)
                    .setMessage(R.string.welcome_sound_no_files_message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        showAudioFileDialog(audioFiles);
    }

    private List<File> getAudioFilesFromDownload() {
        List<File> audioFiles = new ArrayList<>();
        List<String> audioExtensions = Arrays.asList(
                ".mp3", ".wav", ".m4a", ".ogg", ".flac", ".aac", ".wma", ".amr");
        try {
            File downloadDir;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                downloadDir = new File(android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS).getAbsolutePath());
            } else {
                downloadDir = android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS);
            }
            if (downloadDir == null || !downloadDir.exists() || !downloadDir.isDirectory()) {
                downloadDir = new File("/storage/emulated/0/Download");
                if (!downloadDir.exists()) {
                    downloadDir = new File("/sdcard/Download");
                }
            }
            if (downloadDir.exists() && downloadDir.isDirectory()) {
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
            Collections.sort(audioFiles, (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
        } catch (Exception e) {
            Toast.makeText(activity, R.string.welcome_sound_folder_error, Toast.LENGTH_SHORT).show();
        }
        return audioFiles;
    }

    private void showAudioFileDialog(List<File> audioFiles) {
        String[] fileNames = new String[audioFiles.size()];
        for (int i = 0; i < audioFiles.size(); i++) {
            fileNames[i] = audioFiles.get(i).getName();
        }
        String title = activity.getString(R.string.welcome_sound_pick_title)
                + " (" + audioFiles.size() + ")";
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setItems(fileNames, (dialog, which) -> {
                    File selectedFile = audioFiles.get(which);
                    selectedFilePath = Uri.fromFile(selectedFile).toString();
                    tvFilePath.setText(selectedFile.getName());
                    btnPlay.setEnabled(true);
                    btnStop.setEnabled(false);
                    saveSelectedFilePath(selectedFile.getAbsolutePath());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void playAudio() {
        if (selectedFilePath == null || selectedFilePath.isEmpty()) {
            Toast.makeText(activity, R.string.welcome_sound_pick_first, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }
            mediaPlayer = new MediaPlayer();
            Uri audioUri = Uri.parse(selectedFilePath);
            if (audioUri.getScheme() != null && audioUri.getScheme().equals("file")) {
                File audioFile = new File(audioUri.getPath());
                if (audioFile.exists()) {
                    mediaPlayer.setDataSource(audioFile.getAbsolutePath());
                } else {
                    throw new IOException("missing");
                }
            } else {
                mediaPlayer.setDataSource(activity, audioUri);
            }
            mediaPlayer.prepare();
            mediaPlayer.setOnCompletionListener(mp -> handler.post(() -> {
                btnPlay.setEnabled(true);
                btnStop.setEnabled(false);
                Toast.makeText(activity, R.string.welcome_sound_play_done, Toast.LENGTH_SHORT).show();
            }));
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                handler.post(() -> {
                    Toast.makeText(activity, R.string.welcome_sound_play_error, Toast.LENGTH_SHORT).show();
                    btnPlay.setEnabled(true);
                    btnStop.setEnabled(false);
                });
                return true;
            });
            mediaPlayer.start();
            btnPlay.setEnabled(false);
            btnStop.setEnabled(true);
            Toast.makeText(activity, R.string.welcome_sound_playing, Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(activity, R.string.welcome_sound_file_open_error, Toast.LENGTH_SHORT).show();
            btnPlay.setEnabled(true);
            btnStop.setEnabled(false);
        } catch (Exception e) {
            Toast.makeText(activity, R.string.welcome_sound_file_open_error, Toast.LENGTH_SHORT).show();
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
            Toast.makeText(activity, R.string.welcome_sound_stopped, Toast.LENGTH_SHORT).show();
        }
    }

    private void saveSelectedFilePath(String filePath) {
        prefs.edit().putString("welcomeAudioFilePath", filePath).apply();
    }

    private void saveAutoPlaySetting(boolean enabled) {
        prefs.edit().putBoolean("welcomeAudioAutoPlay", enabled).apply();
    }

    private void loadSavedSettings() {
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
