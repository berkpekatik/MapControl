package com.mapcontrol.manager;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;

public class MultipartStreamTest {

    private static final String BOUNDARY = "--abc123";

    private static byte[] body(byte[] file, String name) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] head = (BOUNDARY + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + name
                + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        b.write(head, 0, head.length);
        b.write(file, 0, file.length);
        byte[] tail = ("\r\n" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8);
        b.write(tail, 0, tail.length);
        return b.toByteArray();
    }

    /** Her read() en fazla [chunk] bayt verir: ayıraç tampon sınırına denk gelsin. */
    private static InputStream trickle(byte[] data, int chunk) {
        return new ByteArrayInputStream(data) {
            @Override
            public synchronized int read(byte[] b, int off, int len) {
                return super.read(b, off, Math.min(len, chunk));
            }
        };
    }

    private static void roundTrip(byte[] file, int chunk) throws Exception {
        byte[] data = body(file, "a.apk");
        MultipartStream.Limited in = new MultipartStream.Limited(trickle(data, chunk), data.length);
        byte[] header = MultipartStream.readPartHeader(in, 16 * 1024);
        assertNotNull(header);
        assertEquals("a.apk", MultipartStream.fileName(new String(header, StandardCharsets.UTF_8)));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long n = MultipartStream.copyUntil(in, ("\r\n" + BOUNDARY).getBytes(StandardCharsets.UTF_8), out);
        assertEquals(file.length, n);
        assertArrayEquals(file, out.toByteArray());
        in.drain();
        assertEquals(data.length, in.consumed());
    }

    @Test
    public void streamsFileAcrossBufferBoundaries() throws Exception {
        Random r = new Random(7);
        byte[] big = new byte[MultipartStream.BUF * 3 + 17];
        r.nextBytes(big);
        for (int chunk : new int[] {1, 3, 7, 4096, MultipartStream.BUF, Integer.MAX_VALUE}) {
            roundTrip(big, chunk);
        }
    }

    @Test
    public void keepsTrailingCrlfAndPartialBoundaryInsideFile() throws Exception {
        roundTrip("PK\r\n--abc12\r\n\r\n".getBytes(StandardCharsets.UTF_8), 5);
    }

    @Test
    public void missingBoundaryIsReported() throws Exception {
        byte[] data = "hello world".getBytes(StandardCharsets.UTF_8);
        long n = MultipartStream.copyUntil(new ByteArrayInputStream(data),
                ("\r\n" + BOUNDARY).getBytes(StandardCharsets.UTF_8), new ByteArrayOutputStream());
        assertEquals(-1, n);
    }

    @Test
    public void limitStopsAtContentLength() throws Exception {
        byte[] data = "0123456789".getBytes(StandardCharsets.UTF_8);
        MultipartStream.Limited in = new MultipartStream.Limited(new ByteArrayInputStream(data), 4);
        in.drain();
        assertEquals(4, in.consumed());
    }

    @Test
    public void fileNameDropsDirectories() {
        assertEquals("x.apk", MultipartStream.fileName("filename=\"../../x.apk\"\r\n"));
        assertEquals("b.apk", MultipartStream.fileName("filename=\"C:\\a\\b.apk\"\r\n"));
        assertEquals("c.zip", MultipartStream.fileName("filename=c.zip\r\n"));
        assertNull(MultipartStream.fileName("filename=\"..\"\r\n"));
        assertNull(MultipartStream.fileName("name=\"file\"\r\n"));
    }
}
