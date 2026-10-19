package com.mapcontrol.util;

/** Select only an unambiguous secondary display. The OEM identity still requires validation. */
public final class SecondaryDisplaySelector {
    private SecondaryDisplaySelector() {}
    public static int select(int[] ids) {
        int selected = -1;
        if (ids == null) return selected;
        for (int id : ids) {
            if (id <= 0) continue;
            if (selected != -1 && selected != id) return -1;
            selected = id;
        }
        return selected;
    }
}
