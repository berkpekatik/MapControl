package com.mapcontrol.vehicle;

import com.desaysv.ivi.extra.project.carinfo.CarSettingID;
import com.desaysv.ivi.extra.project.carinfo.NewEnergyID;
import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID;

/**
 * Launcher dashboard ve diğer UI katmanları için anlık araç metrik özeti.
 * Kapı/bagaj: OEM ReadOnly — {@code 1} açık, {@code 0} kapalı.
 * Cam tavan: OEM CarSetting SPF operate/posi.
 * Sürüş modu: OEM NewEnergy {@link NewEnergyID#ID_DRIVE_MODE}.
 */
public final class VehicleMetricsSnapshot {

    public final boolean connected;
    public final int displaySpeed;
    public final int actualSpeed;
    public final int odoKm;
    public final int fuelPercent;
    public final int rangeKm;
    public final int gearSignal;
    public final int powerMode;
    public final int batteryRaw;

    /** Sol ön / sürücü kapısı ham sinyal ({@link VehicleMetricsCache#INVALID} = yok). */
    public final int doorLf;
    /** Sağ ön / yolcu. */
    public final int doorRf;
    /** Sol arka. */
    public final int doorLr;
    /** Sağ arka. */
    public final int doorRr;
    /** Bagaj / hatch. */
    public final int trunk;

    /**
     * Cam tavan operate ({@link CarSettingID#ID_CAR_SPF_OPERATE}).
     * OEM: 2/5 kapalı, 3/6/7 açık, 4 hareket.
     */
    public final int sunroofOperate;
    /**
     * Cam tavan konum ({@link CarSettingID#ID_CAR_SPF_POSI}).
     * OEM: 1 kapalı, 2–11 açık kademe, 12 tilt.
     */
    public final int sunroofPosi;

    /**
     * Sürüş modu ({@link NewEnergyID#ID_DRIVE_MODE}).
     * Eco=0, Normal=1, Sport=2, Snow=3, Mud=4, Offroad=5, Sand=7.
     */
    public final int driveMode;

    public VehicleMetricsSnapshot(
            boolean connected,
            int displaySpeed,
            int actualSpeed,
            int odoKm,
            int fuelPercent,
            int rangeKm,
            int gearSignal,
            int powerMode,
            int batteryRaw,
            int doorLf,
            int doorRf,
            int doorLr,
            int doorRr,
            int trunk,
            int sunroofOperate,
            int sunroofPosi,
            int driveMode) {
        this.connected = connected;
        this.displaySpeed = displaySpeed;
        this.actualSpeed = actualSpeed;
        this.odoKm = odoKm;
        this.fuelPercent = fuelPercent;
        this.rangeKm = rangeKm;
        this.gearSignal = gearSignal;
        this.powerMode = powerMode;
        this.batteryRaw = batteryRaw;
        this.doorLf = doorLf;
        this.doorRf = doorRf;
        this.doorLr = doorLr;
        this.doorRr = doorRr;
        this.trunk = trunk;
        this.sunroofOperate = sunroofOperate;
        this.sunroofPosi = sunroofPosi;
        this.driveMode = driveMode;
    }

    public int preferredSpeed() {
        if (isValid(displaySpeed) && displaySpeed > 0) {
            return displaySpeed;
        }
        if (isValid(actualSpeed) && actualSpeed > 0) {
            return actualSpeed;
        }
        return 0;
    }

    public static boolean isValid(int value) {
        return value != VehicleMetricsCache.INVALID;
    }

    /** OEM kapı/bagaj: 1 = açık. */
    public static boolean isBodyOpen(int rawSignal) {
        return isValid(rawSignal) && rawSignal == 1;
    }

    public boolean hasDoorLf() {
        return isValid(doorLf);
    }

    public boolean hasDoorRf() {
        return isValid(doorRf);
    }

    public boolean hasDoorLr() {
        return isValid(doorLr);
    }

    public boolean hasDoorRr() {
        return isValid(doorRr);
    }

    public boolean hasTrunk() {
        return isValid(trunk);
    }

    public boolean isDoorLfOpen() {
        return isBodyOpen(doorLf);
    }

    public boolean isDoorRfOpen() {
        return isBodyOpen(doorRf);
    }

    public boolean isDoorLrOpen() {
        return isBodyOpen(doorLr);
    }

    public boolean isDoorRrOpen() {
        return isBodyOpen(doorRr);
    }

    public boolean isTrunkOpen() {
        return isBodyOpen(trunk);
    }

    public boolean isAnyDoorOpen() {
        return isDoorLfOpen() || isDoorRfOpen() || isDoorLrOpen() || isDoorRrOpen();
    }

    /**
     * Cam tavan sinyali mevcut mu (operate veya posi).
     * {@code 0} / geçersiz = araçta yok veya henüz gelmedi.
     */
    public boolean hasSunroof() {
        if (isValid(sunroofPosi) && sunroofPosi > 0) {
            return true;
        }
        return isValid(sunroofOperate) && sunroofOperate > 0;
    }

    /**
     * Açık hedef; hareket / bilinmeyen durumda {@code null} (UI güncelleme yok).
     * Öncelik: posi (yüzdelik tavan) → operate (basit tavan).
     */
    public Boolean sunroofOpenOrNull() {
        if (isValid(sunroofPosi) && sunroofPosi > 0) {
            return sunroofPosi != 1;
        }
        if (!isValid(sunroofOperate) || sunroofOperate <= 0) {
            return null;
        }
        if (sunroofOperate == 2 || sunroofOperate == 5) {
            return Boolean.FALSE;
        }
        if (sunroofOperate == 3 || sunroofOperate == 6 || sunroofOperate == 7) {
            return Boolean.TRUE;
        }
        // 4 = hareket halinde — animasyonu zorlama
        return null;
    }

    public boolean isSunroofOpen() {
        Boolean open = sunroofOpenOrNull();
        return open != null && open;
    }

    public boolean hasDriveMode() {
        return isValid(driveMode) && driveMode >= 0;
    }

    public static VehicleMetricsSnapshot empty(boolean connected) {
        int invalid = VehicleMetricsCache.INVALID;
        return new VehicleMetricsSnapshot(
                connected,
                invalid, invalid, invalid, invalid, invalid, invalid, invalid, invalid,
                invalid, invalid, invalid, invalid, invalid,
                invalid, invalid,
                invalid);
    }

    public static VehicleMetricsSnapshot from(VehicleMetricsCache cache, boolean connected) {
        return new VehicleMetricsSnapshot(
                connected,
                cache.getCombined(ReadOnlyID.ID_SPEED_GAUGE_DISPLAY),
                cache.getCombined(ReadOnlyID.ID_CAR_SPEED),
                cache.getCombined(ReadOnlyID.ID_GRAND_TOTAL_KM),
                cache.getCombined(ReadOnlyID.ID_FUEL_PERCENT),
                cache.getCombined(ReadOnlyID.ID_ENDURANCE_KM),
                cache.getCombined(ReadOnlyID.ID_GEARBOX_STATE),
                cache.getCombined(ReadOnlyID.ID_SYSTEM_POWER_MODE),
                cache.getCombined(ReadOnlyID.ID_IBS_VOLTAGE),
                cache.getCombined(ReadOnlyID.ID_DRIVE_DOOR_STATE),
                cache.getCombined(ReadOnlyID.ID_PASSENGER_DOOR_STATE),
                cache.getCombined(ReadOnlyID.ID_LFFT_BEHIND_DOOR_STATE),
                cache.getCombined(ReadOnlyID.ID_RIGHT_BEHIND_DOOR_STATE),
                cache.getCombined(ReadOnlyID.ID_TRUNK_STATE),
                cache.getCombined(VehicleMetricsCache.MODULE_SETTING, CarSettingID.ID_CAR_SPF_OPERATE),
                cache.getCombined(VehicleMetricsCache.MODULE_SETTING, CarSettingID.ID_CAR_SPF_POSI),
                cache.getCombined(VehicleMetricsCache.MODULE_NEW_ENERGY, NewEnergyID.ID_DRIVE_MODE));
    }
}
