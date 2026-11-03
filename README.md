# Map Control

> **Sorumluluk reddi.** Bu yazılım, aracın ana ünitesinde çalışan deneysel bir Android uygulamasıdır ve olduğu gibi sunulur. Motor veya fren yazılımını doğrudan programlamaz. Ana ünitenin araç arayüzü üzerinden sürüş modunu, sürücü destek ayarlarını, kilidi, koltuk hafızasını, cam tavanı ve klimayı değiştirebilir; ekran, uygulama ve ses gibi Android işlevlerini de yönetir. Bu komutlar aracın davranışını değiştirebilir. Çalışır durumda olma, belirli bir amaca uygunluk veya hasar oluşmayacağı konusunda garanti verilmez. Yazılımın kullanımından doğan araç hasarı, veri kaybı ve diğer zararlardan, yürürlükteki kanunun izin verdiği azami ölçüde, yazar sorumlu değildir. Yazılımı çalıştıran kişi tüm riski kabul eder.
>
> **Disclaimer.** This software is an experimental Android application that runs on the vehicle head unit and is provided as is. It does not directly program the engine or brake software. Through the head unit's vehicle interface it can change the drive mode, driver-assistance settings, locks, seat memory, the sunroof, and climate control. It also manages Android functions such as the display, apps, and audio. These commands can change how the vehicle behaves. There is no warranty of operability, fitness for a particular purpose, or that no damage will occur. To the maximum extent permitted by applicable law, the author is not liable for vehicle damage, data loss, or other harm arising from use of the software. The person who runs the software accepts all risk.
>
> Lisans: [Apache License 2.0](LICENSE). Tam metin: [DISCLAIMER](DISCLAIMER).

> **Geliştirici notu:** Güncel launcher orta kartı Lite paneldir (saat, hava durumu, araç ölçümleri). UI tasarım sistemi ve fiziksel IVI kabul hedefleri için [tasarım ve IVI inceleme indeksine](docs/ui/README.md) bakın.

 ⚠️ **Bilinen sorunlar / Known issues**

 | | |
 |---|---|
 | **🇹🇷 Başlangıçta uygulama açılmıyor** | Araç açılışında servis bazen MapControl’ü otomatik başlatmayabilir. **Geçici çözüm:** Uygulamayı elle açın veya IVI’yi yeniden başlatın. |
 | **🇬🇧 App not launched at startup** | On boot, the background service may fail to open MapControl automatically. **Workaround:** launch the app manually or reboot the IVI. |
 | **🇹🇷 Mikrofon kesintisi** | MapControl yüklüyken sesli asistan / mikrofon kesintisi tespit edildi. **Geçici çözüm:** IVI’yi yeniden başlatın (reset). |
 | **🇬🇧 Microphone dropouts** | Microphone / voice assistant interruptions observed while MapControl is installed. **Workaround:** reboot the IVI (reset). |
 | **🇹🇷 Ses takılması** | Yüksek RAM veya GPU bellek (vRAM) kullanımında müzik / sistem sesinde takılma görülebilir. Launcher modunu kapatmak veya 3D modeli devre dışı bırakmak yardımcı olur. |
 | **🇬🇧 Audio stuttering** | High RAM or GPU memory (vRAM) usage may cause music / system audio stutter. Disabling Launcher mode or the 3D model helps. |
 | **🇹🇷 Yandex → Cluster** | Yandex navigasyon panelleri tam entegre değil; bazı sürümlerde yol bilgisi (manevra, ETA) okunamayabilir veya cluster kartları eksik kalabilir. |
 | **🇬🇧 Yandex → Cluster** | Yandex navigation panels are not fully integrated; route info (maneuvers, ETA) may fail to read or cluster cards may be incomplete on some builds. |

 **Sorun mu buldunuz?** Kod hatası, kullanım hatası veya yukarıdakiler dışında bir şey olsun — [GitHub Issues](https://github.com/berkpekatik/MapControl/issues) üzerinden bildirmekten çekinmeyin.  
**Found a problem?** Whether it’s a bug, usage question, or something not listed above — please don’t hesitate to open a [GitHub Issue](https://github.com/berkpekatik/MapControl/issues).

Araç ana ünitesinde (IVI) çalışan bir kontrol merkezi uygulaması. Desay SV tabanlı sistemlerde hem araçla entegre çalışır hem de aynı ağdaki cihazlardan yönetim sağlar.

Bu proje **eğlence ve kişisel ihtiyaç** amacıyla geliştirilmiştir.

---

## Özellikler

**Ana sayfa:** Tam ekran launcher. Saat, hava durumu, araç ölçümleri, müzik ve kısayollar. Orta kart Lite paneldir.

**Wi-Fi:** Ağ açma, kapatma, tarama ve bağlanma.

**Uygulamalar:** Yüklü uygulamaları görüntüleme, kurma, güncelleme, açma ve kaldırma.

**Aktar:** Aynı ağdaki telefon veya bilgisayardan, 7462 portundaki sayfa ile dosya yükleme ve APK kurma. QR kod ile adrese erişim.

**Sürüş hafızası:** Seçilen sürüş modu (Eco, Normal, Sport, Snow, Mud, Offroad, Sand) ve kapatılan sürücü yardımları araç açıldığında uygulanır.

**Karşılama:** Araç açılışında karşılama sesi ve ekranı.

**Ses ayarları:** Ekolayzer.

**Otomasyon:** Dış sıcaklığa göre klima, koltuk ısıtma veya havalandırma ve direksiyon ısıtması.

**Cam tavan:** Cam tavanın açılması, perde konumu ve hız veya yağmurda kapanması.

**Yüz tanıma:** DMS kamerası ile sürücüyü tanıma ve kayıtlı koltuk hafızasını uygulama.

**Kamera 360:** Çevre kameralarını izleme ve kayıtları görüntüleme.

**Bölünmüş ekran:** İki uygulamayı yan yana açma.

**Gösterge ekranı:** Navigasyonu gösterge ekranına alma. Yandex ve Google Maps yol bilgisi bu ekranda kart olarak gösterilebilir.

**Araç bilgileri:** Anlık araç ölçümleri.

**Telefon eşleme:** Yolculuk ve araç verisini MapControl telefon uygulamasına vermek için eşleşme anahtarı. Telefon uygulaması şu an yalnızca test kullanıcılarına açıktır.

**Ayarlar:** Açılışta arka planda çalışma, uygulamayı ekranda açma, tema, dil ve tam ekran ana sayfa.

**Yüzen geri tuşu:** Başka bir uygulama öndeyken geri gitmek için sürüklenebilir tuş.

---

## Araç entegrasyonu

Desay SV CarInfo / VDBus ile güç modu takibi, cluster ekranı ve sürüş modu otomasyonu yapılır. Araç OFF → STANDBY → ON geçişleri izlenir; açılışta cluster/navigasyon paneli açılabilir, hoşgeldin sesi çalınabilir, kayıtlı sürüş modu ve güvenlik ayarları (ISS, LDW, LDP, FCW, AEB, hız sınırı) uygulanır. Araç kapanırken (ayara göre) navigasyon kapatılır ve isteğe bağlı konum buluta kaydedilir.

### `openClusterDisplay()`

Cluster (araç içi ikinci ekran / navigasyon paneli) ekranını açar. Özet akış:

1. **Hazırlık mesajı:** Cluster ekranında “Uygulama Hazırlanıyor” metni gösterilir (timeout ~7 saniye).
2. **Wake-up event:** VDBus üzerinden `NAVIGATION_DISPLAY_TO_CLUSTER` event’i gönderilir; `requestDisplayNaviArea = true` ile panel uyandırılır.
3. **Display area:** `NAVIGATION_DISPLAY_AREA` ile alan 10 atanır (navigasyon alanı açık).
4. **Display cluster:** Aynı event tekrar gönderilir; `requestDisplayNaviArea = false` ile panel açık durumda kalır.
5. **Uygulama başlatma:** Ayarlardan seçilmiş bir uygulama varsa, cluster display ID bulunur (varsayılan olmayan ilk display); uygulama bu ekranda `ActivityOptions.setLaunchDisplayId(clusterDisplayId)` ile başlatılır. Sonrasında hazırlık mesajı gizlenir.

Cluster display ID, `DisplayManager` üzerinden varsayılan olmayan (genelde ikinci fiziksel ekran) display olarak tespit edilir.

### `closeClusterDisplay()`

Cluster / navigasyon panelini kapatır. Özet akış:

1. **Kontrol (isteğe bağlı):** Seçili uygulama varsa, uygulamanın hangi display’de olduğu kontrol edilir; zaten cluster dışındaysa işlem yapılmayabilir.
2. **Display area kapatma:** VDBus ile `NAVIGATION_DISPLAY_AREA` event’i `naviDisplayArea = 0` ile gönderilir (alan kapatılır).
3. **Cluster kapatma:** `NAVIGATION_DISPLAY_TO_CLUSTER` event’i `displayCluster = false`, `naviFrontDeskStatus = false` ile gönderilir; panel kapanır.
4. **Uygulama taşıma:** Seçili uygulama cluster’da açıksa, varsayılan display’e (0) taşınır. `closeClusterDisplay(boolean sendBackground)` ile `sendBackground == true` ise uygulama ayrıca arka plana gönderilir (öne gelmez).

MainActivity’de manuel aç/kapa butonları ve MapControlService’de araç güç moduna göre otomatik açma/kapama bu metodları kullanır.

---

## Yerel debug build

JDK 17, Android SDK Platform 34 ve Android SDK Build-Tools 34.0.0 gereklidir. SDK konumunu `ANDROID_HOME` ile veya `local.properties` içindeki `sdk.dir` alanıyla belirtin.

Windows PowerShell üzerinde APK ve birim testleri:

```powershell
java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain :app:assembleDebug :app:testDebugUnitTest
```

Android Studio üzerinden bir emülatör açtıktan sonra kurulum ve çalıştırma:

```powershell
adb -e install -r app/build/outputs/apk/debug/app-debug.apk
adb -e shell am start -n com.mapcontrol/.ui.activity.MainActivity
```

Debug derlemesi release imzalama anahtarı gerektirmez. Standart Android emülatöründe arayüz açılır; DesaySV araç servisleri ve fiziksel gösterge ekranı bulunmadığı için araç kontrolleri çalışmaz.

## Release imzalı build

İmzalı release APK almak için `local.properties` içinde keystore şifrelerini tanımlamanız gerekir. `local.properties.example` dosyasını `local.properties` olarak kopyalayıp `RELEASE_STORE_PASSWORD` ve `RELEASE_KEY_PASSWORD` değerlerini doldurun.

---

*This project is maintained with Cursor and Claude Opus 4.5. Debugging and validation are done by humans.*
