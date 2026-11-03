package com.mapcontrol.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.IDN;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Araç ünitesi AAAA kaydını seçip IPv6 çıkışı olmadığı için bağlantıyı düşürüyor.
 * TCP hedefini A kaydına sabitler; TLS sunucu adı özgün host olarak kalır.
 */
public final class Ipv4Http {

    static {
        // Masaüstü HttpURLConnection "Host" başlığını yok sayar. Araçtaki yığın başlığı iletir.
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
    }

    private Ipv4Http() {
    }

    public static HttpURLConnection open(URL url) throws IOException {
        String host = url.getHost();
        Inet4Address ipv4 = firstIpv4(host);
        if (ipv4 == null || ipv4.getHostAddress().equals(host)) {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            return connection;
        }

        URL ipUrl = rewriteHost(url, ipv4.getHostAddress());
        HttpURLConnection connection = (HttpURLConnection) ipUrl.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Host", hostHeader(url));
        if (connection instanceof HttpsURLConnection) {
            HttpsURLConnection https = (HttpsURLConnection) connection;
            https.setSSLSocketFactory(new Ipv4SslSocketFactory(https.getSSLSocketFactory(), host));
            https.setHostnameVerifier((hostname, session) -> certificateMatches(host, session));
        }
        return connection;
    }

    private static boolean certificateMatches(String host, SSLSession session) {
        try {
            if (HttpsURLConnection.getDefaultHostnameVerifier().verify(host, session)) {
                return true;
            }
            Certificate[] chain = session.getPeerCertificates();
            if (chain == null || chain.length == 0 || !(chain[0] instanceof X509Certificate)) {
                return false;
            }
            X509Certificate cert = (X509Certificate) chain[0];
            Collection<List<?>> names = cert.getSubjectAlternativeNames();
            boolean sawDns = false;
            if (names != null) {
                for (List<?> name : names) {
                    if (name == null || name.size() < 2 || !(name.get(0) instanceof Integer)) continue;
                    int type = (Integer) name.get(0);
                    if (type == 2 && name.get(1) instanceof String) {
                        sawDns = true;
                        if (dnsNameMatches(host, (String) name.get(1))) return true;
                    }
                }
            }
            if (sawDns) return false;
            String dn = cert.getSubjectX500Principal().getName("RFC2253");
            for (String part : dn.split(",")) {
                String trimmed = part.trim();
                if (trimmed.regionMatches(true, 0, "CN=", 0, 3)) {
                    return dnsNameMatches(host, trimmed.substring(3));
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean dnsNameMatches(String host, String pattern) {
        String asciiHost;
        try {
            asciiHost = IDN.toASCII(host).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (pattern.startsWith("*.")) {
            try {
                String rest = IDN.toASCII(pattern.substring(2)).toLowerCase(Locale.ROOT);
                int dot = asciiHost.indexOf('.');
                return dot > 0 && asciiHost.substring(dot + 1).equals(rest);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        try {
            return asciiHost.equals(IDN.toASCII(pattern).toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static Inet4Address firstIpv4(String host) throws IOException {
        if (host == null || host.isEmpty()) return null;
        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (address instanceof Inet4Address) {
                return (Inet4Address) address;
            }
        }
        return null;
    }

    private static String hostHeader(URL url) {
        int port = url.getPort();
        boolean defaultPort = port == -1
                || ("https".equalsIgnoreCase(url.getProtocol()) && port == 443)
                || ("http".equalsIgnoreCase(url.getProtocol()) && port == 80);
        if (defaultPort) return url.getHost();
        return url.getHost() + ":" + port;
    }

    private static URL rewriteHost(URL url, String ip) throws MalformedURLException {
        String external = url.toExternalForm();
        String host = url.getHost();
        int scheme = external.indexOf("://");
        if (scheme >= 0) {
            int hostStart = scheme + 3;
            int hostEnd = external.indexOf(host, hostStart);
            if (hostEnd >= 0 && hostEnd < authorityEnd(external, hostStart)) {
                return new URL(external.substring(0, hostEnd) + ip + external.substring(hostEnd + host.length()));
            }
        }
        String file = url.getFile();
        if (file == null || file.isEmpty()) file = "/";
        return new URL(url.getProtocol(), ip, url.getPort(), file);
    }

    private static int authorityEnd(String external, int hostStart) {
        int end = external.length();
        int slash = external.indexOf('/', hostStart);
        int query = external.indexOf('?', hostStart);
        if (slash >= 0) end = Math.min(end, slash);
        if (query >= 0) end = Math.min(end, query);
        return end;
    }

    private static void applySni(SSLSocket socket, String host) {
        SSLParameters params = socket.getSSLParameters();
        params.setServerNames(Collections.singletonList(new SNIHostName(host)));
        socket.setSSLParameters(params);
        try {
            Method setHostname = socket.getClass().getMethod("setHostname", String.class);
            setHostname.invoke(socket, host);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static final class Ipv4SslSocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;
        private final String serverName;

        Ipv4SslSocketFactory(SSLSocketFactory delegate, String serverName) {
            this.delegate = delegate;
            this.serverName = serverName;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            return guard(delegate.createSocket(s, serverName, port, autoClose));
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return guard(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return guard(delegate.createSocket(host, port, localHost, localPort));
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return guard(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            return guard(delegate.createSocket(address, port, localAddress, localPort));
        }

        private Socket guard(Socket socket) {
            if (!(socket instanceof SSLSocket)) return socket;
            SSLSocket ssl = (SSLSocket) socket;
            applySni(ssl, serverName);
            return new SniGuardSocket(ssl, serverName);
        }
    }

    private static final class SniGuardSocket extends SSLSocket {
        private final SSLSocket delegate;
        private final String serverName;

        SniGuardSocket(SSLSocket delegate, String serverName) {
            this.delegate = delegate;
            this.serverName = serverName;
        }

        @SuppressWarnings("unused")
        public void setHostname(String ignored) {
            applySni(delegate, serverName);
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }

        @Override
        public String[] getEnabledCipherSuites() {
            return delegate.getEnabledCipherSuites();
        }

        @Override
        public void setEnabledCipherSuites(String[] suites) {
            delegate.setEnabledCipherSuites(suites);
        }

        @Override
        public String[] getSupportedProtocols() {
            return delegate.getSupportedProtocols();
        }

        @Override
        public String[] getEnabledProtocols() {
            return delegate.getEnabledProtocols();
        }

        @Override
        public void setEnabledProtocols(String[] protocols) {
            delegate.setEnabledProtocols(protocols);
        }

        @Override
        public SSLSession getSession() {
            return delegate.getSession();
        }

        @Override
        public SSLSession getHandshakeSession() {
            return delegate.getHandshakeSession();
        }

        @Override
        public void addHandshakeCompletedListener(HandshakeCompletedListener listener) {
            delegate.addHandshakeCompletedListener(listener);
        }

        @Override
        public void removeHandshakeCompletedListener(HandshakeCompletedListener listener) {
            delegate.removeHandshakeCompletedListener(listener);
        }

        @Override
        public void startHandshake() throws IOException {
            applySni(delegate, serverName);
            delegate.startHandshake();
        }

        @Override
        public void setUseClientMode(boolean mode) {
            delegate.setUseClientMode(mode);
        }

        @Override
        public boolean getUseClientMode() {
            return delegate.getUseClientMode();
        }

        @Override
        public void setNeedClientAuth(boolean need) {
            delegate.setNeedClientAuth(need);
        }

        @Override
        public boolean getNeedClientAuth() {
            return delegate.getNeedClientAuth();
        }

        @Override
        public void setWantClientAuth(boolean want) {
            delegate.setWantClientAuth(want);
        }

        @Override
        public boolean getWantClientAuth() {
            return delegate.getWantClientAuth();
        }

        @Override
        public void setEnableSessionCreation(boolean flag) {
            delegate.setEnableSessionCreation(flag);
        }

        @Override
        public boolean getEnableSessionCreation() {
            return delegate.getEnableSessionCreation();
        }

        @Override
        public SSLParameters getSSLParameters() {
            return delegate.getSSLParameters();
        }

        @Override
        public void setSSLParameters(SSLParameters params) {
            delegate.setSSLParameters(params);
            applySni(delegate, serverName);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return delegate.getInputStream();
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            return delegate.getOutputStream();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public boolean isClosed() {
            return delegate.isClosed();
        }

        @Override
        public boolean isConnected() {
            return delegate.isConnected();
        }

        @Override
        public boolean isBound() {
            return delegate.isBound();
        }

        @Override
        public boolean isInputShutdown() {
            return delegate.isInputShutdown();
        }

        @Override
        public boolean isOutputShutdown() {
            return delegate.isOutputShutdown();
        }

        @Override
        public void shutdownInput() throws IOException {
            delegate.shutdownInput();
        }

        @Override
        public void shutdownOutput() throws IOException {
            delegate.shutdownOutput();
        }

        @Override
        public void setSoTimeout(int timeout) throws java.net.SocketException {
            delegate.setSoTimeout(timeout);
        }

        @Override
        public int getSoTimeout() throws java.net.SocketException {
            return delegate.getSoTimeout();
        }

        @Override
        public void setTcpNoDelay(boolean on) throws java.net.SocketException {
            delegate.setTcpNoDelay(on);
        }

        @Override
        public boolean getTcpNoDelay() throws java.net.SocketException {
            return delegate.getTcpNoDelay();
        }

        @Override
        public InetAddress getInetAddress() {
            return delegate.getInetAddress();
        }

        @Override
        public int getPort() {
            return delegate.getPort();
        }
    }
}
