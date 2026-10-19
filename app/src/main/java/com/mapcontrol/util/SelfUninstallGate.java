package com.mapcontrol.util;

/** Fail closed if owner state cannot be read or the owner role cannot be released. */
public final class SelfUninstallGate {
    public interface OwnerAccess {
        boolean isOwner();
        void clearOwner();
    }

    private SelfUninstallGate() {}

    public static void prepare(OwnerAccess access) {
        if (access == null) throw new IllegalStateException("Device policy service unavailable");
        if (access.isOwner()) {
            access.clearOwner();
            if (access.isOwner()) throw new IllegalStateException("Device owner role is still active");
        }
    }
}
