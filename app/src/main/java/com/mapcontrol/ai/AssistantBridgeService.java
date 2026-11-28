package com.mapcontrol.ai;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import com.mapcontrol.api.CloudToken;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Sesli asistan uygulamasına ({@code com.mapcontrol.ai}) açılan köprü (Messenger). Yalnızca aynı anahtarla imzalı uygulama
 * bağlanır: servis {@code signature} düzeyli {@link #PERMISSION} ister ve her mesajda gönderenin imzası ayrıca denetlenir.
 *
 * <p>Protokol (istek {@code what}, yanıt aynı {@code what} ve {@code arg1} = istek kimliği, {@code replyTo}'ya):
 * <ul>
 *   <li>{@link #MSG_SESSION}: telefon eşleşmesinden gelen araç token'ı. Yanıt {@code token}, {@code vin},
 *       {@code expiresAtMs}; token yoksa boş Bundle (asistan üye girişi ister).</li>
 *   <li>{@link #MSG_TOOL}: istek {@code name}, {@code args} (JSON metin). Yanıt {@code result} (kısa metin).</li>
 *   <li>{@link #MSG_STOCK_ASSISTANT}: aracın kendi asistanını kapat/aç ({@link StockVoiceAssistant}).</li>
 *   <li>{@link #MSG_CONTEXT}: yanıt {@code apps} (yüklü uygulama adları), {@code telemetry} (istekte
 *       {@code withTelemetry=true} ise).</li>
 * </ul>
 * Hata durumunda yanıtta {@code error} vardır. Komutlar tek işçi iş parçacığında sırayla çalışır.
 */
public final class AssistantBridgeService extends Service {
    public static final String ACTION = "com.mapcontrol.ai.BIND_ASSISTANT";
    public static final String PERMISSION = "com.mapcontrol.permission.ASSISTANT";
    public static final int MSG_SESSION = 1;
    public static final int MSG_TOOL = 2;
    public static final int MSG_CONTEXT = 3;
    /** Aracın kendi asistanı (Chery): istekte {@code set} (boolean) varsa uygular. Yanıt {@code disabled}, {@code owner},
     * {@code failed} (değiştirilemeyen paketler). */
    public static final int MSG_STOCK_ASSISTANT = 4;

    private static final String TAG = "AssistantBridge";
    /** Asistanın yaptırabileceği komutlar: sunucudaki listeyle aynı. */
    static final java.util.Set<String> TOOLS = new java.util.HashSet<>(Arrays.asList(
            "control_sunroof", "set_child_lock", "set_drive_mode", "set_seat_memory", "get_vehicle_telemetry",
            "get_vehicle_metrics", "get_weather", "launch_split_screen", "launch_app", "control_window",
            "control_climate", "control_seat_climate", "control_steering_wheel_heat", "start_navigation",
            "control_cluster", "control_dvr", "control_ambient_light", "control_avm_camera", "control_trunk",
            "control_defrost", "control_special_climate", "play_media", "control_media", "search_nearby_places"));

    private HandlerThread worker;
    private Messenger messenger;
    private AssistantToolDispatcher dispatcher;

    @Override
    public void onCreate() {
        super.onCreate();
        worker = new HandlerThread("mapcontrol-assistant");
        worker.start();
        dispatcher = new AssistantToolDispatcher(this);
        messenger = new Messenger(new IncomingHandler(worker.getLooper()));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    @Override
    public void onDestroy() {
        if (worker != null) {
            worker.quitSafely();
        }
        super.onDestroy();
    }

    private boolean trusted(int uid) {
        if (uid == Process.myUid()) {
            return true;
        }
        return getPackageManager().checkSignatures(Process.myUid(), uid) == PackageManager.SIGNATURE_MATCH;
    }

    private final class IncomingHandler extends Handler {
        IncomingHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            Messenger reply = msg.replyTo;
            if (reply == null) {
                return;
            }
            Bundle out = new Bundle();
            if (!trusted(msg.sendingUid)) {
                Log.w(TAG, "imzası eşleşmeyen çağrı reddedildi, uid " + msg.sendingUid);
                out.putString("error", "untrusted");
            } else {
                try {
                    handle(msg.what, msg.getData(), out);
                } catch (Throwable t) {
                    Log.w(TAG, "asistan isteği başarısız: " + msg.what, t);
                    out.putString("error", t.getClass().getSimpleName());
                }
            }
            Message response = Message.obtain(null, msg.what, msg.arg1, 0);
            response.setData(out);
            try {
                reply.send(response);
            } catch (RemoteException ignored) {
                // Asistan kapandı.
            }
        }
    }

    private void handle(int what, Bundle in, Bundle out) throws Exception {
        switch (what) {
            case MSG_SESSION: {
                String token = CloudToken.bearer(this, null);
                if (token != null) {
                    out.putString("token", token);
                    out.putString("vin", CloudToken.vin(this));
                    out.putLong("expiresAtMs", CloudToken.expiresAtMs(this));
                }
                break;
            }
            case MSG_TOOL: {
                String name = in.getString("name", "");
                if (!TOOLS.contains(name)) {
                    out.putString("error", "unknown_tool");
                    out.putString("result", "Bilinmeyen işlem: " + name);
                    break;
                }
                String args = in.getString("args", "{}");
                dispatcher.prepare();
                out.putString("result", dispatcher.execute(name, new JSONObject(args.isEmpty() ? "{}" : args)));
                break;
            }
            case MSG_CONTEXT: {
                String apps = dispatcher.executor().getInstalledAppLabelsSummary();
                ArrayList<String> list = new ArrayList<>();
                for (String a : apps.split(",")) {
                    if (!a.trim().isEmpty()) list.add(a.trim());
                }
                out.putStringArrayList("apps", list);
                if (in.getBoolean("withTelemetry", false)) {
                    dispatcher.prepare();
                    out.putString("telemetry", dispatcher.executor().getVehicleMetricsSummary());
                }
                break;
            }
            case MSG_STOCK_ASSISTANT: {
                if (in.containsKey("set")) {
                    StockVoiceAssistant.Result r = StockVoiceAssistant.apply(this, in.getBoolean("set"));
                    out.putStringArrayList("failed", new ArrayList<>(r.failed));
                }
                out.putBoolean("owner", StockVoiceAssistant.isDeviceOwner(this));
                out.putBoolean("disabled", StockVoiceAssistant.isDisabledPref(this));
                break;
            }
            default:
                out.putString("error", "unknown_message");
        }
    }
}
