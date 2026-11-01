package com.mapcontrol.ui;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.desaysv.ivi.extra.project.carinfo.NewEnergyID;
import com.desaysv.ivi.extra.project.carinfo.proxy.Constants;
import com.desaysv.ivi.vdb.client.VDBus;
import com.desaysv.ivi.vdb.event.VDEvent;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Read-only mode source for UI chips, independent of general-metric loading and proxy flags. */
final class WidgetDriveModeSource implements HeaderStatusMonitor.ModeSource {
    interface Transport { VDEvent read(VDEvent request); }
    private final Transport transport;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Consumer<HeaderStatusMonitor.ModeReading>> listeners = new ArrayList<>();
    private HeaderStatusMonitor.ModeReading current = new HeaderStatusMonitor.ModeReading(false, Integer.MIN_VALUE);
    private ExecutorService worker;
    private boolean inFlight;
    private int generation;
    private String lastDiagnostic;

    WidgetDriveModeSource(Context context) {
        this(request -> {
            VDBus bus = VDBus.getDefault();
            bus.init(context.getApplicationContext());
            return bus.getOnce(request);
        });
    }

    WidgetDriveModeSource(Transport transport) { this.transport = transport; }

    @Override public HeaderStatusMonitor.ModeReading currentReading() { return current; }

    @Override public void addListener(Consumer<HeaderStatusMonitor.ModeReading> listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
        listener.accept(current);
    }

    @Override public void removeListener(Consumer<HeaderStatusMonitor.ModeReading> listener) {
        listeners.remove(listener);
        if (!listeners.isEmpty()) return;
        generation++;
        inFlight = false;
        current = new HeaderStatusMonitor.ModeReading(false, Integer.MIN_VALUE);
        if (worker != null) { worker.shutdownNow(); worker = null; }
    }

    @Override public void refresh() {
        if (listeners.isEmpty() || inFlight) return;
        if (worker == null) worker = Executors.newSingleThreadExecutor();
        inFlight = true;
        int ticket = generation;
        worker.execute(() -> {
            int mode = Integer.MIN_VALUE;
            String diagnostic;
            try {
                Bundle payload = new Bundle();
                payload.putInt(Constants.CMD_ID, NewEnergyID.ID_DRIVE_MODE);
                VDEvent reply = transport.read(new VDEvent(VDEventCarInfo.MODULE_NEW_ENERGY, payload));
                Bundle result = reply != null ? reply.getPayload() : null;
                int[] values = result != null ? result.getIntArray(Constants.VALUE) : null;
                if (reply != null && reply.getId() == VDEventCarInfo.MODULE_NEW_ENERGY
                        && result != null && result.getInt(Constants.CMD_ID, NewEnergyID.ID_DRIVE_MODE)
                                == NewEnergyID.ID_DRIVE_MODE
                        && values != null && values.length > 0) {
                    // Preserve VehicleMetricsCache's existing raw-array decoding.
                    mode = values[0];
                    if (values.length > 1) {
                        mode = 0;
                        for (int value : values) mode = (mode << 8) | (value & 0xFF);
                    }
                    diagnostic = "reported mode=" + mode;
                } else {
                    diagnostic = "missing or invalid mode reply";
                }
            } catch (RuntimeException unavailable) {
                diagnostic = "mode read unavailable: " + unavailable.getClass().getSimpleName();
            }
            int reportedMode = mode;
            String readDiagnostic = diagnostic;
            main.post(() -> {
                if (ticket != generation || listeners.isEmpty()) return;
                inFlight = false;
                current = new HeaderStatusMonitor.ModeReading(reportedMode != Integer.MIN_VALUE, reportedMode);
                if (!readDiagnostic.equals(lastDiagnostic)) {
                    Log.i("WidgetDriveMode", readDiagnostic);
                    lastDiagnostic = readDiagnostic;
                }
                for (Consumer<HeaderStatusMonitor.ModeReading> listener : new ArrayList<>(listeners)) {
                    listener.accept(current);
                }
            });
        });
    }
}
