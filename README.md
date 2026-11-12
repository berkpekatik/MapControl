<div align="center">

# MapControl

**Araç ana ünitesi (IVI) için kontrol merkezi.**
Launcher, sürüş hafızası, otomasyon, gösterge ekranı ve daha fazlası, tek uygulamada.

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
![Platform](https://img.shields.io/badge/platform-Android%20IVI-3DDC84?logo=android&logoColor=white)
![Target](https://img.shields.io/badge/Desay%20SV-CarInfo%20%2F%20VDBus-orange)
![Status](https://img.shields.io/badge/status-experimental-red)

[Özellikler](#-özellikler) · [Kurulum](#-kurulum) · [Sorun bildir](https://github.com/berkpekatik/MapControl/issues)

</div>

> [!WARNING]
> **Deneysel yazılım.** Sürüş modu, sürücü destek ayarları, kilit, koltuk hafızası, cam tavan ve klimayı araç arayüzü üzerinden değiştirebilir. Garanti yoktur; kullanım riski size aittir. Tam metin: [DISCLAIMER](DISCLAIMER) · Lisans: [Apache 2.0](LICENSE)
>
> *Experimental software for the vehicle head unit, provided "as is" with no warranty. Use at your own risk. See [DISCLAIMER](DISCLAIMER).*

---

## ✨ Özellikler

| | |
|---|---|
| 🏠 **Launcher** | Tam ekran ana sayfa: saat, hava durumu, araç ölçümleri, müzik, kısayollar |
| 🚗 **Sürüş hafızası** | Eco, Normal, Sport, Snow, Mud, Offroad, Sand ve sürücü yardımları araç açılınca otomatik uygulanır |
| 🙂 **Yüz tanıma** | DMS kamerasıyla sürücüyü tanır, kayıtlı koltuk hafızasını uygular |
| 🌡️ **Otomasyon** | Dış sıcaklığa göre klima, koltuk ısıtma/havalandırma, direksiyon ısıtması |
| ☀️ **Cam tavan** | Açma, perde konumu, hızda veya yağmurda otomatik kapanma |
| 🧭 **Gösterge ekranı** | Navigasyonu cluster'a taşır; Yandex ve Google Maps yol bilgisi kart olarak görünür |
| 📷 **Kamera 360** | Çevre kameraları ve kayıtlar |
| 🪟 **Bölünmüş ekran** | İki uygulama yan yana |
| 📲 **Aktar** | Aynı ağdaki telefon/bilgisayardan dosya yükleme ve APK kurma (QR ile erişim) |
| 🔊 **Karşılama ve ses** | Açılış sesi/ekranı, ekolayzer |
| 📦 **Uygulamalar, Wi-Fi ve Bluetooth** | Uygulama yönetimi, ağ tarama ve bağlanma, araç çalışınca son telefona otomatik bağlanma |
| 🔙 **Yüzen geri tuşu** | Başka uygulama öndeyken sürüklenebilir geri tuşu |
| 📱 **Telefon eşleme** | Yolculuk ve araç verisini telefon uygulamasına aktarır *(şu an yalnızca test kullanıcılarına açık)* |
| 🐞 **Log Kaydını Geliştiriciye Gönder** | Donma, Bluetooth kopması ya da ekran resetlenmesinde, onaydan sonra sistem kayıtlarını maskeleyip eşleşmiş hesaba gönderir |

---

## 🚘 Araç entegrasyonu

Desay SV **CarInfo / VDBus** üzerinden güç modu (OFF → STANDBY → ON), cluster ekranı ve sürüş modu yönetilir.

- **Araç açılırken:** cluster/navigasyon paneli açılır, karşılama sesi çalar, kayıtlı sürüş modu ve güvenlik ayarları (ISS, LDW, LDP, FCW, AEB, hız sınırı) uygulanır.
- **Araç kapanırken:** ayara göre navigasyon kapatılır, isteğe bağlı konum buluta kaydedilir.

<details>
<summary><b>Cluster akışı: <code>openClusterDisplay()</code> / <code>closeClusterDisplay()</code></b></summary>

**Açma**
1. Cluster'da "Uygulama Hazırlanıyor" mesajı gösterilir (zaman aşımı ~7 sn).
2. VDBus ile `NAVIGATION_DISPLAY_TO_CLUSTER` gönderilir (`requestDisplayNaviArea = true`), panel uyanır.
3. `NAVIGATION_DISPLAY_AREA` ile alan `10` atanır.
4. Aynı event `requestDisplayNaviArea = false` ile tekrarlanır, panel açık kalır.
5. Seçili uygulama, varsayılan olmayan ilk display'de `ActivityOptions.setLaunchDisplayId()` ile başlatılır.

**Kapatma**
1. Seçili uygulamanın hangi display'de olduğu kontrol edilir.
2. `NAVIGATION_DISPLAY_AREA` → `naviDisplayArea = 0`.
3. `NAVIGATION_DISPLAY_TO_CLUSTER` → `displayCluster = false`, `naviFrontDeskStatus = false`.
4. Uygulama display 0'a taşınır; `sendBackground == true` ise arka plana gönderilir.

</details>

---

## 🔒 Gizlilik / izinler

**Telefon paneli (launcher):** Launcher modu açıkken ana ekrandaki telefon paneli, arama şeridi ve arama ekranı bağlı telefonun rehberini ve son aramalarını gösterir. Bunun için uygulama şu izinleri ister; launcher modu kapalıyken bu özellikler çalışmaz ve izin istenmez:

- `READ_CONTACTS`: favoriler ve kişi listesi (Bluetooth ile telefondan eşitlenen rehber).
- `READ_CALL_LOG`: son aramalar ve "Geri ara".
- `CALL_PHONE`: panelden ya da arama ekranından arama başlatma.
- `ANSWER_PHONE_CALLS`: arama ekranından gelen aramayı cevaplama ve aramayı kapatma.
- `READ_EXTERNAL_STORAGE`: yalnızca isteğe bağlı fotoğraf widget'ı, İndirilenler klasöründeki resmi göstermek için.

Rehber ve arama geçmişi yalnızca ana ünitede, ekranda göstermek için kullanılır: buluta, web paneline ya da telefon eşleme uygulamasına gönderilmez. Açılışta hızlı gösterim için son liste uygulamaya özel, yedeğe girmeyen bir dosyada tutulur (fotoğraflar yazılmaz); başka bir telefon bağlanınca silinir. Günlüğe (log) isim ya da numara yazılmaz, yalnızca sayılar yazılır. İzinler Android ayarlarından istendiği zaman geri alınabilir.

**Log Kaydını Geliştiriciye Gönder (Ayarlar):** Arka planda log kaydı tutulmaz. Kullanıcı düğmeye basıp onaylayınca sistemin zaten tuttuğu kayıtlar bir kez okunur: son 72 saatin çökme/donma/yeniden başlatma kayıtları (DropBox), uygulamanın son kapanma nedenleri ve donma dökümleri, Bluetooth durumu (`dumpsys bluetooth_manager`, `DUMP` izni `install.sh` ile verilir) ve son sistem günlüğü (`logcat -d`). Telefon numaraları, e-posta ve Bluetooth adresleri, cihaz adları ve koordinatlar **ana ünitede, gönderilmeden önce** maskelenir. Kayıt yalnızca telefonla eşleşmiş hesaba yüklenir, yalnızca geliştirici okuyabilir ve 30 gün sonra silinir. Ayrıca açılışta yeni bir sistem çökmesi varsa çökme raporlayıcısına yalnızca kaydın türü ve süreç adı gider (metni gönderilmez).

---

## 🛠️ Kurulum

**Hazır APK yok**, derleyerek kurulur. Gereksinimler: JDK 17, Android SDK Platform 34, Build-Tools 34.0.0.

```bash
# local.properties içine: sdk.dir=/yol/Android/sdk   (veya ANDROID_HOME)
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

**Araca kurulum:** `local.properties.example` dosyasını `local.properties` olarak kopyalayıp `RELEASE_STORE_PASSWORD` ve `RELEASE_KEY_PASSWORD` değerlerini doldurun, sonra:

```bash
./install.sh      # release APK'yı derler, adb ile yükler
./uninstall.sh    # kaldırır
```

**Emülatörde deneme:** Arayüz açılır, ancak Desay SV servisleri ve fiziksel cluster olmadığı için araç kontrolleri çalışmaz.

```bash
adb -e install -r app/build/outputs/apk/debug/app-debug.apk
adb -e shell am start -n com.mapcontrol/.ui.activity.MainActivity
```

---

Başka bir sorun mu buldunuz? [Issue açın](https://github.com/berkpekatik/MapControl/issues).

---

<div align="center">

Eğlence ve kişisel ihtiyaç için geliştirilmiştir · [Apache 2.0](LICENSE)

<sub>Cursor ve Claude ile geliştirilir. Test ve doğrulama insanlar tarafından yapılır.</sub>

</div>
