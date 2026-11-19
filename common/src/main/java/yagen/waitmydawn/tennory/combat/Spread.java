package yagen.waitmydawn.tennory.combat;

import java.util.ArrayList;
import java.util.List;

public final class Spread {
    private Spread() {
    }

    public record Ray(double right, double up) {
    }

    public static List<Ray> rings(double halfAngle, int... pelletsPerRing) {
        double outer = Math.tan(Math.toRadians(halfAngle));
        List<Ray> rays = new ArrayList<>();
        rays.add(new Ray(0.0, 0.0));
        for (int ring = 0; ring < pelletsPerRing.length; ring++) {
            int count = pelletsPerRing[ring];
            double radius = outer * (ring + 1) / pelletsPerRing.length;
            for (int index = 0; index < count; index++) {
                double angle = (index + 0.5 * ring) * 2.0 * Math.PI / count;
                rays.add(new Ray(Math.cos(angle) * radius, Math.sin(angle) * radius));
            }
        }
        return List.copyOf(rays);
    }

    public static List<Ray> horizontal(double halfAngle, int count) {
        double outer = Math.tan(Math.toRadians(halfAngle));
        List<Ray> rays = new ArrayList<>(count);
        // Space the intersections equally on a plane perpendicular to the aim.
        for (int index = 0; index < count; index++) {
            rays.add(new Ray(count == 1 ? 0.0 : outer * (2.0 * index / (count - 1) - 1.0), 0.0));
        }
        return List.copyOf(rays);
    }
}
