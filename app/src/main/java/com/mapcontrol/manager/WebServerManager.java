package com.mapcontrol.manager;
import android.content.Context;
import android.net.Uri;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileOutputStream;
import java.io.File;
import java.io.BufferedInputStream;
import com.mapcontrol.trip.AppBackupEnv;
import com.mapcontrol.trip.AutomationBackup;
import com.mapcontrol.trip.PhoneAccess;
import com.mapcontrol.trip.PhoneApiJson;
import com.mapcontrol.trip.PhonePrefs;
import com.mapcontrol.trip.TripRecorder;
import com.mapcontrol.vehicle.SunroofAutomationEngine;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import android.os.Environment;

import androidx.core.content.ContextCompat;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;

/**
 * Basit HTTP Server Manager
 * 7462 portunda HTTP servisi başlatır ve welcome sayfası gösterir
 */
public class WebServerManager {
    private static final int MAX_JSON_POST_BYTES = 65536;
    /** {@code PUT /automation} gövdesi için üst sınır. */
    private static final int MAX_AUTOMATION_BYTES = 32 * 1024;
    private static final int MAX_NAV_PLACES_BYTES = 4 * 1024;

    /** open-maps JSON içindeki url alanı: POST üst sınırına yakın; Yandex sctx gibi çok uzun paylaşım linklerine izin */
    private static final int MAX_OPEN_URL_CHARS = MAX_JSON_POST_BYTES - 200;
    private static final int MAX_KEYBOARD_TEXT_CHARS = 8000;
    private volatile ServerSocket serverSocket;
    private final NsdAdvertiser nsdAdvertiser;
    private volatile Thread serverThread;
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    /** Her başlatmada artar; kabul iş parçacığı yalnızca kendi kuşağı güncelken durum yazar. */
    private final ServerGeneration generation = new ServerGeneration();
    /**
     * Sunucunun açık olması istenir mi: {@link #startServer()} true, {@link #stopServer()} false yapar. Beklenmedik
     * düşüşte ({@link #isHealthy()} false iken) bu true kalır; {@link WebServerHost} bekçisi o zaman yeniden açar.
     * Elle kapatılan sunucu false olduğu için kendiliğinden açılmaz.
     */
    private volatile boolean desired;
    private int serverPort;
    private Context context;
    private WebServerListener listener;

    public interface WebServerListener {
        void onServerStarted(int port, String localIp);
        void onServerStopped();
        void onError(String error);
        void onInstallApk(String fileName);
        void onDeleteApp(String packageName);
        void onLaunchApp(String packageName);
        void onLog(String message);
        /** Araçtaki tarayıcı / uygun uygulama: {@link android.content.Intent#ACTION_VIEW} (http(s) için {@code BROWSABLE}). */
        void onOpenMapUrl(String url);
        /**
         * Odaklı metin alanına (erişilebilirlik) yazdırma.
         * @param showDeviceFeedback {@code true} ise araçta kısa başarı/hata tostu; anlık yazma için genelde {@code false}
         */
        void onTypeKeyboardText(String text, boolean showDeviceFeedback);
    }

    public WebServerManager(Context context) {
        this.context = context;
        this.nsdAdvertiser = new NsdAdvertiser(context);
    }

    public void setListener(WebServerListener listener) {
        this.listener = listener;
    }

    /**
     * HTTP server'ı başlat (7462 portunda)
     */
    public synchronized void startServer() {
        desired = true;
        if (isRunning.get()) {
            if (listener != null) listener.onLog("Server zaten çalışıyor");
            return;
        }

        try {
            // Sabit port kullan (7462)
            serverPort = 7462;

            // Hızlı yeniden başlatmada eski bağlantılar (TIME_WAIT) portu tutuyorsa "Address already in use" olmasın.
            ServerSocket socket = new ServerSocket();
            socket.setReuseAddress(true);
            try {
                socket.bind(new InetSocketAddress(serverPort));
            } catch (IOException e) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
                throw e;
            }
            serverSocket = socket;
            // Bu iş parçacığı yalnızca kendi kuşağını kapatır: durdurma geç biterse yeni sunucunun durumunu ezmez.
            final int gen = generation.next();
            isRunning.set(true);
            nsdAdvertiser.register(serverPort);
            
            serverThread = new Thread(() -> {
                try {
                    String localIp = getLocalIpAddress();
                    if (listener != null) {
                        listener.onServerStarted(serverPort, localIp);
                    }
                    
                    if (listener != null) listener.onLog("HTTP Server başlatıldı: http://" + localIp + ":" + serverPort);
                    
                    while (isRunning.get() && generation.isCurrent(gen) && !socket.isClosed()) {
                        try {
                            Socket clientSocket = socket.accept();
                            handleClient(clientSocket);
                        } catch (IOException e) {
                            if (isRunning.get() && generation.isCurrent(gen)) {
                                if (listener != null) listener.onLog("[ERROR] Client bağlantı hatası: " + e.getMessage());
                            }
                        }
                    }
                } catch (Exception e) {
                    if (listener != null) listener.onLog("[ERROR] Server hatası: " + e.getMessage());
                    if (listener != null) {
                        listener.onError("Server hatası: " + e.getMessage());
                    }
                } finally {
                    if (generation.isCurrent(gen)) {
                        isRunning.set(false);
                        if (listener != null) {
                            listener.onServerStopped();
                        }
                    }
                }
            });
            
            serverThread.start();
        } catch (IOException e) {
            if (listener != null) listener.onLog("[ERROR] Server başlatma hatası: " + e.getMessage());
            isRunning.set(false);
            if (listener != null) {
                listener.onError("Server başlatma hatası: " + e.getMessage());
            }
        }
    }

    /**
     * HTTP server'ı durdur
     */
    public synchronized void stopServer() {
        desired = false;
        stopInternal();
    }

    /** Durdurup yeniden açar; {@link #isDesired()} değişmez (bekçi kurtarması). */
    public synchronized void restart() {
        stopInternal();
        startServer();
    }

    /** Açık olması istenen sunucu şu an gerçekten çalışıyor mu: soket açık ve kabul döngüsü canlı. */
    public boolean isHealthy() {
        ServerSocket socket = serverSocket;
        Thread thread = serverThread;
        return isRunning.get() && socket != null && !socket.isClosed() && thread != null && thread.isAlive();
    }

    public boolean isDesired() {
        return desired;
    }

    /**
     * mDNS kaydını yeniden yapar (ağ değişti: Wi-Fi sonradan bağlandı, hotspot açıldı, adres değişti). Sunucu
     * kapalıysa bir şey yapmaz.
     */
    public void refreshAdvertisement() {
        if (isRunning.get()) {
            nsdAdvertiser.register(serverPort);
        }
    }

    private void stopInternal() {
        // Kabul döngüsü beklenmedik biçimde ölmüşse isRunning zaten false'tur ama soket hâlâ açık olabilir (port tutulur);
        // yeniden açmadan önce her durumda kapatılır.
        boolean wasRunning = isRunning.getAndSet(false);
        nsdAdvertiser.unregister();

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            if (listener != null) listener.onLog("[ERROR] Server kapatma hatası: " + e.getMessage());
        }

        if (serverThread != null) {
            try {
                serverThread.join(1000);
            } catch (InterruptedException e) {
                if (listener != null) listener.onLog("[ERROR] Server thread bekleme hatası: " + e.getMessage());
            }
        }

        if (wasRunning && listener != null) listener.onLog("HTTP Server durduruldu");
    }

    /**
     * HTTP istek satırı: {@code GET /yol?a=1 HTTP/1.1} veya mutlak form {@code GET http://host:port/yol?a=1 HTTP/1.1}.
     * @return [method, path, queryString] — sorgu yoksa üçüncü alan "".
     */
    private static String[] parseHttpRequestLine(String requestLine) {
        if (requestLine == null || requestLine.isEmpty()) {
            return null;
        }
        int sp1 = requestLine.indexOf(' ');
        if (sp1 < 0) {
            return null;
        }
        int sp2 = requestLine.indexOf(' ', sp1 + 1);
        if (sp2 < 0) {
            return null;
        }
        String method = requestLine.substring(0, sp1);
        String uriToken = requestLine.substring(sp1 + 1, sp2);
        if (uriToken.startsWith("http://") || uriToken.startsWith("https://")) {
            try {
                URL u = new URL(uriToken);
                String p = u.getPath();
                if (p == null || p.isEmpty()) {
                    p = "/";
                }
                String q = u.getQuery();
                return new String[] { method, p, q != null ? q : "" };
            } catch (Exception e) {
                return null;
            }
        }
        int queryIndex = uriToken.indexOf('?');
        if (queryIndex != -1) {
            return new String[] { method, uriToken.substring(0, queryIndex), uriToken.substring(queryIndex + 1) };
        }
        return new String[] { method, uriToken, "" };
    }

    /**
     * Client isteğini işle
     */
    private void handleClient(Socket clientSocket) {
        new Thread(() -> {
            try {
                // Socket timeout ayarla (30 dakika - çok büyük dosyalar için)
                clientSocket.setSoTimeout(1800000); // 30 dakika
                // Keep-alive ve buffer ayarları
                clientSocket.setTcpNoDelay(true);
                clientSocket.setKeepAlive(true);
                
                InputStream input = clientSocket.getInputStream();
                OutputStream output = clientSocket.getOutputStream();

                // HTTP header'ı oku
                StringBuilder headerBuilder = new StringBuilder();
                byte[] buffer = new byte[1];
                String lineEnding = "\r\n\r\n";
                String currentEnding = "";
                
                // Header'ı oku (boş satıra kadar)
                while (true) {
                    int read = input.read(buffer);
                    if (read == -1) {
                        clientSocket.close();
                        return;
                    }
                    char c = (char) buffer[0];
                    headerBuilder.append(c);
                    currentEnding += c;
                    if (currentEnding.length() > 4) {
                        currentEnding = currentEnding.substring(1);
                    }
                    if (currentEnding.equals(lineEnding)) {
                        break;
                    }
                }

                String requestHeader = headerBuilder.toString();
                String[] requestLines = requestHeader.split("\r\n");
                if (requestLines.length == 0) {
                    clientSocket.close();
                    return;
                }

                String requestLine = requestLines[0];
                String[] parsed = parseHttpRequestLine(requestLine);
                if (parsed == null) {
                    clientSocket.close();
                    return;
                }
                String method = parsed[0];
                String path = parsed[1];
                String queryString = parsed[2] != null ? parsed[2] : "";

                if (listener != null) listener.onLog("HTTP Request: " + method + " " + path);

                if ("OPTIONS".equals(method)) {
                    sendOptionsResponse(output);
                } else if ("POST".equals(method) && "/upload".equals(path)) {
                    handleFileUpload(input, output, requestHeader);
                } else if ("POST".equals(method) && "/action/open-maps".equals(path)) {
                    handleOpenMaps(input, output, requestHeader);
                } else if ("POST".equals(method) && "/action/keyboard".equals(path)) {
                    handleKeyboard(input, output, requestHeader);
                } else if ("GET".equals(method) && "/files".equals(path)) {
                    handleFileList(output);
                } else if ("DELETE".equals(method) && "/files".equals(path)) {
                    handleFileDelete(output, queryString);
                } else if ("POST".equals(method) && "/install".equals(path)) {
                    handleInstallApk(output, queryString);
                } else if ("GET".equals(method) && "/trips".equals(path)) {
                    handlePhoneTrips(output, requestHeader, queryString);
                } else if ("GET".equals(method) && "/parking".equals(path)) {
                    handlePhoneParking(output, requestHeader);
                } else if ("GET".equals(method) && "/parking/photo".equals(path)) {
                    handlePhoneParkingPhoto(output, requestHeader);
                } else if ("GET".equals(method) && "/prefs".equals(path)) {
                    handlePhonePrefsGet(output, requestHeader);
                } else if ("PUT".equals(method) && "/prefs".equals(path)) {
                    handlePhonePrefsPut(input, output, requestHeader);
                } else if ("GET".equals(method) && "/nav/places".equals(path)) {
                    handleNavPlacesGet(output, requestHeader);
                } else if ("PUT".equals(method) && "/nav/places".equals(path)) {
                    handleNavPlacesPut(input, output, requestHeader);
                } else if ("GET".equals(method) && "/automation".equals(path)) {
                    handleAutomationGet(output, requestHeader);
                } else if ("PUT".equals(method) && "/automation".equals(path)) {
                    handleAutomationPut(input, output, requestHeader);
                } else if ("GET".equals(method) && "/cloud-token".equals(path)) {
                    handleCloudTokenGet(output, requestHeader);
                } else if ("PUT".equals(method) && "/cloud-token".equals(path)) {
                    handleCloudTokenPut(input, output, requestHeader);
                } else if ("DELETE".equals(method) && "/cloud-token".equals(path)) {
                    handleCloudTokenDelete(output, requestHeader);
                } else if ("GET".equals(method) && "/ping".equals(path)) {
                    handlePing(output);
                } else if ("GET".equals(method) && "/vehicle/now".equals(path)) {
                    handlePhoneVehicleNow(output, requestHeader);
                } else if ("GET".equals(method) && "/apps".equals(path)) {
                    handleAppList(output);
                } else if ("POST".equals(method) && "/app/delete".equals(path)) {
                    handleAppDelete(output, queryString);
                } else if ("POST".equals(method) && "/app/launch".equals(path)) {
                    handleAppLaunch(output, queryString);
                } else if ("GET".equals(method) || "HEAD".equals(method)) {
                    String response = generateWelcomePage();
                    String httpResponse = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/html; charset=UTF-8\r\n" +
                            "Content-Length: " + response.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                            "Connection: close\r\n\r\n" +
                            response;
                    output.write(httpResponse.getBytes(StandardCharsets.UTF_8));
                    output.flush();
                } else {
                    sendErrorResponse(output, 405, "Method not allowed");
                }
                clientSocket.close();

            } catch (IOException e) {
                if (listener != null) listener.onLog("[ERROR] Client işleme hatası: " + e.getMessage());
                try {
                    clientSocket.close();
                } catch (IOException ex) {
                    // Ignore
                }
            }
        }).start();
    }

    /**
     * Welcome sayfası HTML'i oluştur (assets'ten dosyayı oku)
     */
    private String generateWelcomePage() {
        try {
            InputStream inputStream = context.getAssets().open("welcome.html");
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            StringBuilder html = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                html.append(line).append("\n");
            }
            reader.close();
            inputStream.close();
            
            // Placeholder'ı değiştir
            String localIp = getLocalIpAddress();
            String serverUrl = "http://" + localIp + ":" + serverPort;
            return html.toString().replace("{{SERVER_URL}}", serverUrl);
        } catch (IOException e) {
            if (listener != null) listener.onLog("[ERROR] HTML dosyası okuma hatası: " + e.getMessage());
            // Fallback: basit HTML
            String localIp = getLocalIpAddress();
            String bg = String.format("#%06X", 0xFFFFFF & UiStyles.color(context, R.color.backgroundPage));
            String fg = String.format("#%06X", 0xFFFFFF & UiStyles.color(context, R.color.textPrimary));
            return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>MapControl</title></head>" +
                   "<body style=\"font-family: sans-serif; background: " + bg + "; color: " + fg + "; padding: 40px; text-align: center;\">" +
                   "<h1>Hoş Geldiniz!</h1>" +
                   "<p>MapControl Web Yönetimi</p>" +
                   "<p><code>http://" + localIp + ":" + serverPort + "</code></p>" +
                   "</body></html>";
        }
    }

    /**
     * Dosya yükleme işlemini handle et (deterministik multipart parsing)
     * - Header okuma body'den byte çalmaz
     * - Content-Length zorunlu
     * - Body'yi Content-Length kadar oku, boundary'yi body içinde ara
     * - Dosya offset'lerini deterministik şekilde bul
     */
    private void handleFileUpload(InputStream input, OutputStream output, String requestHeader) {
        FileOutputStream fileOutputStream = null;
        File file = null;
        
        try {
            // Header'ları parse et
            String[] headerLines = requestHeader.split("\r\n");
            
            // Content-Length zorunlu
            long contentLength = -1;
            String contentType = null;
            boolean hasTransferEncoding = false;
            String transferEncoding = null;
            
            for (String line : headerLines) {
                String lowerLine = line.toLowerCase();
                if (lowerLine.startsWith("content-length:")) {
                    try {
                        contentLength = Long.parseLong(line.substring(15).trim());
                    } catch (NumberFormatException e) {
                        if (listener != null) listener.onLog("[ERROR] Geçersiz Content-Length: " + line);
                        sendErrorResponse(output, 400, "Geçersiz Content-Length");
                        return;
                    }
                } else if (lowerLine.startsWith("content-type:")) {
                    contentType = line.substring(13).trim();
                } else if (lowerLine.startsWith("transfer-encoding:")) {
                    hasTransferEncoding = true;
                    transferEncoding = line.substring(18).trim().toLowerCase();
                }
            }
            
            // Debug: Header bilgilerini logla
            if (listener != null) {
                listener.onLog("Upload Header - Content-Length: " + contentLength + 
                             ", Content-Type: " + contentType + 
                             ", Transfer-Encoding: " + (hasTransferEncoding ? transferEncoding : "yok"));
            }
            
            // Content-Length kontrolü
            if (contentLength <= 0) {
                if (listener != null) listener.onLog("[ERROR] Content-Length bulunamadı veya 0");
                sendErrorResponse(output, 411, "Content-Length gerekli");
                return;
            }
            
            // Transfer-Encoding: chunked kontrolü
            if (hasTransferEncoding && "chunked".equals(transferEncoding)) {
                if (listener != null) listener.onLog("[ERROR] Transfer-Encoding: chunked desteklenmiyor");
                sendErrorResponse(output, 501, "Transfer-Encoding: chunked desteklenmiyor");
                return;
            }
            
            // Boundary'yi bul
            String boundary = null;
            if (contentType != null && contentType.toLowerCase().startsWith("multipart/form-data")) {
                int boundaryIndex = contentType.indexOf("boundary=");
                if (boundaryIndex != -1) {
                    String boundaryValue = contentType.substring(boundaryIndex + 9);
                    // Boundary değeri tırnak içinde olabilir
                    if (boundaryValue.startsWith("\"") && boundaryValue.endsWith("\"")) {
                        boundaryValue = boundaryValue.substring(1, boundaryValue.length() - 1);
                    }
                    boundary = "--" + boundaryValue;
                }
            }
            
            if (boundary == null) {
                if (listener != null) listener.onLog("[ERROR] Boundary bulunamadı");
                sendErrorResponse(output, 400, "Boundary bulunamadı");
                return;
            }
            
            if (listener != null) listener.onLog("Boundary: " + boundary);

            // Gövde akış olarak işlenir: dosya belleğe alınmadan diske yazılır (GlitchTip #2301 OOM).
            MultipartStream.Limited body = new MultipartStream.Limited(
                    new BufferedInputStream(input, MultipartStream.BUF), contentLength);

            byte[] headerBytes = MultipartStream.readPartHeader(body, 16 * 1024);
            if (headerBytes == null) {
                if (listener != null) listener.onLog("[ERROR] Body içinde \\r\\n\\r\\n bulunamadı");
                sendErrorResponse(output, 400, "Multipart header bulunamadı");
                return;
            }
            String partHeader = new String(headerBytes, StandardCharsets.UTF_8);
            String fileName = MultipartStream.fileName(partHeader);
            if (fileName == null) {
                if (listener != null) listener.onLog("[ERROR] Filename bulunamadı");
                sendErrorResponse(output, 400, "Filename bulunamadı");
                return;
            }

            if (listener != null) listener.onLog("Filename: " + fileName);

            // Download klasörüne kaydet
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadDir.exists()) {
                boolean created = downloadDir.mkdirs();
                if (!created && listener != null) {
                    listener.onLog("[WARN] Download klasörü oluşturulamadı: " + downloadDir.getAbsolutePath());
                }
            }

            file = new File(downloadDir, fileName);
            // Aynı isimde dosya varsa numara ekle
            int counter = 1;
            int dotIndex = fileName.lastIndexOf('.');
            String nameWithoutExt = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
            String ext = dotIndex > 0 ? fileName.substring(dotIndex) : "";

            while (file.exists()) {
                fileName = nameWithoutExt + " (" + counter + ")" + ext;
                file = new File(downloadDir, fileName);
                counter++;
            }

            // Dosya içeriği "\r\n--boundary"a kadar diske akar.
            byte[] boundaryBytes = ("\r\n" + boundary).getBytes(StandardCharsets.UTF_8);
            long fileDataLength;
            try {
                fileOutputStream = new FileOutputStream(file);
                fileDataLength = MultipartStream.copyUntil(body, boundaryBytes, fileOutputStream);
                fileOutputStream.flush();
                fileOutputStream.close();
                fileOutputStream = null;
            } catch (Exception e) {
                if (fileOutputStream != null) {
                    try {
                        fileOutputStream.close();
                    } catch (Exception ex) {
                        // Ignore
                    }
                    fileOutputStream = null;
                }
                file.delete();
                if (listener != null) listener.onLog("[ERROR] Dosya yazma hatası: " + e.getMessage());
                sendErrorResponse(output, 500, "Dosya yazma hatası: " + e.getMessage());
                return;
            }

            if (fileDataLength < 0) {
                file.delete();
                if (listener != null) listener.onLog("[ERROR] Boundary body içinde bulunamadı");
                sendErrorResponse(output, 400, "Boundary bulunamadı");
                return;
            }
            if (fileDataLength == 0) {
                file.delete();
                if (listener != null) listener.onLog("[ERROR] Dosya içeriği boş");
                sendErrorResponse(output, 400, "Dosya içeriği boş");
                return;
            }

            // Kapanış ayıracı ve kalan gövde okunur; okunan toplam Content-Length ile karşılaştırılır.
            body.drain();
            long totalBodyRead = body.consumed();
            if (listener != null) {
                listener.onLog("Body okundu: " + totalBodyRead + " bytes (Content-Length: " + contentLength
                        + "), dosya: " + fileDataLength + " bytes");
            }

            // Dosya yazımını doğrula
            long actualFileSize = file.length();
            if (actualFileSize != fileDataLength) {
                if (listener != null) {
                    listener.onLog("[ERROR] Dosya boyutu uyuşmazlığı - Beklenen: " + fileDataLength + 
                                 ", Gerçek: " + actualFileSize);
                }
                file.delete();
                sendErrorResponse(output, 500, "Dosya yazma hatası: Boyut uyuşmazlığı");
                return;
            }
            
            if (listener != null) {
                listener.onLog("Dosya kaydedildi: " + file.getAbsolutePath() + 
                             " (Content-Length: " + contentLength + 
                             ", Body okunan: " + totalBodyRead + 
                             ", Dosya yazılan: " + fileDataLength + 
                             ", Final file.length(): " + actualFileSize + ")");
            }
            
            // Zorunlu doğrulamalar
            // 1. Okunan byte sayısı kontrolü
            if (totalBodyRead != contentLength) {
                if (listener != null) {
                    listener.onLog("[ERROR] Okunan byte != Content-Length: " + totalBodyRead + " != " + contentLength);
                }
                file.delete();
                sendErrorResponse(output, 500, "Body okuma hatası");
                return;
            }
            
            // 2. APK için hızlı kontrol
            if (fileName.toLowerCase().endsWith(".apk")) {
                try {
                    if (actualFileSize < 2) {
                        if (listener != null) listener.onLog("[ERROR] APK dosyası çok küçük: " + actualFileSize + " bytes");
                        file.delete();
                        sendErrorResponse(output, 500, "APK dosyası bozuk: Dosya çok küçük");
                        return;
                    }
                    
                    // İlk 2 byte "PK" olmalı
                    java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r");
                    byte[] apkHeader = new byte[2];
                    raf.read(apkHeader);
                    raf.close();
                    
                    if (apkHeader[0] != 0x50 || apkHeader[1] != 0x4B) {
                        if (listener != null) {
                            listener.onLog("[ERROR] APK ZIP signature yok - İlk 2 byte: " + 
                                        String.format("%02X %02X", apkHeader[0], apkHeader[1]) + 
                                        ", Dosya boyutu: " + actualFileSize);
                        }
                        file.delete();
                        sendErrorResponse(output, 500, "APK dosyası bozuk: Geçersiz ZIP signature");
                        return;
                    }
                    
                    if (listener != null) {
                        listener.onLog("APK doğrulama başarılı: ZIP signature geçerli (" + actualFileSize + " bytes)");
                    }
                } catch (Exception e) {
                    if (listener != null) listener.onLog("[ERROR] APK doğrulama hatası: " + e.getMessage());
                    file.delete();
                    sendErrorResponse(output, 500, "APK doğrulama hatası");
                    return;
                }
            }
            
            // Başarı yanıtı gönder
            String successResponse = "Dosya başarıyla yüklendi: " + fileName;
            String httpResponse = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/plain; charset=UTF-8\r\n" +
                    "Content-Length: " + successResponse.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                    "Connection: close\r\n\r\n" +
                    successResponse;
            output.write(httpResponse.getBytes(StandardCharsets.UTF_8));
            output.flush();

        } catch (Exception e) {
            if (listener != null) listener.onLog("[ERROR] Dosya yükleme hatası: " + e.getMessage());
            e.printStackTrace();
            if (file != null && file.exists()) {
                file.delete(); // Hatalı dosyayı sil
            }
            sendErrorResponse(output, 500, "Dosya yükleme hatası: " + e.getMessage());
        } finally {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    if (listener != null) listener.onLog("[ERROR] FileOutputStream kapatma hatası: " + e.getMessage());
                }
            }
        }
    }

    private void sendOptionsResponse(OutputStream output) {
        try {
            String r = "HTTP/1.1 204 No Content\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Access-Control-Allow-Methods: GET, POST, PUT, DELETE, HEAD, OPTIONS\r\n" +
                    "Access-Control-Allow-Headers: Content-Type, " + PhoneAccess.HEADER + "\r\n" +
                    "Access-Control-Max-Age: 86400\r\n" +
                    "Connection: close\r\n\r\n";
            output.write(r.getBytes(StandardCharsets.UTF_8));
            output.flush();
        } catch (IOException e) {
            if (listener != null) listener.onLog("[ERROR] OPTIONS yanıtı: " + e.getMessage());
        }
    }

    private int parseContentLengthFromHeaders(String requestHeader) {
        if (requestHeader == null) {
            return -1;
        }
        for (String line : requestHeader.split("\r\n")) {
            String l = line.trim();
            if (l.isEmpty()) {
                break;
            }
            if (l.toLowerCase().startsWith("content-length:")) {
                try {
                    return Integer.parseInt(l.substring(15).trim());
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }

    private byte[] readFixedBodyFromStream(InputStream input, int contentLength) throws IOException {
        if (contentLength < 0) {
            throw new IOException("Content-Length yok");
        }
        if (contentLength > MAX_JSON_POST_BYTES) {
            throw new IOException("İstek çok büyük");
        }
        byte[] buf = new byte[contentLength];
        int off = 0;
        while (off < contentLength) {
            int n = input.read(buf, off, contentLength - off);
            if (n == -1) {
                throw new IOException("Beklenmeyen son");
            }
            off += n;
        }
        return buf;
    }

    /**
     * Web textarea'dan gelen paylaşım linki: sınır ve görünmez karakterler, istenmeyen satır kırılmaları.
     */
    private static String normalizeOpenMapUrlString(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim()
                .replace("\u200B", "")
                .replace("\uFEFF", "")
                .replaceAll("[\r\n]+", "");
    }

    /**
     * Araçta {@link android.content.Intent#ACTION_VIEW} ile açılabilecek linkler.
     * Tüm http ve https URI'ları (en az bir host ile); kısa link, yönlendirme, arama, harita sitesi — sistem uygun uygulamayı veya tarayıcıyı açar.
     * Ayrıca {@code geo:} ve {@code google.navigation:}.
     * Engelli şemalar: {@code javascript}, {@code file}, {@code data}, {@code content}.
     */
    static boolean isAllowedMapUrl(String raw) {
        if (raw == null) {
            return false;
        }
        String t = raw.trim();
        if (t.isEmpty() || t.length() > MAX_OPEN_URL_CHARS) {
            return false;
        }
        Uri u = Uri.parse(t);
        String scheme = u.getScheme();
        if (scheme == null) {
            return false;
        }
        String s = scheme.toLowerCase();
        if ("javascript".equals(s) || "file".equals(s) || "data".equals(s) || "content".equals(s)) {
            return false;
        }
        if ("geo".equals(s) || "google.navigation".equals(s)) {
            return true;
        }
        if ("http".equals(s) || "https".equals(s)) {
            String host = u.getHost();
            return host != null && !host.isEmpty();
        }
        return false;
    }

    private void handleOpenMaps(InputStream input, OutputStream output, String requestHeader) {
        try {
            int cl = parseContentLengthFromHeaders(requestHeader);
            if (cl < 0) {
                sendErrorResponse(output, 400, "Content-Length gerekli");
                return;
            }
            byte[] body = readFixedBodyFromStream(input, cl);
            JSONObject o = new JSONObject(new String(body, StandardCharsets.UTF_8));
            if (!o.has("url")) {
                sendErrorResponse(output, 400, "url alanı gerekli");
                return;
            }
            String url = normalizeOpenMapUrlString(o.getString("url"));
            if (!isAllowedMapUrl(url)) {
                if (listener != null) {
                    listener.onLog("[WARN] Engellenen link: " + url);
                }
                sendErrorResponse(output, 400, "Geçersiz veya izin verilmeyen link (http/https, geo, google.navigation; javascript/file/data/content yok)");
                return;
            }
            if (listener == null) {
                sendErrorResponse(output, 500, "Listener yok");
                return;
            }
            listener.onOpenMapUrl(url);
            if (listener != null) {
                listener.onLog("Link isteği: " + url);
            }
            String json = new JSONObject().put("ok", true).put("url", url).toString();
            sendJson200(output, json);
        } catch (Exception e) {
            if (listener != null) {
                listener.onLog("[ERROR] open-maps: " + e.getMessage());
            }
            sendErrorResponse(output, 400, "open-maps: " + e.getMessage());
        }
    }

    private void handleKeyboard(InputStream input, OutputStream output, String requestHeader) {
        try {
            int cl = parseContentLengthFromHeaders(requestHeader);
            if (cl < 0) {
                sendErrorResponse(output, 400, "Content-Length gerekli");
                return;
            }
            byte[] body = readFixedBodyFromStream(input, cl);
            JSONObject o = new JSONObject(new String(body, StandardCharsets.UTF_8));
            if (!o.has("text")) {
                sendErrorResponse(output, 400, "text alanı gerekli");
                return;
            }
            String text = o.getString("text");
            if (text.length() > MAX_KEYBOARD_TEXT_CHARS) {
                sendErrorResponse(output, 400, "Metin çok uzun (max " + MAX_KEYBOARD_TEXT_CHARS + ")");
                return;
            }
            if (listener == null) {
                sendErrorResponse(output, 500, "Listener yok");
                return;
            }
            boolean showDeviceFeedback = o.optBoolean("notify", true);
            listener.onTypeKeyboardText(text, showDeviceFeedback);
            if (listener != null) {
                if (showDeviceFeedback) {
                    listener.onLog("Klavye metni " + text.length() + " karakter");
                }
            }
            String json = new JSONObject().put("ok", true).toString();
            sendJson200(output, json);
        } catch (Exception e) {
            if (listener != null) {
                listener.onLog("[ERROR] keyboard: " + e.getMessage());
            }
            sendErrorResponse(output, 400, "keyboard: " + e.getMessage());
        }
    }

    /**
     * Telefon uçları: kişisel veri verir, yalnızca eşleşme anahtarını gösteren telefona açıktır.
     * @return yetki yoksa 401 yazıp {@code false}
     */
    private boolean requirePairedPhone(OutputStream output, String requestHeader) {
        if (PhoneAccess.isAuthorized(context, requestHeader)) {
            return true;
        }
        sendErrorResponse(output, 401, "Eşleşmemiş telefon");
        return false;
    }

    /** {@code GET /trips?since=<epoch ms>}: since sonrasında başlayan yolculuklar, eskiden yeniye. */
    private void handlePhoneTrips(OutputStream output, String requestHeader, String queryString) {
        if (!requirePairedPhone(output, requestHeader)) {
            return;
        }
        try {
            long since = 0;
            if (queryString != null) {
                for (String param : queryString.split("&")) {
                    String[] kv = param.split("=");
                    if (kv.length == 2 && "since".equals(kv[0])) {
                        since = Long.parseLong(kv[1]);
                    }
                }
            }
            sendJson200(output, TripRecorder.get(context).tripsJson(since));
        } catch (NumberFormatException e) {
            sendErrorResponse(output, 400, "since sayı olmalı (epoch ms)");
        } catch (Exception e) {
            if (listener != null) listener.onLog("[ERROR] /trips: " + e.getMessage());
            sendErrorResponse(output, 500, "Yolculuk listesi hatası: " + e.getMessage());
        }
    }

    /** {@code GET /parking}: son park fotoğrafının zamanı ve yolculuğu; fotoğraf yoksa {@code {"photo":null}}. */
    private void handlePhoneParking(OutputStream output, String requestHeader) {
        if (!requirePairedPhone(output, requestHeader)) {
            return;
        }
        try {
            sendJson200(output, TripRecorder.get(context).parkingJson());
        } catch (Exception e) {
            if (listener != null) listener.onLog("[ERROR] /parking: " + e.getMessage());
            sendErrorResponse(output, 500, "Park bilgisi hatası: " + e.getMessage());
        }
    }

    /** {@code GET /parking/photo}: son park fotoğrafı (JPEG); yoksa 404. */
    private void handlePhoneParkingPhoto(OutputStream output, String requestHeader) {
        if (!requirePairedPhone(output, requestHeader)) {
            return;
        }
        try {
            File photo = com.mapcontrol.dvr.ParkPhoto.photoFile(context);
            if (com.mapcontrol.dvr.ParkPhoto.meta(context) == null) {
                sendErrorResponse(output, 404, "Park fotoğrafı yok");
                return;
            }
            byte[] bytes = java.nio.file.Files.readAllBytes(photo.toPath());
            String head = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: image/jpeg\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Content-Length: " + bytes.length + "\r\n" +
                    "Connection: close\r\n\r\n";
            output.write(head.getBytes(StandardCharsets.UTF_8));
            output.write(bytes);
            output.flush();
        } catch (Exception e) {
            if (listener != null) listener.onLog("[ERROR] /parking/photo: " + e.getMessage());
            sendErrorResponse(output, 500, "Park fotoğrafı hatası: " + e.getMessage());
        }
    }

    /** {@code GET /prefs}: telefonun gördüğü boolean MapControl ayarları (aracın şu anki değerleri). */
    private void handlePhonePrefsGet(OutputStream output, String requestHeader) {
        if (!requirePairedPhone(output, requestHeader)) {
            return;
        }
        try {
            sendJson200(output, PhonePrefs.toJson(readPhonePrefs()));
        } catch (Exception e) {
            sendErrorResponse(output, 500, "Ayarlar okunamadı: " + e.getMessage());
        }
    }

    /**
     * {@code PUT /prefs} gövde {@code {"prefs":{"bootAutoStart":true}}}: yalnızca yazılabilir anahtarlar,
     * biri bile bilinmiyorsa hiçbiri yazılmaz. Yanıt güncel ayarlardır.
     */
    private void handlePhonePrefsPut(InputStream input, OutputStream output, String requestHeader) {
        if (!requirePairedPhone(output, requestHeader)) {
            return;
        }
        try {
            int cl = parseContentLengthFromHeaders(requestHeader);
            if (cl < 0) {
                sendErrorResponse(output, 400, "Content-Length gerekli");
                return;
            }
            JSONObject root = new JSONObject(new String(readFixedBodyFromStream(input, cl), StandardCharsets.UTF_8));
            JSONObject prefs = root.optJSONObject("prefs");
            Map<String, Boolean> incoming = new LinkedHashMap<>();
            if (prefs != null) {
                java.util.Iterator<String> keys = prefs.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    Object value = prefs.get(key);
                    // Yalnızca gerçek boolean; "true" yazısı veya 1 kabul edilmez.
                    incoming.put(key, value instanceof Boolean ? (Boolean) value : null);
                }
            }
            Map<String, Boolean> accepted = PhonePrefs.acceptable(incoming);
            if (accepted == null) {
                sendErrorResponse(output, 400, "prefs_invalid");
                return;
            }
            android.content.SharedPreferences.Editor edit =
                    context.getSharedPreferences("MapControlPrefs", Context.MODE_PRIVATE).edit();
            for (Map.Entry<String, Boolean> e : accepted.entrySet()) {
                edit.putBoolean(e.getKey(), e.getValue());
            }
            edit.commit();
            if (listener != null) listener.onLog("Telefondan ayar yazıldı: " + accepted.keySet());
            sendJson200(output, PhonePrefs.toJson(readPhonePrefs()));
        } catch (Exception e) {
            sendErrorResponse(output, 400, "prefs: " + e.getMessage());
        }
    }

    /** {@code GET /nav/places}: launcher'ın Ev / İş konumları; bkz. {@link com.mapcontrol.launcher.NavPlacesDoc}. */
    private void handleNavPlacesGet(OutputStream output, String requestHeader) {
        if (!requirePairedPhone(output, requestHeader)) {
            return;
        }
        try {
            sendJson200(output, com.mapcontrol.launcher.NavPlacesDoc.toJson(
                    com.mapcontrol.launcher.NavPlaces.readAll(context)).toString());
        } catch (Exception e) {
            sendErrorResponse(output, 500, "nav_places: " + e.getMessage());
        }
    }

    /**
     * {@code PUT /nav/places}: belgedeki yer yazılır, {@code null} olan silinir, olmayana dokunulmaz. Tanınan yerin değeri
     * geçersizse hiçbiri yazılmaz ({@code 422 nav_places_invalid: home}). Yanıt güncel belgedir. Konum kişisel veridir:
     * günlüğe yalnızca hangi yerin değiştiği yazılır.
     */
    private void handleNavPlacesPut(InputStream input, OutputStream output, String requestHeader) {
        if (!requirePairedPhone(output, requestHeader)) {
            return;
        }
        try {
            int cl = parseContentLengthFromHeaders(requestHeader);
            if (cl < 0) {
                sendErrorResponse(output, 400, "Content-Length gerekli");
                return;
            }
            if (cl > MAX_NAV_PLACES_BYTES) {
                sendErrorResponse(output, 413, "nav_places_too_large");
                return;
            }
            JSONObject root = new JSONObject(new String(readFixedBodyFromStream(input, cl), StandardCharsets.UTF_8));
            List<String> errors = new ArrayList<>();
            Map<String, com.mapcontrol.launcher.NavPlacesDoc.Place> changes =
                    com.mapcontrol.launcher.NavPlacesDoc.parse(root, errors);
            if (changes == null) {
                sendErrorResponse(output, 422, "nav_places_invalid: " + android.text.TextUtils.join(",", errors));
                return;
            }
            com.mapcontrol.launcher.NavPlaces.writeAll(context, changes);
            if (listener != null && !changes.isEmpty()) listener.onLog("Telefondan konum yazıldı: " + changes.keySet());
            sendJson200(output, com.mapcontrol.launcher.NavPlacesDoc.toJson(
                    com.mapcontrol.launcher.NavPlaces.readAll(context)).toString());
        } catch (JSONException e) {
            sendErrorResponse(output, 400, "nav_places: " + e.getMessage());
        } catch (Exception e) {
            sendErrorResponse(output, 400, "nav_places: " + e.getMessage());
        }
    }

    /**
     * Cam tavan, klima, hafıza modu ve MapControl tercihlerinin yedek belgesi; bkz. {@link AutomationBackup}.
     * Yanıta belge dışı bir bilgi eklenir: {@code pristine} (araçta yedeğe girecek hiçbir ayar yazılı değil).
     * Telefon bunu belgeden ayırır; hesaba gitmez.
     */
    private void handleAutomationGet(OutputStream output, String requestHeader) {
        if (!requirePairedPhone(output, requestHeader)) {
            return;
        }
        try {
            android.content.SharedPreferences main = context.getSharedPreferences("MapControlPrefs", Context.MODE_PRIVATE);
            android.content.SharedPreferences sunroof =
                    context.getSharedPreferences(SunroofAutomationEngine.PREFS_NAME, Context.MODE_PRIVATE);
            AppBackupEnv env = new AppBackupEnv(context);
            JSONObject doc = AutomationBackup.export(main, sunroof, env);
            doc.put("pristine", AutomationBackup.isPristine(main, sunroof, env));
            sendJson200(output, doc.toString());
        } catch (Exception e) {
            sendErrorResponse(output, 500, "Otomasyon okunamadı: " + e.getMessage());
        }
    }

    /**
     * {@code PUT /automation}: yedek belgesini doğrular ve yazar; tek alan geçersizse hiçbiri yazılmaz
     * ({@code 422}, gövde geçersiz alanların listesi). Yanıt güncel belgedir.
     */
    private void handleAutomationPut(InputStream input, OutputStream output, String requestHeader) {
        if (!requirePairedPhone(output, requestHeader)) {
            return;
        }
        try {
            int cl = parseContentLengthFromHeaders(requestHeader);
            if (cl < 0) {
                sendErrorResponse(output, 400, "Content-Length gerekli");
                return;
            }
            if (cl > MAX_AUTOMATION_BYTES) {
                sendErrorResponse(output, 413, "automation_too_large");
                return;
            }
            JSONObject doc = new JSONObject(new String(readFixedBodyFromStream(input, cl), StandardCharsets.UTF_8));
            android.content.SharedPreferences main = context.getSharedPreferences("MapControlPrefs", Context.MODE_PRIVATE);
            android.content.SharedPreferences sunroof =
                    context.getSharedPreferences(SunroofAutomationEngine.PREFS_NAME, Context.MODE_PRIVATE);
            AppBackupEnv env = new AppBackupEnv(context);
            AutomationBackup.Result result = AutomationBackup.apply(doc, main, sunroof, env);
            if (!result.ok()) {
                sendErrorResponse(output, 422, "automation_invalid: " + android.text.TextUtils.join(",", result.errors));
                return;
            }
            // Klima derece ölçeği bellekte tutulur; geri yüklenen tercih hemen geçerli olsun.
            com.mapcontrol.launcher.HomeScreenPrefs.applyClimateScale(context);
            if (listener != null) listener.onLog("Telefondan ayarlar geri yüklendi");
            // Yanıt güncel belgedir; bu araçta uygulanmayan alanlar belge dışı "skipped" olarak eklenir.
            JSONObject fresh = AutomationBackup.export(main, sunroof, env);
            fresh.put("skipped", new org.json.JSONArray(result.skipped));
            sendJson200(output, fresh.toString());
        } catch (JSONException e) {
            sendErrorResponse(output, 400, "automation: " + e.getMessage());
        } catch (Exception e) {
            sendErrorResponse(output, 400, "automation: " + e.getMessage());
        }
    }

    private Map<String, Boolean> readPhonePrefs() {
        android.content.SharedPreferences sp = context.getSharedPreferences("MapControlPrefs", Context.MODE_PRIVATE);
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (Map.Entry<String, Boolean> e : PhonePrefs.DEFAULTS.entrySet()) {
            out.put(e.getKey(), sp.getBoolean(e.getKey(), e.getValue()));
        }
        return out;
    }

    /**
     * {@code GET /ping}: araç kimliği, protokol sürümü ve GPS Link portu. Telefon, adresi değişen aracı bununla
     * tanır ve bağlantının canlı olduğunu hafif bir istekle ölçer. Eşleşme anahtarı istemez; kişisel veri vermez.
     */
    /** {@code GET /cloud-token}: araçtaki sunucu anahtarının durumu (anahtarın kendisi verilmez). */
    private void handleCloudTokenGet(OutputStream output, String requestHeader) {
        if (!requirePairedPhone(output, requestHeader)) {
            return;
        }
        try {
            sendJson200(output, com.mapcontrol.api.CloudToken.statusJson(context));
        } catch (Exception e) {
            sendErrorResponse(output, 500, "cloud-token: " + e.getMessage());
        }
    }

    /**
     * {@code PUT /cloud-token}, gövde {@code {"token": "...", "expiresAtMs": 0, "vin": "..."}}: telefonun sunucudan
     * aldığı araca özel token. Araç bununla yolculuk ve park fotoğrafını hesabın sunucu kutusuna bırakır; araçta
     * hesaba giriş yoktur. Şase araçtakiyle çelişirse {@code 409 vin_mismatch}.
     */
    private void handleCloudTokenPut(InputStream input, OutputStream output, String requestHeader) {
        if (!requirePairedPhone(output, requestHeader)) {
            return;
        }
        try {
            int cl = parseContentLengthFromHeaders(requestHeader);
            if (cl < 0 || cl > 8192) {
                sendErrorResponse(output, 400, "token_invalid");
                return;
            }
            JSONObject root = new JSONObject(new String(readFixedBodyFromStream(input, cl), StandardCharsets.UTF_8));
            String error = com.mapcontrol.api.CloudToken.store(context, root.optString("token", null),
                    root.optLong("expiresAtMs", 0L), root.optString("vin", null),
                    com.mapcontrol.trip.TripRecorder.get(context).vinOrEmpty());
            if (error != null) {
                sendErrorResponse(output, "vin_mismatch".equals(error) ? 409 : 400, error);
                return;
            }
            if (listener != null) listener.onLog("Telefondan sunucu anahtarı alındı");
            // Bekleyen yolculuklar varsa hemen gitsin.
            com.mapcontrol.trip.TripBoxUploader.retryPending(context, true);
            sendJson200(output, com.mapcontrol.api.CloudToken.statusJson(context));
        } catch (Exception e) {
            sendErrorResponse(output, 400, "token_invalid");
        }
    }

    /** {@code DELETE /cloud-token}: telefon eşleşmeyi kaldırdı. */
    private void handleCloudTokenDelete(OutputStream output, String requestHeader) {
        if (!requirePairedPhone(output, requestHeader)) {
            return;
        }
        com.mapcontrol.api.CloudToken.clear(context);
        try {
            sendJson200(output, com.mapcontrol.api.CloudToken.statusJson(context));
        } catch (IOException e) {
            // bağlantı koptu
        }
    }

    private void handlePing(OutputStream output) {
        try {
            sendJson200(output, PhoneApiJson.ping(PhoneAccess.carId(context), com.mapcontrol.gps.Wire.PORT));
        } catch (Exception e) {
            if (listener != null) listener.onLog("[ERROR] /ping: " + e.getMessage());
            sendErrorResponse(output, 500, "ping hatası: " + e.getMessage());
        }
    }

    /** {@code GET /vehicle/now}: son sayaçlar ve parça açılış saatleri. */
    private void handlePhoneVehicleNow(OutputStream output, String requestHeader) {
        if (!requirePairedPhone(output, requestHeader)) {
            return;
        }
        try {
            String json = TripRecorder.get(context).vehicleNowJson();
            if (json == null) {
                sendErrorResponse(output, 503, "Araç verisi henüz okunmadı");
                return;
            }
            sendJson200(output, json);
        } catch (Exception e) {
            if (listener != null) listener.onLog("[ERROR] /vehicle/now: " + e.getMessage());
            sendErrorResponse(output, 500, "Araç verisi hatası: " + e.getMessage());
        }
    }

    private void sendJson200(OutputStream output, String json) throws IOException {
        String http = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json; charset=UTF-8\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Content-Length: " + json.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                "Connection: close\r\n\r\n" +
                json;
        output.write(http.getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    /**
     * Hata yanıtı gönder
     */
    private void sendErrorResponse(OutputStream output, int statusCode, String message) {
        try {
            String response = "HTTP/1.1 " + statusCode + " " + getStatusText(statusCode) + "\r\n" +
                    "Content-Type: text/plain; charset=UTF-8\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Content-Length: " + message.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                    "Connection: close\r\n\r\n" +
                    message;
            output.write(response.getBytes(StandardCharsets.UTF_8));
            output.flush();
        } catch (IOException e) {
            if (listener != null) listener.onLog("[ERROR] Hata yanıtı gönderme hatası: " + e.getMessage());
        }
    }

    /**
     * Dosya listesini döndür (JSON)
     */
    private void handleFileList(OutputStream output) {
        try {
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadDir.exists()) {
                downloadDir.mkdirs();
            }
            
            File[] files = downloadDir.listFiles();
            JSONArray fileArray = new JSONArray();
            
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        JSONObject fileObj = new JSONObject();
                        fileObj.put("name", file.getName());
                        fileObj.put("size", file.length());
                        fileObj.put("sizeFormatted", formatFileSize(file.length()));
                        fileObj.put("modified", file.lastModified());
                        fileArray.put(fileObj);
                    }
                }
            }
            
            JSONObject response = new JSONObject();
            response.put("files", fileArray);
            response.put("count", fileArray.length());
            
            String jsonResponse = response.toString();
            String httpResponse = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/json; charset=UTF-8\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Content-Length: " + jsonResponse.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                    "Connection: close\r\n\r\n" +
                    jsonResponse;
            output.write(httpResponse.getBytes(StandardCharsets.UTF_8));
            output.flush();
            
        } catch (Exception e) {
            if (listener != null) listener.onLog("[ERROR] Dosya listesi hatası: " + e.getMessage());
            sendErrorResponse(output, 500, "Dosya listesi hatası: " + e.getMessage());
        }
    }
    
    /**
     * Dosyayı sil
     */
    private void handleFileDelete(OutputStream output, String queryString) {
        try {
            // Query string'den filename'i al
            String fileName = null;
            if (queryString != null && !queryString.isEmpty()) {
                String[] params = queryString.split("&");
                for (String param : params) {
                    String[] keyValue = param.split("=");
                    if (keyValue.length == 2 && "filename".equals(keyValue[0])) {
                        fileName = java.net.URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name());
                        break;
                    }
                }
            }
            
            if (fileName == null || fileName.isEmpty()) {
                sendErrorResponse(output, 400, "Dosya adı belirtilmedi");
                return;
            }
            
            // Güvenlik: sadece dosya adı, path traversal saldırılarını önle
            if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                sendErrorResponse(output, 400, "Geçersiz dosya adı");
                return;
            }
            
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File file = new File(downloadDir, fileName);
            
            if (!file.exists()) {
                sendErrorResponse(output, 404, "Dosya bulunamadı");
                return;
            }
            
            if (!file.isFile()) {
                sendErrorResponse(output, 400, "Bu bir dosya değil");
                return;
            }
            
            boolean deleted = file.delete();
            
            if (deleted) {
                if (listener != null) listener.onLog("Dosya silindi: " + file.getAbsolutePath());
                String successResponse = "Dosya başarıyla silindi: " + fileName;
                String httpResponse = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/plain; charset=UTF-8\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Content-Length: " + successResponse.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                        "Connection: close\r\n\r\n" +
                        successResponse;
                output.write(httpResponse.getBytes(StandardCharsets.UTF_8));
                output.flush();
            } else {
                sendErrorResponse(output, 500, "Dosya silinemedi");
            }
            
        } catch (Exception e) {
            if (listener != null) listener.onLog("[ERROR] Dosya silme hatası: " + e.getMessage());
            sendErrorResponse(output, 500, "Dosya silme hatası: " + e.getMessage());
        }
    }
    
    /**
     * APK kurulum işlemini handle et
     */
    private void handleInstallApk(OutputStream output, String queryString) {
        try {
            // Query string'den filename'i al
            String fileName = null;
            if (queryString != null && !queryString.isEmpty()) {
                String[] params = queryString.split("&");
                for (String param : params) {
                    String[] keyValue = param.split("=");
                    if (keyValue.length == 2 && "filename".equals(keyValue[0])) {
                        fileName = java.net.URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name());
                        break;
                    }
                }
            }
            
            if (fileName == null || fileName.isEmpty()) {
                sendErrorResponse(output, 400, "Dosya adı belirtilmedi");
                return;
            }
            
            // Güvenlik: sadece APK dosyaları
            if (!fileName.toLowerCase().endsWith(".apk")) {
                sendErrorResponse(output, 400, "Sadece APK dosyaları kurulabilir");
                return;
            }
            
            // Güvenlik: path traversal saldırılarını önle
            if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                sendErrorResponse(output, 400, "Geçersiz dosya adı");
                return;
            }
            
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File file = new File(downloadDir, fileName);
            
            if (!file.exists()) {
                sendErrorResponse(output, 404, "Dosya bulunamadı");
                return;
            }
            
            if (!file.isFile()) {
                sendErrorResponse(output, 400, "Bu bir dosya değil");
                return;
            }
            
            // Listener'a bildir (MainActivity'de kurulum yapılacak)
            if (listener != null) {
                listener.onInstallApk(fileName);
                if (listener != null) listener.onLog("APK kurulum isteği gönderildi: " + fileName);
                
                String successResponse = "APK kurulum başlatıldı: " + fileName;
                String httpResponse = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/plain; charset=UTF-8\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Content-Length: " + successResponse.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                        "Connection: close\r\n\r\n" +
                        successResponse;
                output.write(httpResponse.getBytes(StandardCharsets.UTF_8));
                output.flush();
            } else {
                sendErrorResponse(output, 500, "Listener bulunamadı");
            }
            
        } catch (Exception e) {
            if (listener != null) listener.onLog("[ERROR] APK kurulum hatası: " + e.getMessage());
            sendErrorResponse(output, 500, "APK kurulum hatası: " + e.getMessage());
        }
    }

    /**
     * Yüklü uygulamaları listele (sadece user uygulamaları)
     */
    private void handleAppList(OutputStream output) {
        try {
            PackageManager pm = context.getPackageManager();
            java.util.List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            
            JSONArray appArray = new JSONArray();
            
            for (ApplicationInfo appInfo : apps) {
                // Sistem ve priv uygulamalarını filtrele
                if (isSystemOrPrivApp(appInfo)) {
                    continue;
                }
                
                try {
                    String appName = pm.getApplicationLabel(appInfo).toString();
                    String packageName = appInfo.packageName;
                    
                    JSONObject appObj = new JSONObject();
                    appObj.put("name", appName);
                    appObj.put("package", packageName);
                    appObj.put("locked", "com.mapcontrol".equals(packageName));
                    appArray.put(appObj);
                } catch (Exception e) {
                    // Uygulama bilgisi alınamazsa atla
                    continue;
                }
            }
            
            JSONObject response = new JSONObject();
            response.put("apps", appArray);
            
            String jsonResponse = response.toString();
            String httpResponse = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/json; charset=UTF-8\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Content-Length: " + jsonResponse.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                    "Connection: close\r\n\r\n" +
                    jsonResponse;
            output.write(httpResponse.getBytes(StandardCharsets.UTF_8));
            output.flush();
            
        } catch (Exception e) {
            if (listener != null) listener.onLog("[ERROR] Uygulama listesi hatası: " + e.getMessage());
            sendErrorResponse(output, 500, "Uygulama listesi hatası: " + e.getMessage());
        }
    }

    /**
     * Uygulama silme işlemini handle et
     */
    private void handleAppDelete(OutputStream output, String queryString) {
        try {
            // Query string'den packageName'i al
            String packageName = null;
            if (queryString != null && !queryString.isEmpty()) {
                String[] params = queryString.split("&");
                for (String param : params) {
                    String[] keyValue = param.split("=");
                    if (keyValue.length == 2 && "package".equals(keyValue[0])) {
                        packageName = java.net.URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name());
                        break;
                    }
                }
            }
            
            if (packageName == null || packageName.isEmpty()) {
                sendErrorResponse(output, 400, "Paket adı belirtilmedi");
                return;
            }
            
            // Güvenlik: com.mapcontrol silinemez
            if ("com.mapcontrol".equals(packageName)) {
                sendErrorResponse(output, 400, "Bu uygulama silinemez");
                return;
            }
            
            // Sistem uygulaması kontrolü
            PackageManager pm = context.getPackageManager();
            try {
                ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                if (isSystemOrPrivApp(appInfo)) {
                    sendErrorResponse(output, 400, "Sistem uygulamaları silinemez");
                    return;
                }
            } catch (PackageManager.NameNotFoundException e) {
                sendErrorResponse(output, 404, "Uygulama bulunamadı");
                return;
            }
            
            // Listener'a bildir (MainActivity'de silme yapılacak)
            if (listener != null) {
                listener.onDeleteApp(packageName);
                if (listener != null) listener.onLog("Uygulama silme isteği gönderildi: " + packageName);
                
                String successResponse = "Uygulama silme başlatıldı: " + packageName;
                String httpResponse = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/plain; charset=UTF-8\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Content-Length: " + successResponse.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                        "Connection: close\r\n\r\n" +
                        successResponse;
                output.write(httpResponse.getBytes(StandardCharsets.UTF_8));
                output.flush();
            } else {
                sendErrorResponse(output, 500, "Listener bulunamadı");
            }
            
        } catch (Exception e) {
            if (listener != null) listener.onLog("[ERROR] Uygulama silme hatası: " + e.getMessage());
            sendErrorResponse(output, 500, "Uygulama silme hatası: " + e.getMessage());
        }
    }

    /**
     * Uygulama açma işlemini handle et
     */
    private void handleAppLaunch(OutputStream output, String queryString) {
        try {
            // Query string'den packageName'i al
            String packageName = null;
            if (queryString != null && !queryString.isEmpty()) {
                String[] params = queryString.split("&");
                for (String param : params) {
                    String[] keyValue = param.split("=");
                    if (keyValue.length == 2 && "package".equals(keyValue[0])) {
                        packageName = java.net.URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name());
                        break;
                    }
                }
            }
            
            if (packageName == null || packageName.isEmpty()) {
                sendErrorResponse(output, 400, "Paket adı belirtilmedi");
                return;
            }
            
            // Uygulama var mı kontrol et
            PackageManager pm = context.getPackageManager();
            try {
                pm.getApplicationInfo(packageName, 0);
            } catch (PackageManager.NameNotFoundException e) {
                sendErrorResponse(output, 404, "Uygulama bulunamadı");
                return;
            }
            
            // Listener'a bildir (MainActivity'de açma yapılacak)
            if (listener != null) {
                listener.onLaunchApp(packageName);
                if (listener != null) listener.onLog("Uygulama açma isteği gönderildi: " + packageName);
                
                String successResponse = "Uygulama açma başlatıldı: " + packageName;
                String httpResponse = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/plain; charset=UTF-8\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Content-Length: " + successResponse.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                        "Connection: close\r\n\r\n" +
                        successResponse;
                output.write(httpResponse.getBytes(StandardCharsets.UTF_8));
                output.flush();
            } else {
                sendErrorResponse(output, 500, "Listener bulunamadı");
            }
            
        } catch (Exception e) {
            if (listener != null) listener.onLog("[ERROR] Uygulama açma hatası: " + e.getMessage());
            sendErrorResponse(output, 500, "Uygulama açma hatası: " + e.getMessage());
        }
    }

    /**
     * Bir uygulamanın sistem uygulaması veya priv-app olup olmadığını kontrol eder
     */
    private boolean isSystemOrPrivApp(ApplicationInfo appInfo) {
        try {
            if (appInfo == null) {
                return true;
            }
            
            // com.mapcontrol hariç tut
            if ("com.mapcontrol".equals(appInfo.packageName)) {
                return false;
            }
            
            // Sistem uygulaması kontrolü
            boolean isSystemApp = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean isUpdatedSystemApp = (appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            
            if (isSystemApp || isUpdatedSystemApp) {
                return true;
            }
            
            // Priv-app kontrolü (sourceDir ve publicSourceDir kontrolü)
            String sourceDir = appInfo.sourceDir;
            String publicSourceDir = appInfo.publicSourceDir;
            
            if (sourceDir != null && (sourceDir.contains("/system/priv-app/") || sourceDir.contains("/system/app/"))) {
                return true;
            }
            
            if (publicSourceDir != null && (publicSourceDir.contains("/system/priv-app/") || publicSourceDir.contains("/system/app/"))) {
                return true;
            }
            
            return false;
        } catch (Exception e) {
            // Hata durumunda güvenli tarafta kal (sistem uygulaması say)
            return true;
        }
    }
    
    /**
     * Dosya boyutunu formatla
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
    
    /**
     * HTTP status text
     */
    private String getStatusText(int statusCode) {
        switch (statusCode) {
            case 400: return "Bad Request";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            case 500: return "Internal Server Error";
            default: return "Error";
        }
    }

    /**
     * Local IP adresini al
     */
    private String getLocalIpAddress() {
        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                int ipAddress = wifiInfo.getIpAddress();
                if (ipAddress != 0) {
                    return String.format("%d.%d.%d.%d",
                            (ipAddress & 0xff),
                            (ipAddress >> 8 & 0xff),
                            (ipAddress >> 16 & 0xff),
                            (ipAddress >> 24 & 0xff));
                }
            }
        } catch (Exception e) {
            if (listener != null) listener.onLog("[ERROR] IP adresi alma hatası: " + e.getMessage());
        }

        // Araç Wi-Fi istemcisi değilse (kendi hotspot'u / AP) WifiManager 0 döner; arayüzlerdeki özel adresi ara.
        String fromInterfaces = privateInterfaceAddress();
        if (fromInterfaces != null) {
            return fromInterfaces;
        }

        // Fallback: localhost
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "127.0.0.1";
        }
    }

    /** Kablosuz/AP arayüzleri önce olmak üzere, loopback olmayan ilk özel IPv4 adresi; yoksa null. */
    static String privateInterfaceAddress() {
        String fallback = null;
        try {
            java.util.Enumeration<java.net.NetworkInterface> ifaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (ifaces != null && ifaces.hasMoreElements()) {
                java.net.NetworkInterface iface = ifaces.nextElement();
                if (!iface.isUp() || iface.isLoopback()) {
                    continue;
                }
                String name = iface.getName().toLowerCase(java.util.Locale.ROOT);
                boolean wireless = name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("swlan");
                java.util.Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof java.net.Inet4Address && a.isSiteLocalAddress()) {
                        if (wireless) {
                            return a.getHostAddress();
                        }
                        if (fallback == null) {
                            fallback = a.getHostAddress();
                        }
                    }
                }
            }
        } catch (java.net.SocketException ignored) {
        }
        return fallback;
    }

    /** Telefonun bağlanacağı güncel adres; hotspot sunucudan sonra açılırsa değişmiş olabilir. */
    public String currentHost() {
        return getLocalIpAddress();
    }

    /**
     * Server çalışıyor mu?
     */
    public boolean isRunning() {
        return isRunning.get();
    }

    /**
     * Server port numarası
     */
    public int getPort() {
        return serverPort;
    }
}

