package com.mapcontrol.nav;

import androidx.annotation.Nullable;

/**
 * Eksik erişilebilirlik karelerinde son dolu manevra ve ETA alanlarını kısa süre tutar.
 * Yandex ağacı animasyon veya arka plan sırasında kartları sırayla boşaltabiliyor;
 * tek boş okuma kartı söndürmesin.
 */
public final class YandexNavStability {

    /** Alan bir kez doluysa, ardışık boş okumalarda bu kadar yaşar. */
    static final long FIELD_HOLD_MS = 3000L;

    private final Slot maneuverDistance = new Slot();
    private final Slot maneuverUnit = new Slot();
    private final Slot nextStreet = new Slot();
    private final Slot etaDistance = new Slot();
    private final Slot etaArrival = new Slot();
    private final Slot etaTime = new Slot();
    private final Slot statusPanel = new Slot();

    public void clear() {
        maneuverDistance.clear();
        maneuverUnit.clear();
        nextStreet.clear();
        etaDistance.clear();
        etaArrival.clear();
        etaTime.clear();
        statusPanel.clear();
    }

    public YandexNavSnapshot accept(@Nullable YandexNavSnapshot fresh, long nowMs) {
        String inDistance = fresh == null ? null : fresh.maneuverDistance;
        String inUnit = fresh == null ? null : fresh.maneuverUnit;
        // ETA hâlâ geliyorsa manevra balonu ayrı pencerede kaybolmuş sayılmaz.
        // "30 m" kartı, ETA dururken 3 sn sonra silinmesin.
        if (fresh != null && fresh.hasEta()) {
            maneuverDistance.touch(nowMs);
            maneuverUnit.touch(nowMs);
            nextStreet.touch(nowMs);
        }
        String distance = maneuverDistance.get(inDistance, nowMs);
        String unit;
        if (inUnit != null && !inUnit.isEmpty()) {
            unit = maneuverUnit.get(inUnit, nowMs);
        } else if (distance != null) {
            unit = maneuverUnit.value;
        } else {
            maneuverUnit.clear();
            unit = null;
        }

        String street = nextStreet.get(fresh == null ? null : fresh.nextStreet, nowMs);
        String etaDist = etaDistance.get(fresh == null ? null : fresh.etaDistance, nowMs);
        String arrival = etaArrival.get(fresh == null ? null : fresh.etaArrival, nowMs);
        String eta = etaTime.get(fresh == null ? null : fresh.etaTime, nowMs);
        String status = statusPanel.get(fresh == null ? null : fresh.statusPanel, nowMs);

        boolean cards = distance != null
                || street != null
                || status != null
                || etaDist != null
                || arrival != null
                || eta != null;
        if (!cards) {
            clear();
            return YandexNavSnapshot.inactive();
        }
        return new YandexNavSnapshot(
                true,
                distance,
                unit,
                street,
                etaDist,
                arrival,
                eta,
                fresh == null ? null : fresh.speed,
                fresh == null ? null : fresh.speedLimit,
                status);
    }

    private static final class Slot {
        @Nullable String value;
        private long at;

        @Nullable
        String get(@Nullable String incoming, long nowMs) {
            if (incoming != null && !incoming.isEmpty()) {
                value = incoming;
                at = nowMs;
                return value;
            }
            if (value != null && nowMs - at <= FIELD_HOLD_MS) {
                return value;
            }
            value = null;
            return null;
        }

        void touch(long nowMs) {
            if (value != null) {
                at = nowMs;
            }
        }

        void clear() {
            value = null;
            at = 0L;
        }
    }
}
