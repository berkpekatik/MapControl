package com.mapcontrol.util;

/**
 * Araç sisteminin {@code android.intent.action.car_system_gesture_mode} yayınındaki kaydırma kodları.
 * Kodlar MyCar'ın {@code M6.onReceive} tablosundan doğrulanmıştır.
 *
 * <p>Kod = parmak sayısı * 100 + yön. Yön: sol 0, sağ 1, aşağı 2, yukarı 3.
 */
public final class SplitScreenGesture {

    public static final int LEFT = 0;
    public static final int RIGHT = 1;
    public static final int DOWN = 2;
    public static final int UP = 3;

    public static final int[] FINGER_COUNTS = {2, 3, 4};
    /** Ekranda gösterim sırası. */
    public static final int[] DIRECTIONS = {UP, DOWN, LEFT, RIGHT};

    /** {@code CarSystemGestureController}: iki parmak sola. */
    public static final int CODE_TWO_LEFT = code(2, LEFT);
    /** {@code CarSystemGestureController}: iki parmak sağa. */
    public static final int CODE_TWO_RIGHT = code(2, RIGHT);

    private SplitScreenGesture() {
    }

    public static int code(int fingers, int direction) {
        return fingers * 100 + direction;
    }

    public static int fingersOf(int code) {
        return code / 100;
    }

    public static int directionOf(int code) {
        return code % 100;
    }

    public static boolean isValid(int code) {
        int fingers = fingersOf(code);
        int dir = directionOf(code);
        return fingers >= 2 && fingers <= 4 && dir >= LEFT && dir <= UP;
    }

    /**
     * Harita kaydırması açıkken iki parmak sola/sağa haritaya aittir; bölünmüş ekran bu kodları
     * kullanmaz.
     */
    public static boolean isReservedByMapSwipe(int code) {
        return code == CODE_TWO_LEFT || code == CODE_TWO_RIGHT;
    }
}
