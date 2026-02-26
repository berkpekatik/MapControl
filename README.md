# Map Control

Araç ana ünitesinde (IVI) çalışan bir kontrol merkezi uygulaması. Desay SV tabanlı sistemlerde hem araçla entegre çalışır hem de aynı ağdaki cihazlardan yönetim sağlar.

Bu proje **eğlence ve kişisel ihtiyaç** amacıyla geliştirilmiştir.

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
