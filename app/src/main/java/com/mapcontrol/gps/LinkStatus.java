package com.mapcontrol.gps;

import com.mapcontrol.R;

/** Aktar > Telefon eşleme sayfasındaki GPS Link durumu. Sticky olanlar başlatma denemesinin neden başarısız olduğunu söyler. */
public enum LinkStatus {
    READY(Tone.INFO, R.string.gps_status_ready, R.string.gps_status_ready_hint, false),
    WAITING_PHONE(Tone.WARN, R.string.gps_status_waiting_phone, R.string.gps_status_waiting_phone_hint, false),
    LIVE(Tone.OK, R.string.gps_status_live, R.string.gps_status_live_hint, false),
    LOST(Tone.ERROR, R.string.gps_status_lost, R.string.gps_status_lost_hint, false),
    NEEDS_MOCK(Tone.ERROR, R.string.gps_status_needs_mock, R.string.gps_status_needs_mock_hint, true),
    LOCATION_OFF(Tone.ERROR, R.string.gps_status_location_off, R.string.gps_status_location_off_hint, true),
    NETWORK_ERROR(Tone.ERROR, R.string.gps_status_network_error, R.string.gps_status_network_error_hint, true);

    public enum Tone { INFO, OK, WARN, ERROR }

    public final Tone tone;
    public final int labelRes;
    public final int hintRes;
    final boolean sticky;

    LinkStatus(Tone tone, int labelRes, int hintRes, boolean sticky) {
        this.tone = tone;
        this.labelRes = labelRes;
        this.hintRes = hintRes;
        this.sticky = sticky;
    }

    /** {@code /vehicle/now} içindeki {@code gps.state} değeri. */
    public String wireName() {
        switch (this) {
            case LIVE:
                return "live";
            case WAITING_PHONE:
                return "waiting";
            case LOST:
                return "lost";
            case READY:
                return "off";
            default:
                return "blocked";
        }
    }
}
