package org.tracking;

import org.utils.FileUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TestOutcomeTracker {

    private static final Map<String, Boolean> outcomes = new ConcurrentHashMap<>();

    public static void pass(String testId) {
        outcomes.put(testId, true);
    }

    public static void fail(String testId) {
        outcomes.put(testId, false);
    }

    public static void clear() {
        outcomes.clear();
    }

    public static void dumpTo(Path file) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Boolean> e : outcomes.entrySet()) {
            sb.append(e.getKey())
                    .append('\t')
                    .append(e.getValue() ? "PASS" : "FAIL")
                    .append('\n');
        }
        FileUtils.writeAtomic(file, sb.toString());
    }
}