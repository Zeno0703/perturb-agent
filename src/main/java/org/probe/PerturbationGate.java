package org.probe;

import org.tracking.ProbeExecutionTracker;
import org.tracking.TestContext;

public final class PerturbationGate {

    private PerturbationGate() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static int apply(int value, int probeId) {
        track(probeId);
        if (PerturbationController.isActive(probeId)) {
            return value + 1;
        }
        return value;
    }

    public static boolean apply(boolean value, int probeId) {
        track(probeId);
        if (PerturbationController.isActive(probeId)) {
            return !value;
        }
        return value;
    }

    private static void track(int probeId) {
        String test = TestContext.getCurrent();
        if (test != null) {
            ProbeExecutionTracker.record(test, probeId);
        }
    }
}