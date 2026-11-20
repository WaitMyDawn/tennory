package yagen.waitmydawn.tennory.client;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import java.lang.ref.WeakReference;
import net.bettercombat.api.AttackHand;
import net.bettercombat.api.MinecraftClient_BetterCombat;
import net.bettercombat.api.client.BetterCombatClientEvents;
import net.bettercombat.logic.PlayerAttackHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.fml.ModList;
import org.joml.Quaternionf;
import org.joml.Vector3f;

record BetterCombatSwing(String animation, float progress, float impact) {
    private static final boolean LOADED = ModList.get().isLoaded("bettercombat") && ModList.get().isLoaded("playeranimator");

    static void register() {
        if (LOADED) Active.register();
    }

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
        private static WeakReference<LocalPlayer> attacker = new WeakReference<>(null);
        private static AttackHand activeHand;

        static void register() {
            // Better Combat advances the combo index on impact, before the current swing finishes.
            BetterCombatClientEvents.ATTACK_START.register((player, hand) -> {
                attacker = new WeakReference<>(player);
                activeHand = hand;
            });
        }

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
            var attack = activeHand;
            if (attacker.get() != player || !combat.isWeaponSwingInProgress() || attack == null
                    || attack.isOffHand() || !attack.itemStack().is(player.getMainHandItem().getItem())) {
                return null;
            }
            float duration = PlayerAttackHelper.getAttackCooldownTicksCapped(player);
            float impact = Mth.clamp(Math.round(duration * (float) attack.upswingRate()) / duration, 0.12F, 0.88F);
            return new BetterCombatSwing(attack.attack().animation(), Mth.clamp(combat.getSwingProgress() + partialTick / duration,
                    0.0F, 1.0F), impact);
        }
    }
}

