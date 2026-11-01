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
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.mapcontrol.R;
import com.mapcontrol.api.ProfileApiService;
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
    private TextView downloadsStatus;
    private LinearLayout downloadsList;
    private TextView savedLine;
    private TextView tvFilePath;
    private LinearLayout playbackRow;
    private TextView playbackLabel;
    private EditText lineInput;
    private Button generateButton;
    private TextView accessWarning;
    private MediaPlayer mediaPlayer;
    private String selectedFilePath;
    private boolean playing;
    private boolean copying;
    private boolean generating;
    private boolean mediaRegistered;
    private final ProfileApiService apiService;
    private View apiBlock;
    private TextView soundTitle;
    private Runnable openSoundPage;
    private boolean apiToolsVisible;
    private int usbScanGen;
    private AlertDialog mp3Dialog;
    private LinearLayout mp3DialogList;

    public WelcomeSoundTabBuilder(AppCompatActivity activity, SharedPreferences prefs,
            ActivityResultLauncher<Intent> audioPicker) {
        this.activity = activity;
        this.prefs = prefs;
        this.audioPicker = audioPicker;
        this.apiService = new ProfileApiService(activity);
        registerMedia();
        rebuild();
    }

    /** Light/dark geçişinde ekranı taze renklerle yeniden kurar. */
    public void rebuild() {
        onHostPause();
        boolean showApi = apiToolsVisible;
        boolean autoPlayInitial = prefs.getBoolean("welcomeAudioAutoPlay", false);
        String designId = WelcomeDesignCatalog.savedId(prefs);
        boolean maleVoice = ProfileApiService.VOICE_MALE.equals(savedVoice());
        WelcomeSoundScreenBuilder.Screen screen = WelcomeSoundScreenBuilder.buildTabScrollView(
                activity, autoPlayInitial, this::saveAutoPlaySetting,
                designId, this::saveDesignSetting,
                prefs.getString(WelcomeDesignCatalog.PREF_TTS_LINE, ""),
                this::saveSpokenLine,
                prefs.getString(WelcomeDesignCatalog.PREF_LINE, ""),
                this::saveCustomLine,
                maleVoice, this::saveVoiceSetting);
        this.scrollView = screen.scrollView;
        this.usbStatus = screen.usbStatus;
        this.usbList = screen.usbList;
        this.downloadsStatus = screen.downloadsStatus;
        this.downloadsList = screen.downloadsList;
        this.savedLine = screen.savedLine;
        this.tvFilePath = screen.tvFilePath;
        screen.onShowSound = this::refreshSoundSources;
        this.playbackRow = screen.playbackRow;
        this.playbackLabel = screen.playbackLabel;
        this.lineInput = screen.lineInput;
        this.generateButton = screen.btnGenerate;
        this.accessWarning = screen.accessWarning;
        this.apiBlock = screen.apiBlock;
        this.soundTitle = screen.soundTitle;
        this.openSoundPage = screen.openSoundPage;

        screen.onOpenPublicSound = this::showPublicSound;
        screen.rowSelect.setOnClickListener(v -> showMp3Dialog());
        generateButton.setOnClickListener(v -> generateWelcomeAudio());
        setApiToolsVisible(showApi);
        if (showApi && openSoundPage != null) {
            openSoundPage.run();
        }
        playbackRow.setOnClickListener(v -> {
            if (playing) {
                stopAudio();
            } else {
                playAudio();
            }
        });
        screen.btnPreview.setOnClickListener(v -> WelcomeScreenPresenter.showSaved(activity));

        loadSavedSettings();
        refreshSoundSources();
    }

    public ScrollView getScrollView() {
        return scrollView;
    }

    /** Menüden açılınca yalnızca USB seçimi kalır. */
    public void showPublicSound() {
        setApiToolsVisible(false);
    }

    /** Gizli log ekranından ses üretme araçlarını açar. */
    public void openGenerateTools() {
        setApiToolsVisible(true);
        if (openSoundPage != null) {
            openSoundPage.run();
        }
        refreshSoundSources();
    }

    /** Sekme açılınca USB listesini yeniler. */
    public void onTabVisible() {
        refreshSoundSources();
        if (!apiToolsVisible) {
            return;
        }
        if (!apiService.isLoggedIn()) {
            updateAccessWarning();
            return;
        }
        apiService.refreshMe(new ProfileApiService.ApiCallback() {
            @Override
            public void onSuccess(String message, org.json.JSONObject data) {
                updateAccessWarning();
            }

            @Override
            public void onError(String error) {
                updateAccessWarning();
            }
        });
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

    private void refreshSoundSources() {
        refreshDownloads();
        refreshUsb();
    }

    private void refreshDownloads() {
        if (downloadsList == null) {
            return;
        }
        new Thread(() -> {
            List<File> files = new java.util.ArrayList<>();
            java.util.Set<String> seen = new java.util.LinkedHashSet<>();
            addMp3(new File(activity.getFilesDir(), "welcome-audio"), files, seen);
            files.sort((a, b) -> b.getName().compareToIgnoreCase(a.getName()));
            handler.post(() -> renderDownloads(files));
        }, "welcome-downloads").start();
    }

    private void renderDownloads(List<File> files) {
        if (mp3Dialog != null && mp3Dialog.isShowing()) {
            fillMp3List(mp3DialogList, files);
        }
    }

    private void showMp3Dialog() {
        if (downloadsList == null) {
            return;
        }
        new Thread(() -> {
            List<File> files = new java.util.ArrayList<>();
            java.util.Set<String> seen = new java.util.LinkedHashSet<>();
            addMp3(new File(activity.getFilesDir(), "welcome-audio"), files, seen);
            files.sort((a, b) -> b.getName().compareToIgnoreCase(a.getName()));
            handler.post(() -> presentMp3Dialog(files));
        }, "welcome-mp3-dialog").start();
    }

    private void presentMp3Dialog(List<File> files) {
        if (mp3Dialog != null && mp3Dialog.isShowing() && mp3DialogList != null) {
            fillMp3List(mp3DialogList, files);
            return;
        }
        ScrollView scroll = new ScrollView(activity);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(16 * activity.getResources().getDisplayMetrics().density);
        list.setPadding(pad, pad, pad, pad);
        fillMp3List(list, files);
        scroll.addView(list);
        mp3DialogList = list;
        mp3Dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.welcome_downloads_title)
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        mp3Dialog.setOnDismissListener(dialog -> mp3DialogList = null);
        mp3Dialog.show();
    }

    private void fillMp3List(LinearLayout list, List<File> files) {
        if (list == null) {
            return;
        }
        list.removeAllViews();
        if (files == null || files.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText(R.string.welcome_downloads_empty);
            empty.setTextSize(15);
            list.addView(empty);
            return;
        }
        String selected = prefs.getString("welcomeAudioFilePath", "");
        for (File file : files) {
            list.addView(mp3Row(file, selected != null && selected.equals(file.getAbsolutePath())));
        }
    }

    private LinearLayout mp3Row(File file, boolean selected) {
        float density = activity.getResources().getDisplayMetrics().density;
        int gap = Math.round(8 * density);
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, gap, 0, gap);

        TextView mark = new TextView(activity);
        mark.setText(selected ? "✓" : "");
        mark.setTextSize(18);
        mark.setMinWidth(Math.round(28 * density));
        row.addView(mark);

        TextView name = new TextView(activity);
        name.setText(file.getName());
        name.setTextSize(15);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(name, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        row.addView(mp3Button(R.string.welcome_mp3_select, v -> {
            applySelected(file);
            showMp3Dialog();
        }));
        row.addView(mp3Button(R.string.welcome_mp3_play, v -> playFile(file)));
        row.addView(mp3Button(R.string.welcome_mp3_delete, v -> deleteMp3(file)));
        return row;
    }

    private Button mp3Button(int textRes, android.view.View.OnClickListener click) {
        Button button = new Button(activity);
        button.setText(textRes);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setOnClickListener(click);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMarginStart(Math.round(6 * activity.getResources().getDisplayMetrics().density));
        button.setLayoutParams(params);
        return button;
    }

    private void deleteMp3(File file) {
        String selected = prefs.getString("welcomeAudioFilePath", "");
        if (selected != null && selected.equals(file.getAbsolutePath())) {
            prefs.edit().remove("welcomeAudioFilePath").apply();
            selectedFilePath = null;
            setPlaying(false);
            releasePlayer();
        }
        if (!file.delete() && file.exists()) {
            Toast.makeText(activity, R.string.welcome_sound_copy_error, Toast.LENGTH_SHORT).show();
            return;
        }
        showMp3Dialog();
    }

    private void playFile(File file) {
        if (file == null || !file.exists()) {
            Toast.makeText(activity, R.string.welcome_sound_file_open_error, Toast.LENGTH_SHORT).show();
            return;
        }
        selectedFilePath = Uri.fromFile(file).toString();
        releasePlayer();
        setPlaying(false);
        playAudio();
    }

    private static void addMp3(File dir, List<File> files, java.util.Set<String> seen) {
        File[] children = dir == null ? null : dir.listFiles();
        if (children == null) {
            return;
        }
        for (File file : children) {
            addMp3File(file, files, seen);
        }
    }

    private static void addMp3File(File file, List<File> files, java.util.Set<String> seen) {
        if (file == null || !file.isFile()) {
            return;
        }
        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
        if (!name.endsWith(".mp3")) {
            return;
        }
        String key = file.getAbsolutePath();
        if (seen.add(key)) {
            files.add(file);
        }
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
        if (usbList == null) {
            return;
        }
        android.view.View block = (android.view.View) usbList.getParent();
        usbList.removeAllViews();
        java.util.List<File> mp3s = scan.present
                ? scan.files
                : java.util.Collections.emptyList();
        block.setVisibility(android.view.View.VISIBLE);
        if (!scan.present || mp3s.isEmpty()) {
            if (usbStatus != null) {
                usbStatus.setText(!scan.present
                        ? R.string.welcome_sound_usb_absent
                        : R.string.welcome_sound_usb_empty);
                usbStatus.setVisibility(android.view.View.VISIBLE);
            }
            usbList.setVisibility(android.view.View.GONE);
            return;
        }
        if (usbStatus != null) {
            usbStatus.setVisibility(android.view.View.GONE);
        }
        usbList.setVisibility(android.view.View.VISIBLE);
        for (File file : mp3s) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            int gap = Math.round(8 * activity.getResources().getDisplayMetrics().density);
            row.setPadding(0, gap, 0, gap);
            TextView name = new TextView(activity);
            name.setText(file.getName());
            name.setTextSize(15);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(name, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(mp3Button(R.string.welcome_mp3_select, v -> importFile(file)));
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
        refreshDownloads();
    }

    private void generateWelcomeAudio() {
        if (generating) {
            return;
        }
        updateAccessWarning();
        if (!apiService.isLoggedIn()) {
            Toast.makeText(activity, R.string.welcome_tts_need_login, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!apiService.isPremium()) {
            Toast.makeText(activity, R.string.welcome_tts_need_premium, Toast.LENGTH_SHORT).show();
            return;
        }
        String text = lineInput == null ? "" : lineInput.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(activity, R.string.welcome_tts_need_text, Toast.LENGTH_SHORT).show();
            return;
        }
        saveSpokenLine(text);
        generating = true;
        releasePlayer();
        setPlaying(false);
        showGenerateStatus(R.string.welcome_tts_generating);
        if (generateButton != null) {
            generateButton.setEnabled(false);
        }
        apiService.generateWelcomeAudio(text, savedVoice(), new ProfileApiService.AudioCallback() {
            @Override
            public void onDownloading() {
                showGenerateStatus(R.string.welcome_tts_downloading);
            }

            @Override
            public void onSuccess(File file) {
                generating = false;
                if (generateButton != null) {
                    generateButton.setEnabled(true);
                    generateButton.setText(R.string.welcome_tts_generate);
                }
                showGenerateStatus(R.string.welcome_tts_saved);
                applyGenerated(file);
            }

            @Override
            public void onError(String error) {
                generating = false;
                if (generateButton != null) {
                    generateButton.setEnabled(true);
                    generateButton.setText(R.string.welcome_tts_generate);
                }
                showGenerateStatus(0);
                updateAccessWarning();
                Toast.makeText(activity, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showGenerateStatus(int textRes) {
        if (savedLine == null) {
            return;
        }
        if (textRes == 0) {
            savedLine.setVisibility(android.view.View.GONE);
            return;
        }
        savedLine.setText(textRes);
        savedLine.setVisibility(android.view.View.VISIBLE);
    }

    private void applyGenerated(File dest) {
        if (dest == null) {
            return;
        }
        refreshSoundSources();
        scrollToMp3List();
    }

    private void scrollToMp3List() {
        if (scrollView == null || downloadsList == null) {
            return;
        }
        downloadsList.post(() -> {
            int y = 0;
            View view = downloadsList;
            while (view != null && view != scrollView) {
                y += view.getTop();
                if (!(view.getParent() instanceof View)) {
                    break;
                }
                view = (View) view.getParent();
            }
            scrollView.smoothScrollTo(0, Math.max(0, y - 12));
        });
    }

    private void setApiToolsVisible(boolean visible) {
        apiToolsVisible = visible;
        if (apiBlock != null) {
            apiBlock.setVisibility(visible ? android.view.View.VISIBLE : android.view.View.GONE);
        }
        if (soundTitle != null) {
            soundTitle.setText(visible
                    ? R.string.welcome_group_generate
                    : R.string.welcome_group_sound);
        }
    }

    private void updateAccessWarning() {
        if (accessWarning == null) {
            return;
        }
        if (!apiService.isLoggedIn()) {
            accessWarning.setText(R.string.welcome_tts_need_login);
            accessWarning.setVisibility(android.view.View.VISIBLE);
        } else if (!apiService.isPremium()) {
            accessWarning.setText(R.string.welcome_tts_need_premium);
            accessWarning.setVisibility(android.view.View.VISIBLE);
        } else {
            accessWarning.setVisibility(android.view.View.GONE);
        }
    }

    private String savedVoice() {
        String voice = prefs.getString("welcomeTtsVoice", ProfileApiService.VOICE_FEMALE);
        return ProfileApiService.VOICE_MALE.equals(voice)
                ? ProfileApiService.VOICE_MALE
                : ProfileApiService.VOICE_FEMALE;
    }

    private void saveVoiceSetting(boolean male) {
        prefs.edit().putString("welcomeTtsVoice",
                male ? ProfileApiService.VOICE_MALE : ProfileApiService.VOICE_FEMALE).apply();
    }

    private void playAudio() {
        if (generating) {
            return;
        }
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
        if (active) {
            playbackLabel.setText(R.string.welcome_sound_stop);
        } else if (selectedFilePath != null && !selectedFilePath.isEmpty()) {
            playbackLabel.setText(R.string.welcome_sound_selected_label);
        } else {
            playbackLabel.setText(R.string.welcome_sound_no_file_yet);
        }
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

    private void saveSpokenLine(String line) {
        prefs.edit().putString(WelcomeDesignCatalog.PREF_TTS_LINE, line == null ? "" : line).apply();
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
