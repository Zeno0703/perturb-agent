package org.probe;

public final class PerturbationController {

    private static final int ACTIVE_PROBE = Integer.getInteger("perturb.activeProbe", -1);

    private PerturbationController() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static boolean isActive(int id) {
        return id == ACTIVE_PROBE;
    }

}