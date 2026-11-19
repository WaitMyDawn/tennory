package yagen.waitmydawn.tennory.client;

import java.lang.reflect.Method;
import net.neoforged.fml.ModList;

final class NinjasAnimationCompat {
    private static final String MOD_ID = "ninjas_free_play";
    private static final String CONTEXT_TYPE = "yagen.waitmydawn.ninjas_free_play.render.PlayerAnimationContext";
    private static final boolean LOADED = ModList.get().isLoaded(MOD_ID);
    private static Method current = findContext();

    private NinjasAnimationCompat() {
    }

    static boolean ownsPose() {
        if (current != null) {
            try {
                return current.invoke(null) != null;
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                current = null;
            }
        }
        // Prefer the other mod's pose pipeline if a different version does not expose its render scope.
        return LOADED;
    }

    private static Method findContext() {
        if (LOADED) {
            try {
                Class<?> context = Class.forName(CONTEXT_TYPE, false, NinjasAnimationCompat.class.getClassLoader());
                return context.getMethod("current");
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                return null;
            }
        }
        return null;
    }
}
