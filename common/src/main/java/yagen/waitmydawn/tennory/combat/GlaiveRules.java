package yagen.waitmydawn.tennory.combat;

public final class GlaiveRules {
    public static final int CHARGE_TICKS = 20;
    public static final int BOUNCE_LIMIT = 3;
    public static final double RICOCHET_RADIUS = 4.0;
    public static final double MAX_DISTANCE = 64.0;
    public static final double THROW_SPEED = 1.0;
    public static final double RETURN_SPEED = 1.25;
    public static final double BLAST_RADIUS = 4.0;

    private GlaiveRules() {
    }

    public static double detonationReturnCurve(double distance) {
        return Math.min(distance / 32.0, 1.0);
    }

    public static double outboundDistance(double distanceSquared, double outwardProjection, double requested) {
        double room = MAX_DISTANCE * MAX_DISTANCE - distanceSquared;
        if (room <= 0.0) {
            return 0.0;
        }
        // Intersect the normalized flight ray with the sphere centered on the current owner.
        return Math.min(requested, Math.sqrt(outwardProjection * outwardProjection + room) - outwardProjection);
    }
}
