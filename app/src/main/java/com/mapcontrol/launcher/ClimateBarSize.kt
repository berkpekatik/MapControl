package com.mapcontrol.launcher

/**
 * Alt çubuktaki klima hücrelerinin ölçüleri (dp/sp; saf, JVM'de sınanır).
 * Tiggo 7/8 tam ölçü. Dikey ekranda (~1275 dp genişlik) hava düğmeleri açıkken fan ve düğmeler daralır; sıcaklık hücresi
 * de ~155 dp'ye iner, bu yüzden rakam küçülür ve oklar kenara yaklaşır ki "24,5" oklara binmesin (emülatörde görüldü).
 */
internal data class ClimateBarSize(
    val fanDp: Int,
    val fanTrackDp: Int,
    val airButtonDp: Int,
    val tempSp: Float,
    val chevronEdgeDp: Int,
) {
    companion object {
        val FULL = ClimateBarSize(
            fanDp = 330,
            fanTrackDp = ClimateControl.SAHNE_TRACK_W_DP,
            airButtonDp = 64,
            tempSp = ClimateControl.SAHNE_TEMP_SP,
            chevronEdgeDp = ClimateControl.SAHNE_CHEVRON_EDGE_DP,
        )
        val COMPACT = ClimateBarSize(fanDp = 180, fanTrackDp = 120, airButtonDp = 56, tempSp = 34f, chevronEdgeDp = 4)

        fun forBar(profile: VehicleProfile, airButtons: Boolean): ClimateBarSize =
            if (profile == VehicleProfile.PORTRAIT && airButtons) COMPACT else FULL
    }
}
