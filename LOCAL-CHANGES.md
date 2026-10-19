# MapControl yerel geliştirme

> Güncel sürüm **1.2.4-local.2** ve bağımsız ön kontrol APK'sı için
> [son düzeltme raporunu](../docs/YEREL-PAKET-DUZELTMELERI-2026-10-18.md) okuyun.
> Aşağıdaki local.1 hash'i ve test sayıları ilk geliştirme kaydıdır.

Kaynak: https://github.com/berkpekatik/MapControl

Başlangıç commit: `bb4d70a46b30dfdba5e85681ce01bb9eed62069a`.
Yerel dal: `local/safe-maintenance`. Tarih: 18 Ekim 2026.

## Bu değişikliklerin kapsamı

Bu uygulama araç IVI sistemine kurulan MapControl projesidir. Önceki
Tiggo8Cluster Android Auto / Car App Library prototipinden ayrıdır.
Bu klasördeki çalışma bilgisayarda yapılmıştır; kurulum betikleri gerçek
cihazda çalıştırılmamıştır. OEM firmware veya güvenlik kilidi değiştirilmemiştir.

### Kaldırma

- Uygulamanın sıfırlama akışından diğer yerel uygulamaları topluca silme ve
  `/data/local/tmp/*.apk` temizleme kaldırıldı. Menüde tek bir kendi uygulamasını
  kaldırma düğmesi bırakıldı.
- Cihaz sahibi durumu okunamıyorsa, yetki bırakılamıyorsa veya bırakma sonrasında
  hâlâ aktifse kaldırma durur. Hata günlüğe ve kullanıcıya bildirilir.
- Android kaldırma ekranından sonra zamanlanmış `pm uninstall` çalıştırılmaz.
  Kullanıcının iptali geçerlidir; ekranın açılması kaldırma başarısı sayılmaz.
- Cihaz sahibi yetkisini bırakmak kaldırma ekranından önce gerçekleşir. Kullanıcı
  sonradan iptal ederse bu yetki geri atanmaz; onay metni bunu açıklar.
- Önceki kurulumların değiştirdiği global/secure ayarlar geri alınmaz.
  Önceki değerler kaydedilmediği için tam geri dönüş garantisi verilemez.

### Betikler

`install.sh` artık açık cihaz seri numarası ve mevcut APK dosyası ister.
Sadece `adb -s SERIAL install -r APK` çalıştırır. Cihaz sahibi ataması,
geniş yetki dağıtımı, accessibility listesini değiştirme ve kalıcı sistem
property değişikliği yapmaz. Bu nedenle özel yetki isteyen özelliklerin
çalışacağı varsayılmamalıdır.

`uninstall.sh` sadece belirtilen cihazdaki `com.mapcontrol` paketini kaldırır.
Diğer APK dosyalarını silmez. Her iki betik de hata kodunu ve Android'in
`Success` yanıtını kontrol eder. Cihaz sahibi uygulamanın kaldırılması engellenirse
işlem durur; zorla kaldırma denenmez.

### Windows derlemesi

- Windows Gradle başlatıcısı eklendi.
- Tanımsız imzalama değişkeni giderildi. Gizli değerler git dışında kalan
  `local.properties` üzerinden okunur. Release anahtarı olmadan release APK
  imzasızdır; yayıncının anahtarı kullanılmaz.
- Debug sürüm adı: `1.2.4-local.1`; paket adı hâlâ `com.mapcontrol`.
  Yerel debug imzası yayıncı imzasından farklıdır; yayıncı APK'sının üzerine
  güncelleme olarak kurulamaz. İmza uyuşmazlığında betik durur, uygulamayı silmez.
- Var olan birim testleri için eksik JUnit bağımlılığı eklendi.
- İlk derlemede ayrıca upstream kaynakta eksik Wi-Fi durum metinleri, olmayan
  `segmentRemainMeters()` metodu ve tanımsız Sentry/BuildConfig bağımlılıkları
  bulundu. Wi-Fi metinleri Türkçe/İngilizce eklendi; mesafe dönüşümü m/km/ft/mi
  ve ondalık virgül/nokta için tamamlandı. Bilinmeyen veya taşan mesafeler,
  mevcut VDBus kodundaki boş mesafe değeri olan sıfıra dönüştürülür.
- Yerel dalda GlitchTip başlangıcı yalnızca Android günlüğüne bilgi yazar.
  Uzaktan çökme raporlama SDK'sı eklenmedi ve rapor gönderimi başlatılmaz.
  Bu değişiklik uygulamadaki diğer ağ özelliklerini kapatmaz.
- JDK 17, Gradle 8.2, Android SDK 34 ve Build Tools 34.0.0 kullanılır.
  `local.properties` içindeki `sdk.dir` bilgisayara özgüdür.

PowerShell: `./build-local.ps1` veya `./build-local.ps1 -JavaHome 'JDK17_YOLU'`.
Git Bash: `bash tests/installer-test.sh` (sadece sahte ADB kullanır).

## Sonraki inceleme alanları

1. `WebServerManager`: 7462 portunda tüm ağ arayüzlerini dinleyen HTTP sunucusu;
   APK yönetimi ve klavye girişi gibi işlevler için erişim kontrolü ele alınmalı.
2. Geniş manifest yetkileri, sessiz APK kurulumu ve araç servislerine erişim:
   yalnızca navigasyon ihtiyacına göre kapsam azaltımı değerlendirilmeli.
3. Cluster ekranının seçimi, ekran kaybı ve uygulama yaşam döngüsü:
   gerçek IVI üzerinde doğrulanmadı. DHU bu OEM entegrasyonunu doğrulamaz.
4. Upstream README'de belirtilen mikrofon/ses ve başlangıç sorunları bu çalışma
   kapsamında giderilmiş değildir.
5. `minSdk 29 / targetSdk 28` upstream uyumsuzluğu korundu; hedef API değişimi
   izin ve arka plan çalışma davranışlarını değiştireceğinden ayrı ele alınmalı.

Depoda açık kaynak lisansı bulunamadı. Kaynak kodunun görünür olması, yeniden
dağıtım koşullarını tek başına belirlemez. Bu çalışma yereldir; yayın/push yapılmadı.

## Doğrulama

- `assembleDebug testDebugUnitTest`: **başarılı**.
- **23 JVM birim testi:** 7 başlangıç durumu, 6 mevcut navigasyon ayrıştırma,
  6 kaldırma yetkisi, 4 mesafe dönüşümü; 0 hata, 0 başarısızlık.
- **10 kurulum/kaldırma betik senaryosu:** başarılı; sahte ADB ile eksik
  argüman/dosya, seri numarası, başarısız komut, yanıltıcı sıfır çıkış kodu
  ve CRLF başarı yanıtı kontrol edildi. Gerçek cihaza bağlantı yapılmadı.
- `git diff --check`: başarılı.
- APK: `app/build/outputs/apk/debug/app-debug.apk`, 73.088.134 bayt.
- Sürüm: `1.2.4-local.1`, paket: `com.mapcontrol`.
- SHA-256: `AC7B6A277BA3B20934F1E7323B23698EB21AAA158357DF1F25BD75EEFFE4F580`.
- Derleme günlüğü: yerel `build-local.log`; birim test raporu:
  `app/build/reports/tests/testDebugUnitTest/index.html`.
- Emülatör, DHU ve gerçek IVI çalışma testi **yapılmadı**. Sistem kaldırma
  ekranının OEM davranışı ve cluster görünümü birim testleriyle doğrulanamaz.
- Bu yerel dal araç kurulumuna hazır/onaylı olarak değerlendirilmemelidir.
