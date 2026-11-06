package com.mapcontrol.gps;

/** Bağlantının araç tarafı: telefonun konumlarını alır ve Android'e iletir. */
interface LinkEngine {

    /** Devam edemeyen bir motorun tüm bağlantıyı durdurmasını sağlar. */
    interface Host {
        void onFatal(LinkStatus reason);
    }

    void start();

    void stop();
}
