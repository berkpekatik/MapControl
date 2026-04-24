package com.mapcontrol.manager;
import android.content.Context;
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
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import android.os.Environment;
import com.mapcontrol.ui.activity.MainActivity;

/**
 * Basit HTTP Server Manager
 * 7462 portunda HTTP servisi başlatır ve welcome sayfası gösterir
 */
public class WebServerManager {
    private ServerSocket serverSocket;
    private Thread serverThread;
    private AtomicBoolean isRunning = new AtomicBoolean(false);
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
    }

    public WebServerManager(Context context) {
        this.context = context;
    }

    public void setListener(WebServerListener listener) {
        this.listener = listener;
    }

    /**
     * HTTP server'ı başlat (7462 portunda)
     */
    public void startServer() {
        if (isRunning.get()) {
            if (listener != null) listener.onLog("Server zaten çalışıyor");
            return;
        }

        try {
            // Sabit port kullan (7462)
            serverPort = 7462;
            
            serverSocket = new ServerSocket(serverPort);
            isRunning.set(true);
            
            serverThread = new Thread(() -> {
                try {
                    String localIp = getLocalIpAddress();
                    if (listener != null) {
                        listener.onServerStarted(serverPort, localIp);
                    }
                    
                    if (listener != null) listener.onLog("HTTP Server başlatıldı: http://" + localIp + ":" + serverPort);
                    
                    while (isRunning.get() && !serverSocket.isClosed()) {
                        try {
                            Socket clientSocket = serverSocket.accept();
                            handleClient(clientSocket);
                        } catch (IOException e) {
                            if (isRunning.get()) {
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
                    isRunning.set(false);
                    if (listener != null) {
                        listener.onServerStopped();
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
    public void stopServer() {
        if (!isRunning.get()) {
            return;
        }

        isRunning.set(false);
        
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

        if (listener != null) listener.onLog("HTTP Server durduruldu");
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
                String[] parts = requestLine.split(" ");
                if (parts.length < 2) {
                    clientSocket.close();
                    return;
                }

                String method = parts[0];
                String path = parts[1];
                
                // Query string'i ayır
                String queryString = "";
                int queryIndex = path.indexOf('?');
                if (queryIndex != -1) {
                    queryString = path.substring(queryIndex + 1);
                    path = path.substring(0, queryIndex);
                }

                if (listener != null) listener.onLog("HTTP Request: " + method + " " + path);

                // POST request (dosya yükleme)
                if ("POST".equals(method) && "/upload".equals(path)) {
                    handleFileUpload(input, output, requestHeader);
                } else if ("GET".equals(method) && "/files".equals(path)) {
                    // GET request (dosya listesi)
                    handleFileList(output);
                } else if ("DELETE".equals(method) && "/files".equals(path)) {
                    // DELETE request (dosya silme)
                    handleFileDelete(output, queryString);
                } else if ("POST".equals(method) && "/install".equals(path)) {
                    // POST request (APK kurulum)
                    handleInstallApk(output, queryString);
                } else if ("GET".equals(method) && "/apps".equals(path)) {
                    // GET request (uygulama listesi)
                    handleAppList(output);
                } else if ("POST".equals(method) && "/app/delete".equals(path)) {
                    // POST request (uygulama silme)
                    handleAppDelete(output, queryString);
                } else if ("POST".equals(method) && "/app/launch".equals(path)) {
                    // POST request (uygulama açma)
                    handleAppLaunch(output, queryString);
                } else {
                    // GET request (welcome sayfası)
                    String response = generateWelcomePage();
                    String httpResponse = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/html; charset=UTF-8\r\n" +
                            "Content-Length: " + response.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                            "Connection: close\r\n\r\n" +
                            response;
                    output.write(httpResponse.getBytes(StandardCharsets.UTF_8));
                    output.flush();
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
            return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>Map Control</title></head>" +
                   "<body style=\"font-family: sans-serif; background: #0A0F14; color: #FFF; padding: 40px; text-align: center;\">" +
                   "<h1>Hoş Geldiniz!</h1>" +
                   "<p>Map Control Dosya Yükleme Servisi</p>" +
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

            // Body'yi Content-Length kadar oku (deterministik yaklaşım)
            BufferedInputStream bufferedInput = new BufferedInputStream(input, 65536);
            byte[] bodyBytes = new byte[(int) contentLength];
            long totalBodyRead = 0;
            
            // Content-Length kadar oku
            while (totalBodyRead < contentLength) {
                int bytesRead = bufferedInput.read(bodyBytes, (int) totalBodyRead, 
                                                   (int) (contentLength - totalBodyRead));
                if (bytesRead == -1) {
                    if (listener != null) listener.onLog("[ERROR] Erken EOF: " + totalBodyRead + " / " + contentLength + " byte okundu");
                    sendErrorResponse(output, 400, "Body eksik: " + totalBodyRead + " / " + contentLength + " byte okundu");
                    return;
                }
                totalBodyRead += bytesRead;
            }
            
            if (listener != null) listener.onLog("Body okundu: " + totalBodyRead + " bytes (Content-Length: " + contentLength + ")");
            
            // Body içinde ilk part'ın header'ını bul (\r\n\r\n ile biten)
            byte[] doubleCrlf = "\r\n\r\n".getBytes(StandardCharsets.UTF_8);
            int doubleCrlfPos = -1;
            
            for (int i = 0; i <= bodyBytes.length - doubleCrlf.length; i++) {
                boolean matches = true;
                for (int j = 0; j < doubleCrlf.length; j++) {
                    if (bodyBytes[i + j] != doubleCrlf[j]) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    doubleCrlfPos = i + doubleCrlf.length;
                    break;
                }
            }
            
            if (doubleCrlfPos == -1) {
                if (listener != null) listener.onLog("[ERROR] Body içinde \\r\\n\\r\\n bulunamadı");
                sendErrorResponse(output, 400, "Multipart header bulunamadı");
                return;
            }
            
            // Filename'i header'dan çıkar
            String partHeader = new String(bodyBytes, 0, doubleCrlfPos, StandardCharsets.UTF_8);
            String fileName = null;
            
            int filenameIndex = partHeader.indexOf("filename=\"");
            if (filenameIndex == -1) {
                filenameIndex = partHeader.indexOf("filename=");
                if (filenameIndex != -1) {
                    // Tırnaksız filename
                    int filenameStart = filenameIndex + 9;
                    int filenameEnd = partHeader.indexOf("\r\n", filenameStart);
                    if (filenameEnd == -1) filenameEnd = partHeader.length();
                    fileName = partHeader.substring(filenameStart, filenameEnd).trim();
                }
            } else {
                // Tırnaklı filename
                int filenameStart = filenameIndex + 10;
                int filenameEnd = partHeader.indexOf("\"", filenameStart);
                if (filenameEnd != -1) {
                    fileName = partHeader.substring(filenameStart, filenameEnd);
                }
            }
            
            if (fileName == null || fileName.isEmpty()) {
                if (listener != null) listener.onLog("[ERROR] Filename bulunamadı");
                sendErrorResponse(output, 400, "Filename bulunamadı");
                return;
            }
            
            if (listener != null) listener.onLog("Filename: " + fileName);
            
            // Dosya içeriğinin başlangıcı: doubleCrlfPos
            int fileDataStart = doubleCrlfPos;
            
            // Boundary'yi body içinde ara (\r\n--boundary formatında)
            byte[] boundaryBytes = ("\r\n" + boundary).getBytes(StandardCharsets.UTF_8);
            int boundaryPos = -1;
            
            for (int i = fileDataStart; i <= bodyBytes.length - boundaryBytes.length; i++) {
                boolean matches = true;
                for (int j = 0; j < boundaryBytes.length; j++) {
                    if (bodyBytes[i + j] != boundaryBytes[j]) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    boundaryPos = i;
                    break;
                }
            }
            
            if (boundaryPos == -1) {
                if (listener != null) listener.onLog("[ERROR] Boundary body içinde bulunamadı");
                sendErrorResponse(output, 400, "Boundary bulunamadı");
                return;
            }
            
            // Dosya içeriğinin sonu: boundaryPos (boundary'den önceki \r\n dosyaya dahil değil)
            // boundaryPos'tan önceki 2 byte \r\n olabilir, kontrol et
            int fileDataEnd = boundaryPos;
            if (fileDataEnd >= 2 && 
                bodyBytes[fileDataEnd - 2] == 0x0D && 
                bodyBytes[fileDataEnd - 1] == 0x0A) {
                fileDataEnd -= 2; // \r\n'yi çıkar
            }
            
            int fileDataLength = fileDataEnd - fileDataStart;
            
            if (listener != null) {
                listener.onLog("Dosya offset'leri - Başlangıç: " + fileDataStart + 
                             ", Bitiş: " + fileDataEnd + 
                             ", Uzunluk: " + fileDataLength + " bytes");
            }
            
            if (fileDataLength <= 0) {
                if (listener != null) listener.onLog("[ERROR] Dosya içeriği boş");
                sendErrorResponse(output, 400, "Dosya içeriği boş");
                return;
            }
            
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
            
            // Dosya içeriğini yaz
            try {
                fileOutputStream = new FileOutputStream(file);
                fileOutputStream.write(bodyBytes, fileDataStart, fileDataLength);
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
                }
                if (listener != null) listener.onLog("[ERROR] Dosya yazma hatası: " + e.getMessage());
                sendErrorResponse(output, 500, "Dosya yazma hatası: " + e.getMessage());
                return;
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

    /**
     * Hata yanıtı gönder
     */
    private void sendErrorResponse(OutputStream output, int statusCode, String message) {
        try {
            String response = "HTTP/1.1 " + statusCode + " " + getStatusText(statusCode) + "\r\n" +
                    "Content-Type: text/plain; charset=UTF-8\r\n" +
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

        // Fallback: localhost
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "127.0.0.1";
        }
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

