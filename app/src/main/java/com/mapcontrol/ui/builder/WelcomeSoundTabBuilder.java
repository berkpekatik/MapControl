package com.mapcontrol.ui.builder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.mapcontrol.R;
import com.mapcontrol.ui.welcome.WelcomeAudioFiles;
import com.mapcontrol.ui.welcome.WelcomeDesignCatalog;
import com.mapcontrol.ui.welcome.WelcomeScreenPresenter;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Açılış sesi sekmesi — {@link MainActivity} içindeki ana içerik alanında kullanılır.
 */
public class WelcomeSoundTabBuilder {

    private final AppCompatActivity activity;
    private final SharedPreferences prefs;
    private final ActivityResultLauncher<Intent> audioPicker;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final BroadcastReceiver mediaReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshUsb();
        }
    };

    private ScrollView scrollView;
    private TextView usbStatus;
    private LinearLayout usbList;
    private TextView tvFilePath;
    private LinearLayout playbackRow;
    private TextView playbackLabel;
    private MediaPlayer mediaPlayer;
    private String selectedFilePath;
    private boolean playing;
    private boolean copying;
    private boolean mediaRegistered;
    private int usbScanGen;

    public WelcomeSoundTabBuilder(AppCompatActivity activity, SharedPreferences prefs,
            ActivityResultLauncher<Intent> audioPicker) {
        this.activity = activity;
        this.prefs = prefs;
        this.audioPicker = audioPicker;
        registerMedia();
        rebuild();
    }

    /** Light/dark geçişinde ekranı taze renklerle yeniden kurar. */
    public void rebuild() {
        onHostPause();
        boolean autoPlayInitial = prefs.getBoolean("welcomeAudioAutoPlay", false);
        String designId = WelcomeDesignCatalog.savedId(prefs);
        WelcomeSoundScreenBuilder.Screen screen = WelcomeSoundScreenBuilder.buildTabScrollView(
                activity, autoPlayInitial, this::saveAutoPlaySetting,
                designId, this::saveDesignSetting,
                prefs.getString(WelcomeDesignCatalog.PREF_LINE, ""),
                this::saveCustomLine);
        this.scrollView = screen.scrollView;
        this.usbStatus = screen.usbStatus;
        this.usbList = screen.usbList;
        this.tvFilePath = screen.tvFilePath;
        this.playbackRow = screen.playbackRow;
        this.playbackLabel = screen.playbackLabel;

        screen.rowSelect.setOnClickListener(v -> selectAudioFile());
        playbackRow.setOnClickListener(v -> {
            if (playing) {
                stopAudio();
            } else {
                playAudio();
            }
        });
        screen.btnPreview.setOnClickListener(v -> WelcomeScreenPresenter.showSaved(activity));

        loadSavedSettings();
        refreshUsb();
    }

    public ScrollView getScrollView() {
        return scrollView;
    }

    /** Sekme açılınca USB listesini yeniler. */
    public void onTabVisible() {
        refreshUsb();
    }

    /** Sistem ses seçicisinin sonucu. */
    public void onAudioPicked(ActivityResult result) {
        if (result.getResultCode() != android.app.Activity.RESULT_OK || result.getData() == null) {
            return;
        }
        Uri uri = result.getData().getData();
        if (uri == null) {
            return;
        }
        try {
            activity.getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        importUri(uri);
    }

    /** Başka sekmeye veya uygulama arka planına geçerken çağrılır. */
    public void onHostPause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
        setPlaying(false);
    }

    /** Aktivite yok edilirken oynatıcıyı serbest bırakır. */
    public void releaseAudioFully() {
        releasePlayer();
        if (mediaRegistered) {
            try {
                activity.unregisterReceiver(mediaReceiver);
            } catch (Exception ignored) {
            }
            mediaRegistered = false;
        }
    }

    private void registerMedia() {
        if (mediaRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_EJECT);
        filter.addAction(Intent.ACTION_MEDIA_REMOVED);
        filter.addDataScheme("file");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(mediaReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            activity.registerReceiver(mediaReceiver, filter);
        }
        mediaRegistered = true;
    }

    private void refreshUsb() {
        final int gen = ++usbScanGen;
        new Thread(() -> {
            WelcomeAudioFiles.UsbScan scan = WelcomeAudioFiles.scanUsb(activity);
            handler.post(() -> {
                if (gen != usbScanGen || usbList == null) {
                    return;
                }
                renderUsb(scan);
            });
        }, "welcome-usb").start();
    }

    private void renderUsb(WelcomeAudioFiles.UsbScan scan) {
        usbList.removeAllViews();
        if (scan.files.isEmpty()) {
            usbList.setVisibility(android.view.View.GONE);
            usbStatus.setVisibility(android.view.View.VISIBLE);
            usbStatus.setText(scan.present
                    ? R.string.welcome_sound_usb_empty
                    : R.string.welcome_sound_usb_absent);
            return;
        }
        usbStatus.setVisibility(android.view.View.GONE);
        usbList.setVisibility(android.view.View.VISIBLE);
        for (File file : scan.files) {
            LinearLayout row = WelcomeSoundScreenBuilder.actionRow(activity, file.getName());
            row.setOnClickListener(v -> importFile(file));
            usbList.addView(row);
        }
    }

    private void selectAudioFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        if (intent.resolveActivity(activity.getPackageManager()) == null) {
            selectFromDownloads();
            return;
        }
        audioPicker.launch(intent);
    }

    private void selectFromDownloads() {
        List<File> audioFiles;
        try {
            audioFiles = WelcomeAudioFiles.scanDownloads();
        } catch (Exception e) {
            Toast.makeText(activity, R.string.welcome_sound_folder_error, Toast.LENGTH_SHORT).show();
            return;
        }
        if (audioFiles.isEmpty()) {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.welcome_sound_no_files_title)
                    .setMessage(R.string.welcome_sound_no_files_message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        String[] fileNames = new String[audioFiles.size()];
        for (int i = 0; i < audioFiles.size(); i++) {
            fileNames[i] = audioFiles.get(i).getName();
        }
        String title = activity.getString(R.string.welcome_sound_pick_title)
                + " (" + audioFiles.size() + ")";
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setItems(fileNames, (dialog, which) -> importFile(audioFiles.get(which)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void importFile(File source) {
        if (copying) {
            return;
        }
        copying = true;
        Toast.makeText(activity, R.string.welcome_sound_copying, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                File dest = WelcomeAudioFiles.copyFile(activity, source);
                handler.post(() -> applySelected(dest));
            } catch (Exception e) {
                handler.post(() -> {
                    copying = false;
                    Toast.makeText(activity, R.string.welcome_sound_copy_error, Toast.LENGTH_SHORT).show();
                });
            }
        }, "welcome-copy").start();
    }

    private void importUri(Uri uri) {
        if (copying) {
            return;
        }
        copying = true;
        Toast.makeText(activity, R.string.welcome_sound_copying, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                File dest = WelcomeAudioFiles.copyUri(activity, uri);
                handler.post(() -> applySelected(dest));
            } catch (Exception e) {
                handler.post(() -> {
                    copying = false;
                    Toast.makeText(activity, R.string.welcome_sound_copy_error, Toast.LENGTH_SHORT).show();
                });
            }
        }, "welcome-copy").start();
    }

    private void applySelected(File dest) {
        copying = false;
        releasePlayer();
        selectedFilePath = Uri.fromFile(dest).toString();
        tvFilePath.setText(dest.getName());
        saveSelectedFilePath(dest.getAbsolutePath());
        setPlaying(false);
        Toast.makeText(activity, R.string.welcome_sound_saved, Toast.LENGTH_SHORT).show();
    }

    private void playAudio() {
        if (selectedFilePath == null || selectedFilePath.isEmpty()) {
            Toast.makeText(activity, R.string.welcome_sound_pick_first, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            releasePlayer();
            mediaPlayer = new MediaPlayer();
            Uri audioUri = Uri.parse(selectedFilePath);
            if ("file".equals(audioUri.getScheme())) {
                File audioFile = new File(audioUri.getPath());
                if (!audioFile.exists()) {
                    throw new IOException("missing");
                }
                mediaPlayer.setDataSource(audioFile.getAbsolutePath());
            } else {
                mediaPlayer.setDataSource(activity, audioUri);
            }
            mediaPlayer.prepare();
            mediaPlayer.setOnCompletionListener(mp -> handler.post(() -> {
                setPlaying(false);
                Toast.makeText(activity, R.string.welcome_sound_play_done, Toast.LENGTH_SHORT).show();
            }));
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                handler.post(() -> {
                    Toast.makeText(activity, R.string.welcome_sound_play_error, Toast.LENGTH_SHORT).show();
                    setPlaying(false);
                });
                return true;
            });
            mediaPlayer.start();
            setPlaying(true);
            Toast.makeText(activity, R.string.welcome_sound_playing, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(activity, R.string.welcome_sound_file_open_error, Toast.LENGTH_SHORT).show();
            setPlaying(false);
        }
    }

    private void stopAudio() {
        releasePlayer();
        setPlaying(false);
        Toast.makeText(activity, R.string.welcome_sound_stopped, Toast.LENGTH_SHORT).show();
    }

    private void releasePlayer() {
        if (mediaPlayer == null) {
            return;
        }
        try {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
        } catch (Exception ignored) {
        }
        mediaPlayer = null;
    }

    private void setPlaying(boolean active) {
        playing = active;
        if (playbackLabel == null || playbackRow == null) {
            return;
        }
        playbackLabel.setText(active ? R.string.welcome_sound_stop : R.string.welcome_sound_play);
        boolean ready = selectedFilePath != null && !selectedFilePath.isEmpty();
        playbackRow.setAlpha(ready ? 1f : 0.45f);
    }

    private void saveSelectedFilePath(String filePath) {
        prefs.edit().putString("welcomeAudioFilePath", filePath).apply();
    }

    private void saveAutoPlaySetting(boolean enabled) {
        prefs.edit().putBoolean("welcomeAudioAutoPlay", enabled).apply();
    }

    private void saveDesignSetting(String designId) {
        prefs.edit().putString(WelcomeDesignCatalog.PREF_KEY, designId).apply();
    }

    private void saveCustomLine(String line) {
        prefs.edit().putString(WelcomeDesignCatalog.PREF_LINE, line == null ? "" : line).apply();
    }

    private void loadSavedSettings() {
        String savedFilePath = prefs.getString("welcomeAudioFilePath", null);
        if (savedFilePath != null && !savedFilePath.isEmpty()) {
            File file = new File(savedFilePath);
            if (file.exists()) {
                selectedFilePath = Uri.fromFile(file).toString();
                tvFilePath.setText(file.getName());
            }
        }
        setPlaying(false);
    }
}
