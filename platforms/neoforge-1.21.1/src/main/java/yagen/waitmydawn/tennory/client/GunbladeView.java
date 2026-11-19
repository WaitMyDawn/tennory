package yagen.waitmydawn.tennory.client;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import yagen.waitmydawn.tennory.combat.VolleyProfile;
import yagen.waitmydawn.tennory.registries.TennoryRegistries;
import yagen.waitmydawn.tennory.weapon.GunbladeItem;

final class GunbladeView {
    private static final float RAISE_TICKS = 3.0F;
    private static final float HOLD_TICKS = 15.0F;
    private static final float LOWER_TICKS = 16.0F;
    private static final float MELEE_RETURN_TICKS = 2.0F;
    private static final float KICK_TICKS = 4.0F;
    private static final long NO_SHOT = Long.MIN_VALUE;

    private final ArrayDeque<Long> releaseTicks = new ArrayDeque<>();
    private WeakReference<AbstractClientPlayer> holder = new WeakReference<>(null);
    private Item item;
    private int slot;
    private long lastGesture = NO_SHOT;
    private double raiseAt;
    private double lowerAt;
    private float raiseFrom;
    private double meleeAt = Double.POSITIVE_INFINITY;
    private float meleeFrom;

    void update(AbstractClientPlayer player) {
        if (player == null || !(player.getMainHandItem().getItem() instanceof GunbladeItem)) {
            if (player != holder.get() || item != null) {
                reset(player, null, -1);
            }
            if (player != null) {
                var gesture = player.getExistingData(TennoryRegistries.VOLLEY_GESTURE).orElse(null);
                if (gesture != null && gesture.profileId() == VolleyProfile.REDEEMER.ordinal()) {
                    lastGesture = Math.max(lastGesture, gesture.startTick());
                }
            }
            return;
        }
        observe(player, player.level().getGameTime(), player.swinging && player.swingingArm == InteractionHand.MAIN_HAND);
    }

    Pose sample(AbstractClientPlayer player, float partialTick, float swing) {
        double now = player.level().getGameTime() + (double) partialTick;
        observe(player, now, swing > 0.0F);
        float age = Float.POSITIVE_INFINITY;
        for (long releaseTick : releaseTicks) {
            if (releaseTick > now) {
                break;
            }
            age = (float) (now - releaseTick);
        }
        float recoil = VolleyMotion.ease(age / 0.55F) * (1.0F - VolleyMotion.ease((age - 0.55F) / (KICK_TICKS - 0.55F)));
        return new Pose(aim(now), recoil, VolleyMotion.flash(age));
    }

    private void observe(AbstractClientPlayer player, double now, boolean swinging) {
        Item held = player.getMainHandItem().getItem();
        int selected = player.getInventory().selected;
        if (player != holder.get() || item != held || slot != selected) {
            reset(player, held, selected);
        }
        var gesture = player.getExistingData(TennoryRegistries.VOLLEY_GESTURE).orElse(null);
        if (gesture != null && gesture.profileId() == VolleyProfile.REDEEMER.ordinal() && gesture.startTick() > lastGesture) {
            long release = gesture.startTick() + VolleyProfile.REDEEMER.releaseTicks();
            // Restart the hold window from the current pose, including a shot during recovery.
            raiseFrom = aim(now);
            raiseAt = now;
            lowerAt = release + HOLD_TICKS;
            meleeAt = Double.POSITIVE_INFINITY;
            releaseTicks.addLast(release);
            lastGesture = gesture.startTick();
        }
        if (swinging && Double.isInfinite(meleeAt)) {
            meleeFrom = aim(now);
            meleeAt = now;
        }
        while (!releaseTicks.isEmpty() && now - releaseTicks.getFirst() >= KICK_TICKS) {
            releaseTicks.removeFirst();
        }
    }

    private float aim(double now) {
        if (now >= meleeAt) {
            return meleeFrom * (1.0F - VolleyMotion.ease((float) (now - meleeAt) / MELEE_RETURN_TICKS));
        }
        float raised = Mth.lerp(VolleyMotion.ease((float) (now - raiseAt) / RAISE_TICKS), raiseFrom, 1.0F);
        return raised * (1.0F - VolleyMotion.ease((float) (now - lowerAt) / LOWER_TICKS));
    }

    private void reset(AbstractClientPlayer player, Item held, int selected) {
        if (player != holder.get()) {
            lastGesture = NO_SHOT;
            holder = new WeakReference<>(player);
        }
        item = held;
        slot = selected;
        raiseAt = Double.NEGATIVE_INFINITY;
        lowerAt = Double.NEGATIVE_INFINITY;
        raiseFrom = 0.0F;
        meleeAt = Double.POSITIVE_INFINITY;
        releaseTicks.clear();
    }

    record Pose(float aim, float recoil, float flash) {
    }
}
