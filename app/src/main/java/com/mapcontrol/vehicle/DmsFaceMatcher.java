package com.mapcontrol.vehicle;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.FaceDetector;

import java.util.List;

public final class DmsFaceMatcher {
    public static final int FRAME_WIDTH = 320;
    public static final int FRAME_HEIGHT = 240;
    private static final int FEATURE_SIZE = 48;
    private static final int GRID_SIZE = 4;
    private static final int LBP_BINS = 256;
    private static final double MATCH_THRESHOLD = 0.35;
    private static final double MIN_MATCH_GAP = 0.028;

    public static final class CameraSelection {
        public final String cameraId;
        public final int sensorOrientation;
        public final boolean frontFacing;

        public CameraSelection(String cameraId, int sensorOrientation, boolean frontFacing) {
            this.cameraId = cameraId;
            this.sensorOrientation = sensorOrientation;
            this.frontFacing = frontFacing;
        }
    }

    public static final class Match {
        public final FaceProfileStore.Profile profile;
        public final double distance;

        Match(FaceProfileStore.Profile profile, double distance) {
            this.profile = profile;
            this.distance = distance;
        }
    }

    private DmsFaceMatcher() {
    }

    /**
     * Picks the vehicle DMS camera ("0") or falls back to a front-facing / first available camera.
     */
    public static CameraSelection selectCamera(CameraManager manager) throws CameraAccessException {
        if (manager == null) {
            return null;
        }
        String[] ids = manager.getCameraIdList();
        if (ids == null || ids.length == 0) {
            return null;
        }
        String chosenId = null;
        for (String id : ids) {
            if ("0".equals(id)) {
                chosenId = id;
                break;
            }
            CameraCharacteristics chars = manager.getCameraCharacteristics(id);
            Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                chosenId = id;
            }
        }
        if (chosenId == null) {
            chosenId = ids[0];
        }
        CameraCharacteristics chars = manager.getCameraCharacteristics(chosenId);
        Integer orientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION);
        Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
        int sensorOrientation = orientation != null ? orientation : 0;
        boolean frontFacing = facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT;
        return new CameraSelection(chosenId, sensorOrientation, frontFacing);
    }

    /**
     * Center-crops any source bitmap to 4:3 (320x240) without aspect-ratio stretching.
     */
    public static Bitmap normalizeFrame(Bitmap source, int rotationDegrees, boolean mirrorHorizontal) {
        if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) {
            return null;
        }
        Bitmap oriented = source;
        if (rotationDegrees != 0 || mirrorHorizontal) {
            Matrix matrix = new Matrix();
            if (rotationDegrees != 0) {
                matrix.postRotate(rotationDegrees);
            }
            if (mirrorHorizontal) {
                matrix.postScale(-1f, 1f);
            }
            oriented = Bitmap.createBitmap(
                    source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
        }
        int srcW = oriented.getWidth();
        int srcH = oriented.getHeight();
        float targetAspect = (float) FRAME_WIDTH / (float) FRAME_HEIGHT;
        float srcAspect = (float) srcW / (float) srcH;
        int cropW = srcW;
        int cropH = srcH;
        int cropX = 0;
        int cropY = 0;
        if (srcAspect > targetAspect) {
            cropW = Math.max(1, Math.round(srcH * targetAspect));
            cropX = (srcW - cropW) / 2;
        } else if (srcAspect < targetAspect) {
            cropH = Math.max(1, Math.round(srcW / targetAspect));
            cropY = (srcH - cropH) / 2;
        }
        Bitmap cropped = (cropX == 0 && cropY == 0 && cropW == srcW && cropH == srcH)
                ? oriented
                : Bitmap.createBitmap(oriented, cropX, cropY, cropW, cropH);
        if (cropped != oriented && oriented != source) {
            oriented.recycle();
        }
        Bitmap scaled = (cropped.getWidth() == FRAME_WIDTH && cropped.getHeight() == FRAME_HEIGHT)
                ? cropped
                : Bitmap.createScaledBitmap(cropped, FRAME_WIDTH, FRAME_HEIGHT, true);
        if (scaled != cropped && cropped != source) {
            cropped.recycle();
        }
        return scaled;
    }

    public static Match findMatch(Bitmap frame, FaceDetector.Face face,
                                  List<FaceProfileStore.Profile> profiles) {
        byte[] descriptor = describeFace(frame, face);
        if (descriptor == null) {
            return null;
        }
        FaceProfileStore.Profile best = null;
        double bestDistance = Double.MAX_VALUE;
        double secondDistance = Double.MAX_VALUE;
        for (FaceProfileStore.Profile profile : profiles) {
            double min1 = Double.MAX_VALUE;
            double min2 = Double.MAX_VALUE;
            for (byte[] template : profile.templates) {
                double distance = chiSquareDistance(descriptor, template);
                if (distance < min1) {
                    min2 = min1;
                    min1 = distance;
                } else if (distance < min2) {
                    min2 = distance;
                }
            }
            double profileDistance = (min2 < Double.MAX_VALUE)
                    ? (min1 * 0.70 + min2 * 0.30)
                    : min1;
            if (profileDistance < bestDistance) {
                secondDistance = bestDistance;
                bestDistance = profileDistance;
                best = profile;
            } else if (profileDistance < secondDistance) {
                secondDistance = profileDistance;
            }
        }
        if (best != null && bestDistance < MATCH_THRESHOLD
                && (secondDistance == Double.MAX_VALUE
                || secondDistance - bestDistance > MIN_MATCH_GAP)) {
            return new Match(best, bestDistance);
        }
        return null;
    }

    public static byte[] describeFace(Bitmap frame, FaceDetector.Face face) {
        PointF midpoint = new PointF();
        face.getMidPoint(midpoint);
        float eyeDistance = face.eyesDistance();
        if (eyeDistance < 8) {
            return null;
        }
        int left = Math.max(0, Math.round(midpoint.x - eyeDistance * 2.0f));
        int top = Math.max(0, Math.round(midpoint.y - eyeDistance * 1.5f));
        int right = Math.min(frame.getWidth(), Math.round(midpoint.x + eyeDistance * 2.0f));
        int bottom = Math.min(frame.getHeight(), Math.round(midpoint.y + eyeDistance * 3.0f));
        if (right - left < 24 || bottom - top < 24) {
            return null;
        }
        Bitmap crop = Bitmap.createBitmap(frame, left, top, right - left, bottom - top);
        Bitmap normalized = Bitmap.createScaledBitmap(crop, FEATURE_SIZE, FEATURE_SIZE, true);
        if (normalized != crop) {
            crop.recycle();
        }
        int[] pixels = new int[FEATURE_SIZE * FEATURE_SIZE];
        normalized.getPixels(pixels, 0, FEATURE_SIZE, 0, 0, FEATURE_SIZE, FEATURE_SIZE);
        normalized.recycle();
        int[] gray = equalize(pixels);
        byte[] descriptor = new byte[GRID_SIZE * GRID_SIZE * LBP_BINS];
        int cellWidth = FEATURE_SIZE / GRID_SIZE;
        int cellHeight = FEATURE_SIZE / GRID_SIZE;
        for (int y = 1; y < FEATURE_SIZE - 1; y++) {
            for (int x = 1; x < FEATURE_SIZE - 1; x++) {
                int center = gray[y * FEATURE_SIZE + x];
                int code = 0;
                code |= (gray[(y - 1) * FEATURE_SIZE + x - 1] >= center ? 1 : 0) << 7;
                code |= (gray[(y - 1) * FEATURE_SIZE + x] >= center ? 1 : 0) << 6;
                code |= (gray[(y - 1) * FEATURE_SIZE + x + 1] >= center ? 1 : 0) << 5;
                code |= (gray[y * FEATURE_SIZE + x + 1] >= center ? 1 : 0) << 4;
                code |= (gray[(y + 1) * FEATURE_SIZE + x + 1] >= center ? 1 : 0) << 3;
                code |= (gray[(y + 1) * FEATURE_SIZE + x] >= center ? 1 : 0) << 2;
                code |= (gray[(y + 1) * FEATURE_SIZE + x - 1] >= center ? 1 : 0) << 1;
                code |= gray[y * FEATURE_SIZE + x - 1] >= center ? 1 : 0;
                int cell = (y / cellHeight) * GRID_SIZE + x / cellWidth;
                int offset = cell * LBP_BINS + code;
                descriptor[offset] = (byte) Math.min(255, (descriptor[offset] & 0xFF) + 1);
            }
        }
        normalizeHistograms(descriptor);
        return descriptor;
    }

    private static int[] equalize(int[] pixels) {
        int[] histogram = new int[256];
        int[] gray = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int color = pixels[i];
            gray[i] = (int) (0.299 * ((color >> 16) & 0xFF)
                    + 0.587 * ((color >> 8) & 0xFF) + 0.114 * (color & 0xFF));
            histogram[gray[i]]++;
        }
        int[] mapping = new int[256];
        int cumulative = 0;
        for (int i = 0; i < histogram.length; i++) {
            cumulative += histogram[i];
            mapping[i] = cumulative * 255 / pixels.length;
        }
        for (int i = 0; i < gray.length; i++) {
            gray[i] = mapping[gray[i]];
        }
        return gray;
    }

    private static void normalizeHistograms(byte[] descriptor) {
        for (int cell = 0; cell < GRID_SIZE * GRID_SIZE; cell++) {
            int offset = cell * LBP_BINS;
            int total = 0;
            for (int i = 0; i < LBP_BINS; i++) {
                total += descriptor[offset + i] & 0xFF;
            }
            if (total == 0) {
                continue;
            }
            for (int i = 0; i < LBP_BINS; i++) {
                descriptor[offset + i] = (byte) ((descriptor[offset + i] & 0xFF) * 255 / total);
            }
        }
    }

    private static double chiSquareDistance(byte[] first, byte[] second) {
        if (first.length != second.length) {
            return Double.MAX_VALUE;
        }
        double distance = 0;
        int cells = GRID_SIZE * GRID_SIZE;
        for (int cell = 0; cell < cells; cell++) {
            int offset = cell * LBP_BINS;
            double cellDistance = 0;
            for (int bin = 0; bin < LBP_BINS; bin++) {
                double a = first[offset + bin] & 0xFF;
                double b = second[offset + bin] & 0xFF;
                double sum = a + b;
                if (sum > 0) {
                    double difference = a - b;
                    cellDistance += difference * difference / sum;
                }
            }
            distance += cellDistance / (2 * LBP_BINS);
        }
        return distance / cells;
    }
}
