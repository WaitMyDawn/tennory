package yagen.waitmydawn.tennory.client;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Quaternionf;
import yagen.waitmydawn.tennory.client.QuassusComboStep.Hand;
import yagen.waitmydawn.tennory.weapon.QuassusItem;

final class QuassusBetterCombat {
    private static final float FAN_BLADE_LIFT = -20.0F * Mth.DEG_TO_RAD;

    private QuassusBetterCombat() {
    }

    static boolean ownsThirdPersonPose(AbstractClientPlayer player) {
        return player.getMainHandItem().getItem() instanceof QuassusItem && BetterCombatSwing.hasPlayerAnimation(player);
    }

    static Frame sampleFirstPerson(LocalPlayer player, float partialTick) {
        if (!(player.getMainHandItem().getItem() instanceof QuassusItem)) {
            return null;
        }
        var swing = BetterCombatSwing.sample(player, partialTick);
        var step = swing == null ? null : QuassusComboStep.byAnimationId(swing.animation());
        return step == null ? null : step.sample(swing.progress(), swing.impact(), player.getMainArm() == HumanoidArm.LEFT);
    }

    static void rotateWrist(AbstractClientPlayer player, HumanoidArm arm, float partialTick, Quaternionf wrist) {
        if (ownsThirdPersonPose(player)) {
            BetterCombatSwing.applyItemRotation(player, arm, partialTick, wrist);
            // The mesh's bottom edge runs along local X through the grip.
            wrist.rotateX(FAN_BLADE_LIFT);
        }
    }

    record Frame(Hand primary, Hand secondary, boolean mirrored) {
        Hand hand(HumanoidArm arm) {
            return (arm == HumanoidArm.RIGHT) != mirrored ? primary : secondary;
        }
    }
}
