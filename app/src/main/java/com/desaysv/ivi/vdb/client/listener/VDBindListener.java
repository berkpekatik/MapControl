package com.desaysv.ivi.vdb.client.listener;

import com.desaysv.ivi.vdb.client.bind.VDServiceDef;

/* compiled from: r8-map-id-68001c32d3ba41aaeec8ea0ca25ce36b0b764eb40c8b7abbf6d2ae52bbacc2f9 */
public interface VDBindListener {
    void onVDConnected(VDServiceDef.ServiceType serviceType);

    void onVDDisconnected(VDServiceDef.ServiceType serviceType);
}
