package yagen.waitmydawn.tennory.combat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record Chambers(int next, List<Long> readyTicks) {
    public static final Chambers EMPTY = new Chambers(0, List.of());

    public Chambers {
        readyTicks = List.copyOf(readyTicks);
    }

    public int readyChamber(int count, long now) {
        for (int offset = 0; offset < count; offset++) {
            int chamber = (Math.floorMod(next, count) + offset) % count;
            if (chamber >= readyTicks.size() || readyTicks.get(chamber) <= now) {
                return chamber;
            }
        }
        return -1;
    }

    public Chambers discharge(int count, int chamber, long readyTick) {
        List<Long> updated = new ArrayList<>(Collections.nCopies(count, 0L));
        for (int index = 0; index < Math.min(count, readyTicks.size()); index++) {
            updated.set(index, readyTicks.get(index));
        }
        updated.set(chamber, readyTick);
        return new Chambers((chamber + 1) % count, updated);
    }
}
