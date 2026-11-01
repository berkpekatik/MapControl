package com.mapcontrol.ui;

import com.mapcontrol.ui.builder.TopBarBuilder;

/**
 * Interface for components that display header / system live status chips (Wi-Fi, Sürüş Modu, Telefon, Gösterge).
 */
public interface HeaderStatusSink {
    void setWifiState(TopBarBuilder.WifiState state);
    void setDriveMode(boolean connected, int mode);
    /** App selection is UI state, independently of confirmed vehicle connectivity. */
    default void setSelectedDriveMode(int mode) { setDriveMode(false, mode); }
    void setServerRunning(boolean running);
    void setProjectionActive(boolean active);
    void setSoundBoost(boolean active, int gainMb);
}
