package yagen.waitmydawn.tennory.client;

import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import yagen.waitmydawn.tennory.glaive.GlaiveWeaponItem;
import yagen.waitmydawn.tennory.network.ClientWeaponState;

record GlaiveMotion(float draw, float cast) {
    private static final float RELEASE_TICKS = 3.0F;
    private static final float RECOVERY_TICKS = 7.0F;

    static GlaiveMotion sample(Player player, float partialTick) {
        var held = player.getMainHandItem();
        if (held.getItem() instanceof GlaiveWeaponItem weapon && player.isUsingItem()
                && player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
            return new GlaiveMotion(ease((player.getTicksUsingItem() + partialTick) / weapon.chargeTicks()), 0.0F);
        }
        if (!held.isEmpty() && !(held.getItem() instanceof GlaiveWeaponItem)) {
            return null;
        }
        long start = ClientWeaponState.get(player).throwTick();
        if (start == Long.MIN_VALUE) {
            return null;
        }
        float elapsed = Math.max(0.0F, player.level().getGameTime() - start + partialTick);
        if (elapsed < RELEASE_TICKS) {
            float cast = ease(elapsed / RELEASE_TICKS);
            return new GlaiveMotion(1.0F - cast, cast);
        }
        if (elapsed < RELEASE_TICKS + RECOVERY_TICKS) {
            return new GlaiveMotion(0.0F, 1.0F - ease((elapsed - RELEASE_TICKS) / RECOVERY_TICKS));
        }
        return null;
    }

    float weight() {
        return draw + cast;
    }

    private static float ease(float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
