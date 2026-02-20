package org.probe;

import org.utils.FileUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class ProbeCatalog {

    private static final AtomicInteger counter = new AtomicInteger();
    private static final Map<String, Integer> locations = new ConcurrentHashMap<>();
    private static final Map<Integer, String> descriptions = new ConcurrentHashMap<>();
    private static final Set<Integer> probes = ConcurrentHashMap.newKeySet();
    private static volatile boolean frozen = false;

    private ProbeCatalog() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static int idForLocation(String locationKey) {
        Integer existing = locations.get(locationKey);
        if (existing != null) {
            return existing;
        }

        if (frozen) {
            return -1;
        }

        int id = counter.incrementAndGet();
        Integer previous = locations.putIfAbsent(locationKey, id);
        int chosen = previous != null ? previous : id;
        probes.add(chosen);
        return chosen;
    }

    public static void freeze() {
        frozen = true;
    }

    public static Set<Integer> allProbeIds() {
        return Set.copyOf(probes);
    }

    public static void describe(int probeId, String description) {
        descriptions.put(probeId, description);
    }

    public static String descriptionFor(int probeId) {
        String description = descriptions.get(probeId);
        if (description != null) {
            return description;
        }
        return "probe " + probeId;
    }

    public static void dumpTo(Path file) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int id : allProbeIds()) {
            sb.append(id).append("\t").append(descriptionFor(id)).append("\n");
        }
        FileUtils.writeAtomic(file, sb.toString());
    }
}