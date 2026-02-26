package com.desaysv.ivi.vdb.event.base;

import android.os.Bundle;
import com.desaysv.ivi.vdb.event.VDEvent;

/* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
public class VDValue {
    public static final boolean INVALID_BOOLEAN = false;
    public static final double INVALID_DOUBLE = -10000.0d;
    public static final float INVALID_FLOAT = -10000.0f;
    public static final int INVALID_INT = -10000;
    public static final long INVALID_LONG = -10000;
    public static final Object INVALID_NULL = null;

    /* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
    public static class ConnectCtrl {
        public static final int CONNECT = 1;
        public static final int DISCONNECT = 2;
    }

    /* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
    public static class ConnectStatus {
        public static final int CONNECTED = 2;
        public static final int CONNECTING = 4;
        public static final int DISCONNECTED = 1;
        public static final int DISCONNECTING = 3;
        public static final int ERROR = 100;
    }

    /* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
    public static class EnableCtrl {
        public static final int DISABLE = 1;
        public static final int ENABLE = 2;
    }

    /* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
    public static class EnableStatus {
        public static final int CLOSING = 3;
        public static final int DISABLED = 1;
        public static final int ENABLED = 2;
        public static final int ERROR = 100;
        public static final int OPENING = 4;
    }

    /* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
    public static class SearchCtrl {
        public static final int START = 1;
        public static final int STOP = 2;
    }

    /* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
    public static class SearchStatus {
        public static final int ERROR = 100;
        public static final int FINISHED = 3;
        public static final int IDLE = 1;
        public static final int SEARCHING = 2;
    }

    /* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
    public static class Signal {
        public static final int ERROR = 100;
        public static final int HAS_SIGNAL = 2;
        public static final int NO_SIGNAL = 1;
    }

    public static final VDEvent getNullEvent() {
        return new VDEvent(0, (Bundle) null);
    }

    public static final boolean isNullEvent(VDEvent vDEvent) {
        if (vDEvent == null || vDEvent.getId() == 0) {
            return true;
        }
        return false;
    }
}
