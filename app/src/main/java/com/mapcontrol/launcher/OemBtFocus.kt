package com.mapcontrol.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.util.Log

/**
 * OEM BT servisinden (`com.desaysv.ivi.vds.bt`) yalnızca BT müzik odağını (42) ister, telefona oynat GÖNDERMEZ.
 *
 * Telefon önceliği önceden VDBus `MUSIC_PLAY{1,0}` ile geçiyordu; bu, telefona AVRCP oynat da yollar. Mesaj bildirimi
 * sesi oturumu bir an PLAYING yaparken telefonda duraklatılmış bir şarkı varsa o şarkı başlıyor ve radyo kalıcı olarak
 * kesiliyordu. Yalnızca odak alınınca bildirim sesi bitince BT kendiliğinden durur ve [PriorityRevert] radyoya döner;
 * gerçek müzikte telefon zaten çaldığı için sürer.
 *
 * Yol (Tiggo 7 / Tiggo 8 `SVVDSBluetooth` APK'sından): OEM BT servisi `AndLinkerService`'i
 * (`com.desaysv.service.bluetooth.REMOTE_SERVICE_ACTION`, exported, izin yok) yayınlar. AndLinker
 * (github.com/codezjx/AndLinker) AIDL'siz bir IPC: `ITransfer.execute(Request)` ile sınıfın kısa adı + yöntem adı
 * gönderilir. `IA2DPManager.requestBTMusicAudioFocus()` OEM'in kendi kullandığı yoldur
 * (`A2DPAudioMgr.notifyPlayStateChanged`, BT zaten kaynakken); odak verilince OEM A2DP sesini açar, telefonu yalnızca
 * kendisi duraklatıp "sürdür" bayrağı koyduysa yeniden oynatır. Bu yol VDBus'ta yoktur.
 *
 * Bağlantı kurulamaz ya da çağrı başarısız olursa [requestFocus] null döner; çağıran eski yola düşer.
 */
internal object OemBtFocus {

    private const val TAG = "MediaSource"

    const val PKG = "com.desaysv.ivi.vds.bt"
    const val ACTION = "com.desaysv.service.bluetooth.REMOTE_SERVICE_ACTION"

    /** AndLinker `ITransfer` AIDL tanımlayıcısı ve `execute` işlem kodu. */
    private const val DESCRIPTOR = "com.codezjx.alinker.ITransfer"
    private const val TRANSACTION_EXECUTE = 1

    /** AndLinker sunucusu `@RemoteInterface` arayüzünü kısa adıyla eşler (`Invoker.handleObject`). */
    private const val TARGET_CLASS = "IA2DPManager"
    private const val METHOD_REQUEST_FOCUS = "requestBTMusicAudioFocus"

    /** AndLinker `Response.STATUS_CODE_SUCCESS`. */
    const val STATUS_SUCCESS = 200

    @Volatile private var binder: IBinder? = null
    @Volatile private var bound = false
    private var appContext: Context? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            binder = service
            Log.i(TAG, "bt-focus: connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // BIND_AUTO_CREATE: servis yeniden başlayınca sistem yeniden bağlar.
            binder = null
            Log.i(TAG, "bt-focus: disconnected")
        }

        override fun onBindingDied(name: ComponentName?) {
            binder = null
            Log.i(TAG, "bt-focus: binding died, rebinding")
            val ctx = appContext ?: return
            synchronized(this@OemBtFocus) {
                try {
                    ctx.unbindService(this)
                } catch (t: Throwable) {
                    // zaten bağlı değil
                }
                bound = false
            }
            bind(ctx)
        }
    }

    /** Bağlantıyı önceden kurar (telefon önceliği OEM'in 1 sn'lik duraklatma süresi içinde gitmeli). Tekrar çağrılabilir. */
    fun bind(context: Context) {
        synchronized(this) {
            if (bound) return
            val app = context.applicationContext
            appContext = app
            bound = try {
                app.bindService(Intent(ACTION).setPackage(PKG), connection, Context.BIND_AUTO_CREATE)
            } catch (t: Throwable) {
                Log.w(TAG, "bt-focus: bind ${t.javaClass.simpleName}")
                false
            }
            if (!bound) Log.i(TAG, "bt-focus: service not available")
        }
    }

    /**
     * io: `IA2DPManager.requestBTMusicAudioFocus()`. AndLinker durum kodu ([STATUS_SUCCESS] başarı) ya da bağlı değilse /
     * çağrı patladıysa null. Başarı yalnızca isteğin iletildiğini söyler; odağın verildiği kaynak bildiriminden anlaşılır.
     */
    fun requestFocus(): Int? {
        val b = binder ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            writeRequest(data, TARGET_CLASS, METHOD_REQUEST_FOCUS)
            b.transact(TRANSACTION_EXECUTE, data, reply, 0)
            reply.readException()
            // Response: statusCode, statusMessage, result (yalnızca durum okunur).
            if (reply.readInt() != 0) reply.readInt() else null
        } catch (t: Throwable) {
            Log.w(TAG, "bt-focus: execute ${t.javaClass.simpleName}")
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /**
     * AndLinker `ITransfer.Stub.Proxy.execute` ile aynı biçim: arayüz belirteci, istek var (1), `Request.writeToParcel(p, 0)`
     * = hedef sınıf, yöntem, `writeParcelableArray(args)` (argümansız: uzunluk 0).
     */
    private fun writeRequest(p: Parcel, targetClass: String, method: String) {
        p.writeInterfaceToken(DESCRIPTOR)
        p.writeInt(1)
        p.writeString(targetClass)
        p.writeString(method)
        p.writeInt(0)
    }
}
