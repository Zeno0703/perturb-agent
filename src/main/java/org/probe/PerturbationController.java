package org.probe;

public final class PerturbationController {

    private static final int UNINITIALIZED = Integer.MIN_VALUE;
    private static volatile int active = UNINITIALIZED;

    private PerturbationController() {
        throw new UnsupportedOperationException("Utility class");
    }

    private static int configuredActiveProbe() {
        String raw = System.getProperty("perturb.activeProbe", "-1");
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    public static boolean isActive(int id) {
        if (active == UNINITIALIZED) {
            active = configuredActiveProbe();
        }
        return id == active;
    }

}