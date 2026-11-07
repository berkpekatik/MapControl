package com.mapcontrol.launcher

import android.content.Context
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Rehber ve son aramaların uygulamaya özel disk önbelleği (süreç yeniden başlayınca anında gösterim için).
 *
 * OEM'de kalıcılık yok: BT servisinin _CACHE haritası yalnız o PBAP oturumunda indirme bitince dolar, PBAP kopunca
 * silinir. Bu yüzden son başarılı listeyi biz tutarız.
 *
 * GİZLİLİK:
 * - Dosya yalnızca `noBackupFilesDir` içinde (uygulamaya özel, otomatik yedeğe girmez); başka yere kopyalanmaz.
 * - Bağlı telefona bağlıdır: başlıkta adresin tuzlu SHA-256 özeti ([deviceHash]) durur, ham MAC düz metin yazılmaz.
 *   Özet yalnız göz gezdirerek okumayı önler: tuz APK içinde sabit ve özet 64 bit, APK'yı elinde tutan biri bilinen
 *   üretici önekleri üzerinden kaba kuvvetle adresi bulabilir. Etkisi düşük: dosya zaten uygulamaya özel depoda ve
 *   rehberi düz metin taşıyor.
 *   Farklı telefon bağlanınca dosya silinir; yüklenirken özet tutmazsa da silinir.
 * - Fotoğraf yazılmaz ([PhoneContact.photoKey] / [PhoneRecent.photoKey] boş kaydedilir; fotoğraflar yalnız bellekte).
 * - Günlüğe yalnız sayılar ve istisna sınıf adı yazılır.
 */
internal object PhoneCache {
    private const val TAG = "PhoneCache"
    private const val FILE_NAME = "phone_directory.bin"

    // ---- Saf kodlama (JVM testli) ----

    private const val MAGIC = 0x4D4C5042 // "MLPB"
    private const val VERSION = 1
    /** OEM sınırları: rehber en çok 5000, geçmiş depoda [DirectoryStore.MAX_RECENTS]. */
    const val MAX_CONTACTS = 5000
    private const val SALT = "mapcontrol-phone-cache-v1:"

    class Decoded(val deviceHash: String, val savedAtMs: Long, val contacts: List<PhoneContact>, val recents: List<PhoneRecent>) {
        override fun toString(): String = "PhoneCache.Decoded(contacts=${contacts.size}, recents=${recents.size})"
    }

    /**
     * Cihaz adresinin özeti (büyük/küçük harf duyarsız, tuzlu SHA-256, ilk 16 hex). Yalnız gelişigüzel okumayı önler;
     * tuz sabit olduğundan bilinen üretici önekiyle kaba kuvvete dayanıklı değildir (sınıf notuna bakın).
     */
    fun deviceHash(address: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest((SALT + address.trim().uppercase(Locale.ROOT)).toByteArray(Charsets.UTF_8))
        return d.take(8).joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    fun encode(deviceHash: String, savedAtMs: Long, contacts: List<PhoneContact>, recents: List<PhoneRecent>): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { o ->
            o.writeInt(MAGIC)
            o.writeInt(VERSION)
            o.writeUTF(deviceHash)
            o.writeLong(savedAtMs)
            val cs = contacts.take(MAX_CONTACTS)
            o.writeInt(cs.size)
            for (c in cs) {
                o.writeUTF(c.id)
                o.writeUTF(c.name)
                o.writeUTF(c.number)
                o.writeBoolean(c.starred)
            }
            val rs = recents.take(DirectoryStore.MAX_RECENTS)
            o.writeInt(rs.size)
            for (r in rs) {
                o.writeByte(r.type.ordinal)
                o.writeBoolean(r.name != null)
                if (r.name != null) o.writeUTF(r.name)
                o.writeUTF(r.number)
                o.writeLong(r.timeMs)
            }
        }
        return bytes.toByteArray()
    }

    /** Bozuk, eski sürüm ya da sınır dışı dosyada null (çağıran dosyayı siler). */
    fun decode(data: ByteArray): Decoded? = try {
        DataInputStream(ByteArrayInputStream(data)).use { i ->
            if (i.readInt() != MAGIC || i.readInt() != VERSION) return null
            val hash = i.readUTF()
            val savedAt = i.readLong()
            val nc = i.readInt()
            if (nc < 0 || nc > MAX_CONTACTS) return null
            val contacts = ArrayList<PhoneContact>(nc)
            repeat(nc) {
                val id = i.readUTF()
                val name = i.readUTF()
                val number = i.readUTF()
                val starred = i.readBoolean()
                contacts.add(PhoneContact(id, name, number, starred))
            }
            val nr = i.readInt()
            if (nr < 0 || nr > DirectoryStore.MAX_RECENTS) return null
            val types = RecentType.values()
            val recents = ArrayList<PhoneRecent>(nr)
            repeat(nr) {
                val t = i.readByte().toInt()
                if (t !in types.indices) return null
                val name = if (i.readBoolean()) i.readUTF() else null
                val number = i.readUTF()
                val time = i.readLong()
                recents.add(PhoneRecent(types[t], name, number, time))
            }
            if (i.read() != -1) return null
            Decoded(hash, savedAt, contacts, recents)
        }
    } catch (e: java.io.IOException) {
        null
    } catch (e: RuntimeException) {
        null
    }

    // ---- Dosya (io kuyruğunda) ----

    private fun file(context: Context): File = File(context.noBackupFilesDir, FILE_NAME)

    /** [deviceHash] telefonunun önbelleği; yoksa null. Bozuksa ya da başka telefonunsa dosya silinir. */
    fun load(context: Context, deviceHash: String): Decoded? {
        val f = file(context)
        if (!f.isFile) return null
        val d = try {
            decode(f.readBytes())
        } catch (t: Throwable) {
            Log.w(TAG, "okuma: ${t.javaClass.simpleName}")
            null
        }
        if (d == null || d.deviceHash != deviceHash) {
            Log.i(TAG, if (d == null) "bozuk önbellek silindi" else "başka telefonun önbelleği silindi")
            clear(context)
            return null
        }
        return d
    }

    /** Geçici dosyaya yazıp yerine taşır (yarım dosya kalmaz). Başarılıysa true. */
    fun save(context: Context, deviceHash: String, contacts: List<PhoneContact>, recents: List<PhoneRecent>): Boolean {
        val f = file(context)
        val tmp = File(f.parentFile, "$FILE_NAME.tmp")
        return try {
            tmp.writeBytes(encode(deviceHash, System.currentTimeMillis(), contacts, recents))
            if (!tmp.renameTo(f)) {
                tmp.delete()
                Log.w(TAG, "yazma: taşınamadı")
                return false
            }
            Log.i(TAG, "yazıldı: kişi=${minOf(contacts.size, MAX_CONTACTS)} geçmiş=${minOf(recents.size, DirectoryStore.MAX_RECENTS)}")
            true
        } catch (t: Throwable) {
            tmp.delete()
            Log.w(TAG, "yazma: ${t.javaClass.simpleName}")
            false
        }
    }

    fun clear(context: Context) {
        try {
            val f = file(context)
            if (f.exists() && f.delete()) Log.i(TAG, "silindi")
        } catch (t: Throwable) {
            Log.w(TAG, "silme: ${t.javaClass.simpleName}")
        }
    }
}
