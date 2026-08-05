package chanhne.AntiCheat.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ViolationTracker {

    private final Map<UUID, Integer> violations = new ConcurrentHashMap<>();

    public int increase(UUID uuid) {
        return violations.merge(uuid, 1, Integer::sum);
    }

    public void reset(UUID uuid) {
        violations.remove(uuid);
    }

    public int get(UUID uuid) {
        return violations.getOrDefault(uuid, 0);
    }
}