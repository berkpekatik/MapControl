package com.mapcontrol.launcher

/**
 * Aracın açık gövde parçaları için tek doğruluk kaynağı: kapılar, bagaj, kaput, tavan ve (biliniyorsa) camlar. Sahne'deki
 * araç görseli, sahne uyarı satırı, kilit hapı ve şeritteki Bagaj öğesi aynı değeri okur; böylece biri "açık" derken
 * öteki "kapalı" diyemez. [VehicleHeroCard] her okumada günceller ([OpeningsBus]).
 */
internal data class Openings(
    val doorFl: Boolean = false,
    val doorFr: Boolean = false,
    val doorRl: Boolean = false,
    val doorRr: Boolean = false,
    val trunk: Boolean = false,
    val hood: Boolean = false,
    val sunroof: Boolean = false,
    /** Açık camlar (yalnızca cam sinyali okunabiliyorsa). */
    val windows: List<BodyPart> = emptyList(),
    /** Panoramik tavanın konumu (perde, ön panel); sinyal yoksa null. [sunroof] yine "açık" uyarısı için tek kaynaktır. */
    val roof: RoofState? = null,
) {
    val doors: Int get() = listOf(doorFl, doorFr, doorRl, doorRr).count { it }
    val anyDoor: Boolean get() = doors > 0
    val count: Int get() = doors + listOf(trunk, hood, sunroof).count { it } + windows.size
    val any: Boolean get() = count > 0

    /** Sahne aracında açık çizilecek parçalar. */
    fun carParts(): Set<CarPart> = CarPart.fromSignals(doorFl, doorFr, doorRl, doorRr, trunk, hood)

    companion object {
        val NONE = Openings()
    }
}

/** Son açıklık durumu ve dinleyicileri (ana iş parçacığı). null: araç gövdesi henüz okunamadı. */
internal object OpeningsBus {
    var current: Openings? = null
        private set
    private val listeners = LinkedHashSet<(Openings?) -> Unit>()

    fun publish(o: Openings?) {
        if (o == current) return
        current = o
        listeners.toList().forEach { it(o) }
    }

    fun add(l: (Openings?) -> Unit) { listeners += l }
    fun remove(l: (Openings?) -> Unit) { listeners -= l }
}
