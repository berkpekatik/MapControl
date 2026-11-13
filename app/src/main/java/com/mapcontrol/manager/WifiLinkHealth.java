package com.mapcontrol.manager;

/**
 * Wi-Fi istemci bağlantısının sağlık kararı (Android'e bağımlı değil, birim testli). Bazı araçlarda telefon hotspot'u
 * kapanıp açılınca Wi-Fi "bağlı" görünmeye devam eder ama ağ geçidine (telefona) tek paket gitmez: telefon aracı
 * bulamaz, eşleşme olmaz. {@link WifiLinkWatchdog} her bekçi turunda ağ geçidini yoklar, sonucu buraya verir;
 * dönen {@link Action} uygulanır.
 *
 * <p>Arka arkaya {@link #FAILS_BEFORE_ACTION} yoklama başarısız olunca kademeli onarım: önce yeniden ilişkilenme,
 * sonra kes-bağlan, sonra Wi-Fi kapat-aç. Her adımdan sonra {@link #SETTLE_MS} beklenir; kapat-aç tekrarları üstel
 * beklemeyle en fazla {@link #TOGGLE_BACKOFF_MAX_MS}'de bir yapılır. Başarılı yoklama her şeyi sıfırlar; onarım
 * sonrası ilk başarıda bir kez {@link Action#RECOVERED} döner (mDNS/QR yenilensin).
 *
 * <p>Bağlantı anahtarı (arayüz + ağ geçidi) değişince durum sıfırlanır: başka bir ağa geçildi demektir. Kapat-aç
 * aynı anahtarı korur, böylece düzelmeyen bağlantıda bekleme süresi büyümeye devam eder.
 */
final class WifiLinkHealth {

    enum Action {
        NONE,
        /** Sürücüden yeniden ilişkilenme iste ({@code WifiManager.reassociate}). */
        REASSOCIATE,
        /** Bağlantıyı kes ve yeniden bağlan ({@code disconnect} + {@code reconnect}). */
        RECONNECT,
        /** Wi-Fi'yi kapatıp aç. */
        TOGGLE,
        /** Onarımdan sonra bağlantı geri geldi: yayın ve adres yenilenmeli. */
        RECOVERED
    }

    static final int FAILS_BEFORE_ACTION = 2;
    static final long SETTLE_MS = 45_000L;
    static final long TOGGLE_BACKOFF_MAX_MS = 10 * 60_000L;

    private String linkKey;
    private int fails;
    private int step;
    private long quietUntil;
    private long toggleBackoffMs = SETTLE_MS;

    /**
     * @param key   bağlantı anahtarı (arayüz + ağ geçidi); null = Wi-Fi istemci bağlantısı yok, durum sıfırlanır
     * @param alive ağ geçidine ulaşıldı mı
     * @param now   monoton saat (ms)
     */
    synchronized Action onProbe(String key, boolean alive, long now) {
        if (key == null) {
            linkKey = null;
            reset();
            return Action.NONE;
        }
        if (!key.equals(linkKey)) {
            linkKey = key;
            reset();
        }
        if (alive) {
            boolean repaired = step > 0;
            reset();
            return repaired ? Action.RECOVERED : Action.NONE;
        }
        fails++;
        if (fails < FAILS_BEFORE_ACTION || now < quietUntil) {
            return Action.NONE;
        }
        Action action;
        if (step == 0) {
            action = Action.REASSOCIATE;
            quietUntil = now + SETTLE_MS;
        } else if (step == 1) {
            action = Action.RECONNECT;
            quietUntil = now + SETTLE_MS;
        } else {
            action = Action.TOGGLE;
            quietUntil = now + toggleBackoffMs;
            toggleBackoffMs = Math.min(toggleBackoffMs * 2, TOGGLE_BACKOFF_MAX_MS);
        }
        step++;
        return action;
    }

    private void reset() {
        fails = 0;
        step = 0;
        quietUntil = 0;
        toggleBackoffMs = SETTLE_MS;
    }
}
