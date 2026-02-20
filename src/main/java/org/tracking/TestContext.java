package org.tracking;

public final class TestContext {

    private static final ThreadLocal<String> current = new ThreadLocal<>();

    private TestContext() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void enter(String id) {
        current.set(id);
    }

    public static String getCurrent() {
        return current.get();
    }

    public static void exit() {
        current.remove();
    }
}