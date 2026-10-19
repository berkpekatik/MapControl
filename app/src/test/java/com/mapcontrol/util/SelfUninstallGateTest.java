package com.mapcontrol.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class SelfUninstallGateTest {
    private static class Owner implements SelfUninstallGate.OwnerAccess {
        boolean owner;
        boolean staysOwner;
        boolean denied;
        int clears;
        public boolean isOwner() { return owner; }
        public void clearOwner() {
            clears++;
            if (denied) throw new SecurityException("Denied");
            owner = staysOwner;
        }
    }

    @Test public void nonOwnerDoesNotChangePolicy() {
        Owner owner = new Owner();
        SelfUninstallGate.prepare(owner);
        assertEquals(0, owner.clears);
    }
    @Test public void ownerReleaseIsVerified() {
        Owner owner = new Owner();
        owner.owner = true;
        SelfUninstallGate.prepare(owner);
        assertFalse(owner.owner);
        assertEquals(1, owner.clears);
    }
    @Test(expected = IllegalStateException.class) public void missingServiceStopsUninstall() {
        SelfUninstallGate.prepare(null);
    }
    @Test(expected = IllegalStateException.class) public void unchangedOwnerStopsUninstall() {
        Owner owner = new Owner();
        owner.owner = owner.staysOwner = true;
        SelfUninstallGate.prepare(owner);
    }
    @Test(expected = SecurityException.class) public void deniedReleaseStopsUninstall() {
        Owner owner = new Owner();
        owner.owner = owner.denied = true;
        SelfUninstallGate.prepare(owner);
    }
    @Test(expected = SecurityException.class) public void unreadableOwnerStopsUninstall() {
        SelfUninstallGate.prepare(new SelfUninstallGate.OwnerAccess() {
            public boolean isOwner() { throw new SecurityException("Denied"); }
            public void clearOwner() { fail("Must not clear unknown owner"); }
        });
    }
}
