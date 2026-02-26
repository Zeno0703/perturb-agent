package org.tracking;

import org.utils.FileUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ProbeExecutionTracker {

    private static final Map<String, Set<Integer>> hits = new ConcurrentHashMap<>();
    private static final Set<String> actions = ConcurrentHashMap.newKeySet();

    public static void record(String testId, int probeId) {
        hits.computeIfAbsent(testId, k -> ConcurrentHashMap.newKeySet()).add(probeId);
    }

    public static void recordAction(Object original, Object perturbed) {
        String testId = TestContext.getCurrent();
        String prefix = (testId != null) ? testId : "UNKNOWN_TEST";

        actions.add(prefix + "\t" + original + " -> " + perturbed);
    }

    public static void clear() {
        hits.clear();
        actions.clear();
    }

    public static void dumpTo(Path file) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Set<Integer>> e : hits.entrySet()) {
            String testId = e.getKey();
            for (Integer probeId : e.getValue()) {
                sb.append(probeId).append("\t").append(testId).append("\n");
            }
        }
        FileUtils.writeAtomic(file, sb.toString());
    }

    public static void dumpActionsTo(Path file) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String action : actions) {
            sb.append(action).append("\n");
        }
        FileUtils.writeAtomic(file, sb.toString());
    }
}