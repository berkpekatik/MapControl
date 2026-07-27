# Map Control

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

> ⚠️ **v1.1.4 — Performans raporu (DesaySV, Android 11)**  
> Launcher modu (3D araç modeli) en büyük kaynak tüketicisidir. Optimizasyon sonrası ölçümler (cihaz `29115210`):
>
> | Durum | CPU | Bellek (PSS) |
> |-------|-----|--------------|
> | Launcher **açık** (önce → sonra) | %70 → **%25–58** | 449 MB → **~370 MB** |
> | Launcher **kapalı** | **%7** | **111 MB** |
> | GPU gecikme (90. yüzdelik) | 4950 ms → **17 ms** | — |
>
> **Özet:** Yandex scrape yalnızca navigasyon aktifken çalışır; 3D model post-processing kapatıldı (Saf GLB + FXAA). Launcher'ı kapatmak bellekte **~%70**, CPU'da **~%87** kazanç sağlar. Detaylar aşağıda.

Araç ana ünitesinde (IVI) çalışan bir kontrol merkezi uygulaması. Desay SV tabanlı sistemlerde hem araçla entegre çalışır hem de aynı ağdaki cihazlardan yönetim sağlar.

Bu proje **eğlence ve kişisel ihtiyaç** amacıyla geliştirilmiştir.

---

## v1.1.4 — Performans optimizasyonu

**Cihaz:** DesaySV (Android 11) — `29115210` · **Tarih:** 26–27 Temmuz 2026

### Yapılan değişiklikler

**Yandex navigasyon scrape (`GlobalBackService`)**
- Nav **aktif:** 750 ms throttle + içerik değişimi dinlenir
- Nav **pasif:** yalnızca pencere değişimi, 2000 ms throttle
- Nav bitince cluster overlay otomatik kapanır
- *Önce:* 350 ms throttle, tüm Yandex içerik değişimlerinde scrape

**3D araç modeli (`VehicleGlbView`)**
- Post-processing kapatıldı (SSAO, bloom, HDR grading yok)
- DRS kapalı — tam çözünürlük, net görüntü
- Yalnızca hafif FXAA + GLB materyalleri

### Ölçüm sonuçları

| Metrik | Launcher açık (önce → sonra) | Launcher kapalı |
|--------|-------------------------------|-----------------|
| CPU | %70 → **%25–58** | **%7** |
| PSS | 449 MB → **~370 MB** | **111 MB** |
| Render thread (`FEngine::loop`) | 2556 → **1259 jiffies** (~%51 ↓) | Durdu |
| GPU 90. yüzdelik | 4950 ms → **17 ms** | — |
| GPU medyan | 14 ms → **6 ms** | — |

### Bulgular

1. **3D model (Filament)** launcher açıkken CPU ve belleğin büyük kısmını tüketir.
2. **Saf GLB modu** hem performansı artırdı hem görüntüyü netleştirdi.
3. **Launcher'ı kapatmak** en büyük kazanç: bellek **−%70**, CPU **−%87**.
4. Launcher kapalıyken kalan yük (~111 MB, %7 CPU): ana UI + floating overlay'ler + arka plan servisleri.
5. UI jank büyük ölçüde floating overlay'lerden (`ViewRootImpl`); 3D GPU süreleri iyi (medyan 6 ms).

---

## Özellikler

**Yerel HTTP sunucu (7462):** Cihazla aynı ağdaki telefon veya bilgisayardan tarayıcı ile dosya yükleme, APK kurma, uygulama listeleme/silme/başlatma. Ana ekrandaki QR kod ile sunucu adresine hızlı erişim.

**Wi-Fi:** Ağ aç/kapat, tarama ve bağlanma.

**Profil / bulut:** Harici API (api.vnoisy.dev) ile giriş, araç token’ı; isteğe bağlı araç kapanırken konum kaydetme.

**Uygulamalar:** Sunucudan veya yerel listeden uygulama görüntüleme, kurma, güncelleme, açma ve kaldırma.

**Yansıtma:** Telefon veya harici ekran yansıtma ile ilgili yönetim.

**Hafıza modu:** Kayıtlı sürüş modu (Snow, Mud, Offroad, Sand) seçimi; araç açıldığında bu mod otomatik uygulanır.

**Ayarlar:** Araç kapanınca navigasyonu otomatik kapatma, otomatik konum kaydetme, güç modu davranışı (motor çalışınca / araç hazırken / elle) vb.

**Floating back butonu:** Başka uygulama (örn. navigasyon) öndeyken geri tuşu simülasyonu (erişilebilirlik servisi).

**Kamera ve ses testi:** Test ekranları.

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

## Release imzalı build

İmzalı release APK almak için `local.properties` içinde keystore şifrelerini tanımlamanız gerekir. `local.properties.example` dosyasını `local.properties` olarak kopyalayıp `RELEASE_STORE_PASSWORD` ve `RELEASE_KEY_PASSWORD` değerlerini doldurun.

---

*This project is maintained with Cursor and Claude Opus 4.5. Debugging and validation are done by humans.*
