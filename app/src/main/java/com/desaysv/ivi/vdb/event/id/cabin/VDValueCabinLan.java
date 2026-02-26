package com.desaysv.ivi.vdb.event.id.cabin;

/* loaded from: classes.dex */
public class VDValueCabinLan {
    public static final String CABIN_ID = "id";
    public static final String CABIN_VALUE = "value";
    public static final String CAMERA_OP = "camera_op";
    public static final String CAMERA_PARAMS = "camera_param";
    public static final String CHECK_SUM = "check_sum";
    public static final String FILE_PATH = "path";
    public static final String MODULE_TYPE = "module_type";
    public static final String MSG_DATA = "msgData";
    public static final String MSG_EXTRA = "extra";
    public static final String MSG_TYPE = "msgType";
    public static final String RESULT_STATUS = "result_status";
    public static final String RESULT_TYPE = "result_type";
    public static final String SUB_TYPE = "subType";
    public static final String TOUCH_H = "touch_height";
    public static final String TOUCH_STATE = "touch_state";
    public static final String TOUCH_W = "touch_width";
    public static final String TOUCH_X = "touch_Xpos";
    public static final String TOUCH_Y = "touch_Ypos";

    public static class CabinLanResult {
        public static final int INVALID_ARGUMENTS = 2;
        public static final int INVALID_STATE = 3;
        public static final int NOT_INITIALIZED = 1;
        public static final int NOT_SUPPORTED = 4;
        public static final int OK = 0;
    }

    public static class Cabin_LAN_MSG_Type {
        public static final int AR_HUD_TYPE_ANDROID2QNX = 4105;
        public static final int AR_HUD_TYPE_QNX2ANDROID = 8201;
        public static final int CABIN_LAN_MSG_TYPE_NULL = 0;
        public static final int CAMERA_TYPE_ANDROID2QNX = 4104;
        public static final int CAMERA_TYPE_QNX2ANDROID = 8200;
        public static final int COMMON_CHANNEL_TYPE_A2Q = 36608;
        public static final int COMMON_CHANNEL_TYPE_Q2A = 65280;
        public static final int DANDER_BEHAVIOR_TYPE_Q2A = 8294;
        public static final int FACE_ENTRY_TYPE_A2Q = 4193;
        public static final int FACE_RECOG_TYPE_A2Q = 4192;
        public static final int FACE_STRUCTURE_TYPE_Q2A = 8291;
        public static final int FACE_TYPE_Q2A = 8289;
        public static final int FACE_VALIDITY_TYPE_Q2A = 8288;
        public static final int HEALTH_INFO_TYPE_Q2A = 8293;
        public static final int HEALTH_VALUE_TYPE_A2Q = 4194;
        public static final int LIVE_INFO_TYPE_Q2A = 8292;
        public static final int MUSIC_TYPE_ANDROID2QNX = 4097;
        public static final int MUSIC_TYPE_QNX2ANDROID = 8193;
        public static final int NAVI_TYPE_ANDROID2QNX = 4100;
        public static final int NAVI_TYPE_QNX2ANDROID = 8196;
        public static final int PHONE_TYPE_ANDROID2QNX = 4099;
        public static final int PHONE_TYPE_QNX2ANDROID = 8195;
        public static final int PRODUCT_DIAG_TYPE_ANDROID2QNX = 4102;
        public static final int PRODUCT_DIAG_TYPE_QNX2ANDROID = 8198;
        public static final int RADIO_TYPE_ANDROID2QNX = 4098;
        public static final int RADIO_TYPE_QNX2ANDROID = 8194;
        public static final int SOUND_TYPE_ANDROID2QNX = 4103;
        public static final int SOUND_TYPE_QNX2ANDROID = 8199;
        public static final int SYSTEM_TYPE_ANDROID2QNX = 4101;
        public static final int SYSTEM_TYPE_QNX2ANDROID = 8197;
    }

    public static class CameraGettingType {
        public static final String GET_ALL = "get-all";
        public static final String GET_BRIGHTNESS = "get-brightness";
        public static final String GET_CONTRAST = "get-contrast";
        public static final String GET_HUE = "get-hue";
        public static final String GET_RVC_SIGNAL = "get-rvc-signal";
        public static final String GET_SATURATION = "get-saturation";
        public static final String GET_VIDEO_SIGNAL = "get-video-signal";
    }

    public static class CameraOp {
        public static final String OP_CLOSE = "op-close";
        public static final String OP_OPEN = "op-open";
        public static final String OP_RESET = "op-reset";
        public static final String OP_RESTART = "op-restart";
        public static final String OP_STARTUP = "op-startup";
    }

    public enum CameraOpParams {
        COP_OPEN_FRONT,
        COP_OPEN_BACK,
        COP_OPEN_LEFT,
        COP_OPEN_RIGHT,
        COP_OPEN_BVS,
        COP_OPEN_BVS_FRONT,
        COP_OPEN_BVS_BACK,
        COP_OPEN_BVS_LEFT,
        COP_OPEN_BVS_RIGHT,
        COP_MAX,
        COP_CLOSE_MAX,
        COP_STARTUP_MAX,
        COP_RESTART_MAX,
        COP_RESET_MAX
    }

    public static class CameraSettingType {
        public static final String SET_ALL = "set-all";
        public static final String SET_BRIGHTNESS = "set-brightness";
        public static final String SET_CONTRAST = "set-contrast";
        public static final String SET_HUE = "set-hue";
        public static final String SET_SATURATION = "set-saturation";
    }

    public static class DisplayId {
        public static final int ID_CLUSTER = 0;
        public static final int ID_HDMI = 4;
        public static final int ID_HUD = 3;
        public static final int ID_HVAC = 2;
        public static final int ID_INVALID = -1;
        public static final int ID_IVI = 1;
        public static final int ID_OTHERS = 5;
    }

    public static class DownloadStatus {
        public static final int DS_FAILED = 1;
        public static final int DS_FAILED_CHECKED = 2;
        public static final int DS_FAILED_PKG_NOT_EXIST = 4;
        public static final int DS_FAILED_SET_FLAG = 3;
        public static final int DS_SUCCESSFUL = 0;
    }

    public static class EventID {
        public static final int CAMERA_AVM_CLICK_EVENT = 1542144;
        public static final int CAMERA_AVM_ICON_PRESS = 1509376;
        public static final int CAMERA_DMS_ICON_PRESS = 1509378;
        public static final int CAMERA_DVR_ICON_PRESS = 1509377;
        public static final int CAMERA_GET_RVC_SIGNAL = 1509379;
        public static final int CAMERA_GET_VIDEO_SIGNAL = 1509380;
        public static final int CAMERA_RVC_SIGNAL_CHANGE = 1542145;
        public static final int CAMERA_VIDEO_SIGNAL_CHANGE = 1542146;
        public static final int HMI_CONTROL = 1966080;
        public static final int HMI_STATUS = 1998848;
        public static final int MUSIC_CONTROL_MEDIA = 1540352;
        public static final int MUSIC_FAVOURITE = 1507588;
        public static final int MUSIC_GET_PLAYING_LIST = 1540353;
        public static final int MUSIC_ID3_INFO = 1507584;
        public static final int MUSIC_LOADING_STATUS = 1507592;
        public static final int MUSIC_PLAYING_ACTION = 1507586;
        public static final int MUSIC_PLAYING_LIST = 1507589;
        public static final int MUSIC_PLAYING_LYRIC = 1507591;
        public static final int MUSIC_PLAYING_MODE = 1507587;
        public static final int MUSIC_PLAYING_STATUS = 1507585;
        public static final int MUSIC_PLAYING_TIME = 1507590;
        public static final int NAVI_AREADISPLAY = 1541121;
        public static final int NAVI_COMPASS = 1508358;
        public static final int NAVI_CROSSING_INFO = 1508353;
        public static final int NAVI_DISPLAY_AREA = 1508357;
        public static final int NAVI_DISPLAY_SETTINGS = 1508354;
        public static final int NAVI_PERSPECTIVE = 1541120;
        public static final int NAVI_ROADCAM_INFO = 1508356;
        public static final int NAVI_ROAD_INFO = 1508352;
        public static final int NAVI_TRIP_SUMMARY = 1508355;
        public static final int PDG_AVM_DIAG_INFO = 1934853;
        public static final int PDG_CLUSTER_DISPLAY_DIAG_INFO = 1934848;
        public static final int PDG_CLUSTER_DISPLAY_DIAG_INFO_EX = 1934851;
        public static final int PDG_DMS_CAMERA_DIAG_INFO = 1934850;
        public static final int PDG_GET_AVM_DIAG_INFO = 1902083;
        public static final int PDG_GET_CLUSTER_DISPLAY_DIAG_INFO = 1902080;
        public static final int PDG_GET_DMS_CAMERA_DIAG_INFO = 1902082;
        public static final int PDG_GET_IVI_DISPLAY_DIAG_INFO = 1902081;
        public static final int PDG_IVI_DISPLAY_DIAG_INFO = 1934849;
        public static final int PDG_IVI_DISPLAY_DIAG_INFO_EX = 1934852;
        public static final int PDG_START_LCD_TEST = 1902084;
        public static final int PDG_START_USB_3_0_TEST = 1902085;
        public static final int PDG_USB_3_0_TEST = 1934854;
        public static final int PHONE_CALLLOG = 1508096;
        public static final int PHONE_CALLLOG_SYNCSTATE = 1508097;
        public static final int PHONE_CALL_DURATION = 1508099;
        public static final int PHONE_CALL_DURATION_2 = 1508101;
        public static final int PHONE_CALL_STATUS = 1508098;
        public static final int PHONE_CALL_STATUS_2 = 1508100;
        public static final int PHONE_CONTROL_CALL = 1540864;
        public static final int PHONE_GET_CALLLOG_LIST = 1540865;
        public static final int RADIO_AST_STATE = 1507841;
        public static final int RADIO_COVER_PATH = 1507843;
        public static final int RADIO_FAST_TUNER = 1507840;
        public static final int RADIO_GET_COVER_PATH = 1540611;
        public static final int RADIO_GET_PLAYING_STATUS = 1540613;
        public static final int RADIO_GET_RADIO_LIST = 1540610;
        public static final int RADIO_GET_SIGNAL_STATUS = 1540612;
        public static final int RADIO_LIST = 1507842;
        public static final int RADIO_PLAYING_STATUS = 1507845;
        public static final int RADIO_SET_BAND = 1540608;
        public static final int RADIO_SET_CHANNEL = 1540609;
        public static final int RADIO_SIGNAL_STATUS = 1507844;
        public static final int SYSTEM_AVMFILE_OPERATION_RESULT = 1541388;
        public static final int SYSTEM_BOOTLOADER_VERSION = 1541381;
        public static final int SYSTEM_CAR_LOCATIONSHOOTING = 1541382;
        public static final int SYSTEM_CONTROL_ENGINEERING_MODE = 1508624;
        public static final int SYSTEM_DSP_VERSION = 1541387;
        public static final int SYSTEM_ENTER_RECOVERY_MODE = 1508610;
        public static final int SYSTEM_EXCETION_COUNT = 1541379;
        public static final int SYSTEM_FACTORYRESET_RESULT = 1541389;
        public static final int SYSTEM_GET_AVMFILE_OPERATION_RESULT = 1508622;
        public static final int SYSTEM_GET_BOOTLOADER_VERSION = 1508615;
        public static final int SYSTEM_GET_CAR_LOCATIONSHOOTING = 1508616;
        public static final int SYSTEM_GET_DSP_VERSION = 1508621;
        public static final int SYSTEM_GET_FACTORYRESET_RESULT = 1508623;
        public static final int SYSTEM_GET_IVILOGO_STATUS = 1508614;
        public static final int SYSTEM_GET_QNX_RUNINFO = 1508612;
        public static final int SYSTEM_GET_QNX_VERSIONINFO = 1508613;
        public static final int SYSTEM_GET_SOC_TEMPRATURE = 1508617;
        public static final int SYSTEM_HEARTBEAT = 1508609;
        public static final int SYSTEM_HMI_STARTED = 1508611;
        public static final int SYSTEM_IVILOGO_STATUS = 1541378;
        public static final int SYSTEM_PARTITIONSWITCH_RESULT = 1541384;
        public static final int SYSTEM_PHY_STARTED = 1508619;
        public static final int SYSTEM_PHY_VERSION = 1541385;
        public static final int SYSTEM_QNX_UPLOAD_RESULT = 1541376;
        public static final int SYSTEM_QNX_VERSION = 1541377;
        public static final int SYSTEM_RESTART_ANDROID = 1541380;
        public static final int SYSTEM_SET_DISPLAYER = 1508620;
        public static final int SYSTEM_SET_DISPLAY_RESULT = 1541386;
        public static final int SYSTEM_SOC_TEMPERATURE = 1541383;
        public static final int SYSTEM_SOC_UPGRADE_COMPLETED = 1508618;
        public static final int SYSTEM_STARTED = 1508608;
        public static final int UPGRADE_APPLY_UPGRADE = 1572865;
        public static final int UPGRADE_DOWNLOADING_STATUS = 1605632;
        public static final int UPGRADE_DOWNLOAD_PACKAGE = 1572864;
        public static final int UPGRADE_GET_DOWNLOAD_STATUS = 1572866;
        public static final int UPGRADE_GET_UPGRADE_STATUS = 1572867;
        public static final int UPGRADE_UPGRADING_STATUS = 1605633;
    }

    public static class FactoryResetStatus {
        public static final int FACTORY_RESET_FAILED = 1;
        public static final int FACTORY_RESET_START = 2;
        public static final int FACTORY_RESET_SUCCESSFUL = 0;
    }

    public static class RVCStatusMsgType {
        public static final int MSG_RVC_STATUS_CHANGE = 1;
        public static final int MSG_VIDEO_STATUS_CHANGE = 2;
    }

    public static class RequesterId {
        public static final int REQ_ANDROID = 1;
        public static final int REQ_INVALID = -1;
        public static final int REQ_OTHERS = 2;
        public static final int REQ_QNX = 0;
    }

    public static class UpdateMessage {
        public static final int MSG_COPY_PROGRESS_CHANGE = 0;
        public static final int MSG_COPY_STATUS_CHANGE = 1;
        public static final int MSG_FACTORY_RESET_STATUS_CHANGE = 4;
        public static final int MSG_UPDATE_PROGRESS_CHANGE = 2;
        public static final int MSG_UPDATE_STATUS_CHANGE = 3;
    }

    public static class UpdateModules {
        public static final int MODULE_ANDROID = 1;
        public static final int MODULE_ARHUD = 128;
        public static final int MODULE_AVM = 32;
        public static final int MODULE_CHIME = 16;
        public static final int MODULE_DISPLAY = 64;
        public static final int MODULE_DSP = 8;
        public static final int MODULE_INVALID = 0;
        public static final int MODULE_MCU = 4;
        public static final int MODULE_OTHERS = 32768;
        public static final int MODULE_QNX = 2;
        public static final int MODULE_USS = 256;
    }

    public static class UpdateStatus {
        public static final int UPDATE_FAILED = 4;
        public static final int UPDATE_FAILED_PKG_INVALID = 2;
        public static final int UPDATE_FAILED_PKG_NOT_EXIST = 1;
        public static final int UPDATE_FAILED_RMT_UPGRADE_DSP = 3;
        public static final int UPDATE_SUCCESSFUL = 0;
    }
}
