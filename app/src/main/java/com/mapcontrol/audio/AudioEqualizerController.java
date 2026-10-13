package com.mapcontrol.audio;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.audiofx.DynamicsProcessing;

/**
 * Global çıkışa çok bantlı ekolayzer ve ses yükseltme uygular.
 * Bantlar DynamicsProcessing üzerinde, sınırlayıcıdan önce durur; tepe kırpılmaz.
 */
public final class AudioEqualizerController {

    public static final String PRESET_FLAT = "flat";
    public static final String PRESET_BASS = "bass";
    public static final String PRESET_VOCAL = "vocal";
    public static final String PRESET_TREBLE = "treble";
    public static final String PRESET_CUSTOM = "custom";

    /** Ses yükseltme tavanı. Sınırlayıcı eşiği bu kadar aşağı çekilir, çıkış −1 dBFS'te kalır. */
    public static final int MAX_GAIN_MB = 1500;

    private static final int BAND_RANGE_MB = 1500;
    private static final int[] BAND_HZ = {60, 230, 910, 3600, 14000};
    private static final float FRAME_MS = 10f;
    private static final float LIMITER_RATIO = 10f;
    private static final float LIMITER_ATTACK_MS = 1f;
    private static final float LIMITER_RELEASE_MS = 60f;
    private static final float OUTPUT_CEILING_DB = -1f;

    private static final String PREFS_NAME = "MapControlPrefs";
    private static final String KEY_ENABLED = "equalizerEnabled";
    private static final String KEY_GAIN_MB = "equalizerGainMb";
    private static final String KEY_PRESET = "equalizerPreset";
    private static final String KEY_BANDS = "equalizerBands";

    private static final int SESSION_ID = 0;
    private static final int PRIORITY = Integer.MAX_VALUE;

    private static DynamicsProcessing dynamics;
    private static boolean supported;
    private static boolean keeperActive = true;
    private static long lastPreviewApplyMs;
    private static int minLevelMb = -BAND_RANGE_MB;
    private static int maxLevelMb = BAND_RANGE_MB;
    private static int[] centerHz = new int[0];

    private AudioEqualizerController() {
    }

    public static final class State {
        public final boolean supported;
        public final boolean enabled;
        public final int gainMb;
        public final int maxGainMb;
        public final int minLevelMb;
        public final int maxLevelMb;
        public final int[] centerHz;
        public final int[] bands;
        public final String preset;
        public final boolean active;

        State(boolean supported, boolean enabled, int gainMb, int maxGainMb,
                int minLevelMb, int maxLevelMb, int[] centerHz, int[] bands,
                String preset, boolean active) {
            this.supported = supported;
            this.enabled = enabled;
            this.gainMb = gainMb;
            this.maxGainMb = maxGainMb;
            this.minLevelMb = minLevelMb;
            this.maxLevelMb = maxLevelMb;
            this.centerHz = centerHz;
            this.bands = bands;
            this.preset = preset;
            this.active = active;
        }
    }

    public static synchronized void applySaved(Context context) {
        keeperActive = true;
        Saved saved = load(context);
        if (!saved.enabled) {
            releaseEffects();
            return;
        }
        releaseEffects();
        if (openEffects()) {
            applyEffects(saved);
        }
    }

    public static synchronized void update(Context context, boolean enabled, int gainMb,
            int[] bands, String preset) {
        openEffects();
        Saved saved = new Saved(
                enabled,
                clamp(gainMb, 0, MAX_GAIN_MB),
                normalizePreset(preset),
                fitBands(bands, centerHz.length, minLevelMb, maxLevelMb));
        save(context, saved);
        if (!saved.enabled || !supported) {
            releaseEffects();
            return;
        }
        if (openEffects()) {
            applyEffects(saved);
        }
    }

    /** Kaydırıcı sürüklenirken efekti günceller; tercihlere yazmaz. */
    public static synchronized void preview(Context context, int gainMb, int[] bands) {
        if (!load(context).enabled) {
            return;
        }
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastPreviewApplyMs < 80L) {
            return;
        }
        lastPreviewApplyMs = now;
        if (!openEffects()) {
            return;
        }
        applyEffects(new Saved(
                true,
                clamp(gainMb, 0, MAX_GAIN_MB),
                load(context).preset,
                fitBands(bands, centerHz.length, minLevelMb, maxLevelMb)));
    }

    public static synchronized State read(Context context) {
        Saved saved = load(context);
        boolean opened = openEffects();
        int[] fitted = fitBands(saved.bands, centerHz.length, minLevelMb, maxLevelMb);
        if (opened && fitted.length != saved.bands.length) {
            saved = new Saved(saved.enabled, saved.gainMb, saved.preset, fitted);
            save(context, saved);
        } else if (opened) {
            saved = new Saved(saved.enabled, saved.gainMb, saved.preset, fitted);
        }
        if (!saved.enabled || !opened) {
            releaseEffects();
        } else {
            applyEffects(saved);
        }
        return snapshot(saved);
    }

    public static synchronized void ensureApplied(Context context) {
        if (!keeperActive) {
            return;
        }
        Saved saved = load(context);
        if (!saved.enabled) {
            releaseEffects();
            return;
        }
        if (!effectsMatch(saved)) {
            releaseEffects();
            if (openEffects()) {
                applyEffects(fitSaved(saved));
            }
        }
    }

    public static synchronized void release() {
        keeperActive = false;
        releaseEffects();
    }

    public static int[] bandsForPreset(String preset, int[] centers, int minMb, int maxMb) {
        int count = centers == null ? 0 : centers.length;
        int[] out = new int[count];
        String name = normalizePreset(preset);
        for (int i = 0; i < count; i++) {
            float amount = curveAmount(name, centers[i]);
            int level = Math.round(amount < 0f ? amount * Math.abs(minMb) : amount * maxMb);
            out[i] = clamp(level, minMb, maxMb);
        }
        return out;
    }

    private static float curveAmount(String preset, int hz) {
        if (PRESET_BASS.equals(preset)) {
            if (hz <= 120) {
                return 0.67f;
            }
            if (hz <= 250) {
                return 0.2f;
            }
            return 0f;
        }
        if (PRESET_VOCAL.equals(preset)) {
            if (hz >= 800 && hz <= 4000) {
                return 0.4f;
            }
            if (hz >= 400 && hz < 800) {
                return 0.2f;
            }
            if (hz < 120) {
                return -0.2f;
            }
            return 0f;
        }
        if (PRESET_TREBLE.equals(preset)) {
            if (hz >= 8000) {
                return 0.67f;
            }
            if (hz >= 4000) {
                return 0.27f;
            }
            if (hz <= 150) {
                return -0.13f;
            }
            return 0f;
        }
        return 0f;
    }

    private static boolean openEffects() {
        if (dynamics != null) {
            supported = true;
            return true;
        }
        releaseEffects();
        DynamicsProcessing created = null;
        try {
            DynamicsProcessing.Config.Builder builder = new DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2,
                    false,
                    0,
                    false,
                    0,
                    true,
                    BAND_HZ.length,
                    true);
            builder.setPreferredFrameDuration(FRAME_MS);
            created = new DynamicsProcessing(PRIORITY, SESSION_ID, builder.build());
            DynamicsProcessing.Eq eq = created.getPostEqByChannelIndex(0);
            for (int band = 0; band < BAND_HZ.length; band++) {
                DynamicsProcessing.EqBand stage = eq.getBand(band);
                stage.setCutoffFrequency(BAND_HZ[band]);
                stage.setGain(0f);
            }
            created.setPostEqAllChannelsTo(eq);
            created.setEnabled(false);
            dynamics = created;
            centerHz = BAND_HZ.clone();
            minLevelMb = -BAND_RANGE_MB;
            maxLevelMb = BAND_RANGE_MB;
            supported = true;
            return true;
        } catch (Throwable ignored) {
            if (dynamics != created) {
                releaseQuietly(created);
            }
            releaseEffects();
            supported = false;
            return false;
        }
    }

    private static void applyEffects(Saved saved) {
        if (dynamics == null) {
            return;
        }
        int[] bands = fitBands(saved.bands, centerHz.length, minLevelMb, maxLevelMb);
        float postGainDb = saved.gainMb / 100f;
        float thresholdDb = OUTPUT_CEILING_DB - postGainDb;
        try {
            DynamicsProcessing.Eq eq = dynamics.getPostEqByChannelIndex(0);
            boolean bandsChanged = false;
            for (int band = 0; band < bands.length; band++) {
                float db = bands[band] / 100f;
                DynamicsProcessing.EqBand stage = eq.getBand(band);
                if (Math.abs(stage.getGain() - db) > 0.05f) {
                    stage.setGain(db);
                    bandsChanged = true;
                }
            }
            if (bandsChanged) {
                dynamics.setPostEqAllChannelsTo(eq);
            }
            DynamicsProcessing.Limiter limiter = dynamics.getLimiterByChannelIndex(0);
            if (Math.abs(limiter.getPostGain() - postGainDb) > 0.05f
                    || Math.abs(limiter.getThreshold() - thresholdDb) > 0.05f
                    || Math.abs(limiter.getRatio() - LIMITER_RATIO) > 0.05f) {
                limiter.setEnabled(true);
                limiter.setRatio(LIMITER_RATIO);
                limiter.setAttackTime(LIMITER_ATTACK_MS);
                limiter.setReleaseTime(LIMITER_RELEASE_MS);
                limiter.setThreshold(thresholdDb);
                limiter.setPostGain(postGainDb);
                dynamics.setLimiterAllChannelsTo(limiter);
            }
            boolean active = anyNonZero(bands) || saved.gainMb > 0;
            if (dynamics.getEnabled() != active) {
                dynamics.setEnabled(active);
            }
        } catch (Throwable ignored) {
            releaseEffects();
        }
    }

    private static boolean effectsMatch(Saved saved) {
        if (dynamics == null || centerHz.length == 0) {
            return false;
        }
        int[] bands = fitBands(saved.bands, centerHz.length, minLevelMb, maxLevelMb);
        float postGainDb = saved.gainMb / 100f;
        float thresholdDb = OUTPUT_CEILING_DB - postGainDb;
        try {
            if (dynamics.getEnabled() != (anyNonZero(bands) || saved.gainMb > 0)) {
                return false;
            }
            DynamicsProcessing.Eq eq = dynamics.getPostEqByChannelIndex(0);
            for (int band = 0; band < bands.length; band++) {
                if (Math.abs(eq.getBand(band).getGain() - bands[band] / 100f) > 0.05f) {
                    return false;
                }
            }
            DynamicsProcessing.Limiter limiter = dynamics.getLimiterByChannelIndex(0);
            return Math.abs(limiter.getPostGain() - postGainDb) <= 0.05f
                    && Math.abs(limiter.getThreshold() - thresholdDb) <= 0.05f;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void releaseQuietly(android.media.audiofx.AudioEffect effect) {
        if (effect == null) {
            return;
        }
        try {
            effect.release();
        } catch (Throwable ignored) {
        }
    }

    private static void releaseEffects() {
        if (dynamics == null) {
            return;
        }
        try {
            dynamics.setEnabled(false);
        } catch (Throwable ignored) {
        }
        try {
            dynamics.release();
        } catch (Throwable ignored) {
        }
        dynamics = null;
    }

    private static Saved fitSaved(Saved saved) {
        return new Saved(saved.enabled, saved.gainMb, saved.preset,
                fitBands(saved.bands, centerHz.length, minLevelMb, maxLevelMb));
    }

    private static State snapshot(Saved saved) {
        boolean active = false;
        try {
            active = dynamics != null && dynamics.getEnabled();
        } catch (Throwable ignored) {
        }
        return new State(
                supported && centerHz.length > 0,
                saved.enabled,
                saved.gainMb,
                MAX_GAIN_MB,
                minLevelMb,
                maxLevelMb,
                centerHz.clone(),
                saved.bands.clone(),
                saved.preset,
                active);
    }

    private static boolean anyNonZero(int[] bands) {
        for (int level : bands) {
            if (level != 0) {
                return true;
            }
        }
        return false;
    }

    private static int[] fitBands(int[] bands, int count, int min, int max) {
        int[] fitted = new int[Math.max(0, count)];
        if (bands == null) {
            return fitted;
        }
        for (int i = 0; i < fitted.length && i < bands.length; i++) {
            fitted[i] = clamp(bands[i], min, max);
        }
        return fitted;
    }

    private static int clamp(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

    private static String normalizePreset(String preset) {
        if (PRESET_FLAT.equals(preset) || PRESET_BASS.equals(preset)
                || PRESET_VOCAL.equals(preset) || PRESET_TREBLE.equals(preset)
                || PRESET_CUSTOM.equals(preset)) {
            return preset;
        }
        return PRESET_FLAT;
    }

    private static Saved load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean(KEY_ENABLED, false);
        int gain = clamp(prefs.getInt(KEY_GAIN_MB, 0), 0, MAX_GAIN_MB);
        String preset = normalizePreset(prefs.getString(KEY_PRESET, PRESET_FLAT));
        String raw = prefs.getString(KEY_BANDS, "");
        int[] bands = new int[0];
        if (raw != null && !raw.isEmpty()) {
            String[] parts = raw.split(",");
            bands = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                try {
                    bands[i] = Integer.parseInt(parts[i].trim());
                } catch (NumberFormatException ignored) {
                    bands[i] = 0;
                }
            }
        }
        return new Saved(enabled, gain, preset, bands);
    }

    private static void save(Context context, Saved saved) {
        StringBuilder bands = new StringBuilder();
        for (int i = 0; i < saved.bands.length; i++) {
            if (i > 0) {
                bands.append(',');
            }
            bands.append(saved.bands[i]);
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, saved.enabled)
                .putInt(KEY_GAIN_MB, saved.gainMb)
                .putString(KEY_PRESET, saved.preset)
                .putString(KEY_BANDS, bands.toString())
                .apply();
    }

    private static final class Saved {
        final boolean enabled;
        final int gainMb;
        final String preset;
        final int[] bands;

        Saved(boolean enabled, int gainMb, String preset, int[] bands) {
            this.enabled = enabled;
            this.gainMb = gainMb;
            this.preset = preset;
            this.bands = bands == null ? new int[0] : bands;
        }
    }
}
