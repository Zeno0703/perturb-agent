package org.tracking;

import org.utils.FileUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ProbeExecutionTracker {

    private static final Map<String, Set<Integer>> hits = new ConcurrentHashMap<>();
    private static final Queue<String> actions = new ConcurrentLinkedQueue<>();

    public static void record(String testId, int probeId) {
        hits.computeIfAbsent(testId, k -> ConcurrentHashMap.newKeySet()).add(probeId);
    }

    public static void recordAction(String testId, Object original, Object perturbed) {
        actions.add(testId + "\t" + original + " -> " + perturbed);
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