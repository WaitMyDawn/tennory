package yagen.waitmydawn.tennory.combat;

import java.util.Collections;
import java.util.List;

public enum VolleyProfile {
    REDEEMER(36, 16, 4, 8.0, 64.0, Collections.nCopies(3, Spread.rings(5.0, 3, 6))),
    QUASSUS(40, 18, 5, 2.0, 32.0, List.of(Spread.horizontal(30.0, 18), Spread.horizontal(20.0, 9)));

    private final int reloadTicks;
    private final int motionTicks;
    private final int releaseTicks;
    private final double speed;
    private final double range;
    private final List<List<Spread.Ray>> chambers;

    VolleyProfile(int reloadTicks, int motionTicks, int releaseTicks, double speed, double range, List<List<Spread.Ray>> chambers) {
        this.reloadTicks = reloadTicks;
        this.motionTicks = motionTicks;
        this.releaseTicks = releaseTicks;
        this.speed = speed;
        this.range = range;
        this.chambers = chambers;
    }

    public int chamberCount() {
        return chambers.size();
    }

    public int reloadTicks() {
        return reloadTicks;
    }

    public int motionTicks() {
        return motionTicks;
    }

    public int releaseTicks() {
        return releaseTicks;
    }

    public double speed() {
        return speed;
    }

    public double range() {
        return range;
    }

    public List<Spread.Ray> rays(int chamber) {
        return chambers.get(chamber);
    }
}
