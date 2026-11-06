package com.mapcontrol.dvr;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ParkPhotoImageTest {

    @Test
    public void i420PlanesBecomeNv21WithVBeforeUInterleaved() {
        // 4x2 kare: 8 Y, 2 U, 2 V.
        byte[] i420 = {1, 2, 3, 4, 5, 6, 7, 8, 10, 11, 20, 21};
        byte[] nv21 = ParkPhotoImage.i420ToNv21(i420, 4, 2);
        assertEquals(12, nv21.length);
        for (int i = 0; i < 8; i++) {
            assertEquals(i + 1, nv21[i]);
        }
        // V0 U0 V1 U1
        assertEquals(20, nv21[8]);
        assertEquals(10, nv21[9]);
        assertEquals(21, nv21[10]);
        assertEquals(11, nv21[11]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shortBufferIsRejected() {
        ParkPhotoImage.i420ToNv21(new byte[5], 4, 2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void oddDimensionsAreRejected() {
        ParkPhotoImage.i420ToNv21(new byte[100], 5, 3);
    }
}
