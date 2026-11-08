/*
 * VDBus BT sabitleri (araçla konuşmak için); değerler servisle aynı olmalı, değiştirilmemeli.
 */
package com.desaysv.ivi.vdb.event.id.bt;

public class VDValueBT {

    public static class AudioMode {
        public static final int CAR = 1;
        public static final int CAR_CONNECTING = 3;
        public static final int PHONE = 2;
    }

    public static class ContactsNumberType {
        public static final int ASSISTANT = 19;
        public static final int CALLBACK = 8;
        public static final int CAR = 9;
        public static final int COMPANY_MAIN = 4;
        public static final int HOME = 2;
        public static final int HOME_FAX = 6;
        public static final int ISDN = 11;
        public static final int MAIN = 12;
        public static final int MMS = 10;
        public static final int OTHER = 100;
        public static final int OTHER_FAX = 13;
        public static final int PAGER = 7;
        public static final int PHONE = 1;
        public static final int RADIO = 14;
        public static final int TELEX = 15;
        public static final int TTY_TDD = 16;
        public static final int UNIT = 3;
        public static final int UNIT_FAX = 5;
        public static final int WORK_MOBILE = 17;
        public static final int WORK_PAGER = 18;
    }

    public static class DeviceType {
        public static final int CLASSIC = 1;
        public static final int DUAL = 3;
        public static final int LE = 2;
        public static final int UNKNOWN = 0;
    }

    public static class DialCtrl {
        public static final int ACCEPT = 2;
        public static final int DIAL = 1;
        public static final int DTMF = 4;
        public static final int HOLD = 5;
        public static final int HUNG_UP = 3;
        public static final int REDIAL = 6;
    }

    public static class DialStatus {
        public static final int ACTIVE = 0;
        public static final int ALERTING = 3;
        public static final int DIALING = 2;
        public static final int HELD = 1;
        public static final int HELD_BY_RESPONSE_AND_HOLD = 6;
        public static final int INCOMING = 4;
        public static final int MISSED = 8;
        public static final int TERMINATED = 7;
        public static final int WAITING = 5;
    }

    public static class DiscoverableCtrl {
        public static final int CONNECTABLE = 2;
        public static final int CONNECTABLE_DISCOVERABLE = 3;
        public static final int NONE = 1;
    }

    public static class DiscoverableStatus {
        public static final int CONNECTABLE = 2;
        public static final int CONNECTABLE_DISCOVERABLE = 3;
        public static final int ERROR = 100;
        public static final int NONE = 1;
    }

    public static class LatestDialInfoType {
        public static final int CALLLOG_ONLY = 1;
        public static final int DEFAULT = 0;
    }

    public static class MessageSyncStatus {
        public static final int COMPLETE = 3;
        public static final int DOWNLOADING = 2;
        public static final int ERROR_NONE = 8;
        public static final int ERROR_NOT_ACCEPTEABLE = 7;
        public static final int ERROR_TIMEOUT = 6;
        public static final int IDLE = 1;
        public static final int NOT_SUPPORT = 4;
        public static final int STOP = 5;
    }

    public static class MessageSyncType {
        public static final int DELETED = 4;
        public static final int DRAFT = 5;
        public static final int INBOX = 1;
        public static final int OUTBOX = 2;
        public static final int SENT = 3;
    }

    public static class MicMode {
        public static final int MUTE = 2;
        public static final int UNMUTE = 1;
    }

    public static class MusicListSyncType {
        public static final int DEFAULT = 0;
    }

    public static class MusicMuteMode {
        public static final int MUTE = 2;
        public static final int UNMUTE = 1;
    }

    public static class MusicPlayCtrl {
        public static final int FORWARD_END = 7;
        public static final int FORWARD_START = 6;
        public static final int NEXT = 5;
        public static final int PAUSE = 2;
        public static final int PLAY = 1;
        public static final int PREVIOUS = 4;
        public static final int REWIND_END = 9;
        public static final int REWIND_START = 8;
        public static final int STOP = 3;
    }

    public static class MusicPlayStatus {
        public static final int BUFFERING = 6;
        public static final int CONNECTING = 8;
        public static final int ERROR = 7;
        public static final int FAST_FORWARDING = 4;
        public static final int NONE = 0;
        public static final int PAUSED = 2;
        public static final int PLAYING = 3;
        public static final int REWINDING = 5;
        public static final int SKIPPING_TO_NEXT = 10;
        public static final int SKIPPING_TO_PREVIOUS = 9;
        public static final int STOPPED = 1;
    }

    public static class MusicPlayType {
        public static final int DEFAULT = 0;
        public static final int FROM_MEDIA_ID = 1;
        public static final int FROM_SEARCH = 2;
        public static final int FROM_URI = 3;
    }

    public static class PairCtrl {
        public static final int CANCEL = 3;
        public static final int CREATE = 1;
        public static final int PIN_CONFIRM = 5;
        public static final int REJECT = 4;
        public static final int REMOVE = 2;
    }

    public static class PairStatus {
        public static final int ERROR = 100;
        public static final int PAIRED = 4;
        public static final int PAIRING = 2;
        public static final int PIN_CONFIRM = 3;
        public static final int UNPAIRED = 1;
    }

    public static class PhoneBookSyncType {
        public static final int CALLLOG_ICH = 3;
        public static final int CALLLOG_MCH = 5;
        public static final int CALLLOG_OCH = 4;
        public static final int CALLLOG_SYNC = 6;
        public static final int CALLLOG_SYNC_COMBINE = 8;
        public static final int CONTACT_CALLLOG_SYNC = 50;
        public static final int CONTACT_SIM = 2;
        public static final int CONTACT_SYNC = 7;
        public static final int TELECOM_PB = 1;
    }

    public static class ProfileType {
        public static final int A2DP_SINK = 11;
        public static final int AVRCP_CONTROLLER = 12;
        public static final int HEADSET_CLIENT = 16;
        public static final int MAP_CLIENT = 18;
        public static final int PBAP_CLIENT = 17;
    }

    public static class RecordType {
        public static final int ACCEPTED = 2;
        public static final int CALLED = 3;
        public static final int MISSED = 1;
    }

    public static class SyncCtrl {
        public static final int CANCEL = 2;
        public static final int START = 1;
    }

    public static class SyncStatus {
        public static final int ERROR = 6;
        public static final int FINISHED = 1;
        public static final int IDLE = 20;
        public static final int NONE = 7;
        public static final int NOT_ACCEPTEABLE = 5;
        public static final int NOT_SUPPORT = 2;
        public static final int REQUEST_TIMEOUT = 4;
        public static final int START = 0;
        public static final int STOP = 3;
        public static final int WAITING = 21;
        public static final int WRITE_DATABASE_COMPLETE = 8;
        public static final int WRITING_DATABASE = 9;
    }

    public class RedialType {
        public static final int TYPE_REDIAL_CALLLOG_DEFAULT = 5;
        public static final int TYPE_REDIAL_CALLLOG_INCOMING = 7;
        public static final int TYPE_REDIAL_CALLLOG_INCOMING_MISSED = 9;
        public static final int TYPE_REDIAL_CALLLOG_MISSED = 8;
        public static final int TYPE_REDIAL_CALLLOG_OUTGOING = 6;
        public static final int TYPE_REDIAL_DEFAULT = 0;
        public static final int TYPE_REDIAL_INCOMING = 2;
        public static final int TYPE_REDIAL_INCOMING_MISSED = 4;
        public static final int TYPE_REDIAL_MISSED = 3;
        public static final int TYPE_REDIAL_OUTGOING = 1;

        public RedialType() {
        }
    }

    public class AcceptCallType {
        public static final int HOLD = 1;
        public static final int NONE = 0;
        public static final int TERMINATE = 2;

        public AcceptCallType() {
        }
    }

    public class PlayerSettingsType {
        public static final int SETTING_EQUALIZER = 1;
        public static final int SETTING_REPEAT = 2;
        public static final int SETTING_SCAN = 8;
        public static final int SETTING_SHUFFLE = 4;

        public PlayerSettingsType() {
        }
    }

    public class PlayerSettingsState {
        public static final int STATE_ALL_TRACK = 3;
        public static final int STATE_GROUP = 4;
        public static final int STATE_INVALID = -1;
        public static final int STATE_OFF = 0;
        public static final int STATE_ON = 1;
        public static final int STATE_SINGLE_TRACK = 2;

        public PlayerSettingsState() {
        }
    }
}
