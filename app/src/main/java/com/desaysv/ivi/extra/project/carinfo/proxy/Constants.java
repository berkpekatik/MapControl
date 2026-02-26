package com.desaysv.ivi.extra.project.carinfo.proxy;

/* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
public class Constants {
    public static final String CMD_ID = "CMD_ID";
    public static final String CMD_ID_ARRAY = "CMD_ID_ARRAY";
    public static final int TIMER_INTERVAL_PER_TIME = 200;
    public static final String VALUE = "VALUE";

    /* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
    public static class Service {
        public static final int BIND_FAIL = 0;
        public static final int BIND_SUCCESS = 1;
        public static final int UNKNOW = 0;
    }

    /* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
    public class SpiMsgType {
        public static final int MSG_ON_CHANGE_EVENT = 1;
        public static final int MSG_ON_CHANGE_EVENT_IN_TIMING = 2;
        public static final int MSG_ON_SERVICE_CONNECTED = 3;
        public static final int MSG_TIMING_ARRIVAL = 0;

        public SpiMsgType() {
        }
    }
}
