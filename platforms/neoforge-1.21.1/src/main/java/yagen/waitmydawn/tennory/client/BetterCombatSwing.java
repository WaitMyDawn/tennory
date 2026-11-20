package yagen.waitmydawn.tennory.client;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import net.bettercombat.api.MinecraftClient_BetterCombat;
import net.bettercombat.logic.PlayerAttackHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.fml.ModList;
import org.joml.Quaternionf;
import org.joml.Vector3f;

record BetterCombatSwing(String animation, float progress, float impact) {
    private static final boolean LOADED = ModList.get().isLoaded("bettercombat") && ModList.get().isLoaded("playeranimator");

    static BetterCombatSwing sample(LocalPlayer player, float partialTick) {
        return LOADED ? Active.sample(player, partialTick) : null;
    }

    static boolean hasPlayerAnimation(AbstractClientPlayer player) {
        return LOADED && Active.hasPlayerAnimation(player);
    }

    static void applyItemRotation(AbstractClientPlayer player, HumanoidArm arm, float partialTick, Quaternionf wrist) {
        if (LOADED) {
            Active.applyItemRotation(player, arm, partialTick, wrist);
        }
    }

    private static final class Active {
        static boolean hasPlayerAnimation(AbstractClientPlayer player) {
            return PlayerAnimationAccess.getPlayerAnimLayer(player).isActive();
        }

        static void applyItemRotation(AbstractClientPlayer player, HumanoidArm arm, float partialTick, Quaternionf wrist) {
            String part = arm == HumanoidArm.RIGHT ? "rightItem" : "leftItem";
            // The animated arm places the grip. The item track controls the weapon's model-space orientation.
            Vector3f neutral = wrist.getEulerAnglesYXZ(new Vector3f());
            var aim = PlayerAnimationAccess.getPlayerAnimLayer(player).get3DTransform(part, TransformType.ROTATION,
                    partialTick, new Vec3f(neutral.x, neutral.y, neutral.z));
            wrist.rotationYXZ(aim.getY(), aim.getX(), aim.getZ());
        }

        static BetterCombatSwing sample(LocalPlayer player, float partialTick) {
            var combat = (MinecraftClient_BetterCombat) Minecraft.getInstance();
            var attack = combat.getCurrentAttackHand();
            if (attack == null || attack.isOffHand() || !attack.itemStack().is(player.getMainHandItem().getItem())) {
                return null;
            }
            float duration = PlayerAttackHelper.getAttackCooldownTicksCapped(player);
            float impact = Mth.clamp(Math.round(duration * (float) attack.upswingRate()) / duration, 0.12F, 0.88F);
            return new BetterCombatSwing(attack.attack().animation(), Mth.clamp(combat.getSwingProgress() + partialTick / duration,
                    0.0F, 1.0F), impact);
        }
    }
}
