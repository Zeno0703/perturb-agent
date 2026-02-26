package org.probe;

import org.tracking.ProbeExecutionTracker;
import org.tracking.TestContext;

public class PerturbationGate {

    public static int apply(int value, int probeId) {
        track(probeId);
        if (PerturbationController.isActive(probeId)) {
            int newValue = value + 1;
            ProbeExecutionTracker.recordAction(value, newValue);
            return newValue;
        }
        return value;
    }

    public static boolean apply(boolean value, int probeId) {
        track(probeId);
        if (PerturbationController.isActive(probeId)) {
            boolean newValue = !value;
            ProbeExecutionTracker.recordAction(value, newValue);
            return newValue;
        }
        return value;
    }

    public static Object apply(Object value, int probeId) {
        track(probeId);
        if (PerturbationController.isActive(probeId)) {
            ProbeExecutionTracker.recordAction(value, "null");
            return null;
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