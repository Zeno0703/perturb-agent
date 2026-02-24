package org.probe;

public class PerturbationController {

    private static final int ACTIVE_PROBE = Integer.getInteger("perturb.activeProbe", -1);

    public static boolean isActive(int id) {
        return id == ACTIVE_PROBE;
    }
}