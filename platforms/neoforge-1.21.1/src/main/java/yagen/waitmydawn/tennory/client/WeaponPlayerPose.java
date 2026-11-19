package yagen.waitmydawn.tennory.client;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class WeaponPlayerPose {
    private static final float DEG = Mth.DEG_TO_RAD;

    private WeaponPlayerPose() {
    }

    static boolean canPose(AbstractClientPlayer player) {
        return !player.isFallFlying() && !player.isVisuallySwimming() && !player.isSleeping() && !NinjasAnimationCompat.ownsPose();
    }

    static BaseOffsets applyStance(PlayerModel<?> model, Stance stance, float walk) {
        if (model.riding) {
            return null;
        }
        BaseOffsets offsets = new BaseOffsets(model);
        float footWeight = 1.0F - 0.69F * Mth.clamp(walk, 0.0F, 1.0F);
        float yaw = stance.yaw() * DEG;
        float pitch = stance.pitch() * DEG;
        float roll = stance.roll() * DEG;
        Quaternionf turn = new Quaternionf().rotationZYX(roll, yaw, pitch);
        float hipY = (model.rightLeg.y + model.leftLeg.y) * 0.5F;
        offsetAtHips(model.body, turn, hipY, stance);
        offsetAtHips(model.head, turn, hipY, stance);
        offsetAtHips(model.rightArm, turn, hipY, stance);
        offsetAtHips(model.leftArm, turn, hipY, stance);
        model.body.xRot += pitch;
        model.body.yRot += yaw;
        model.body.zRot += roll;
        model.head.zRot += roll * 0.23F;
        braceLeg(model.rightLeg, 1.0F, stance, footWeight);
        braceLeg(model.leftLeg, -1.0F, stance, footWeight);
        return offsets;
    }

    private static void offsetAtHips(ModelPart part, Quaternionf turn, float hipY, Stance stance) {
        Vector3f origin = new Vector3f(part.x, part.y - hipY, part.z);
        turn.transform(origin);
        part.setPos(origin.x + stance.lean(), origin.y + hipY + stance.drop(), origin.z + stance.fore());
    }

    private static void braceLeg(ModelPart leg, float side, Stance stance, float weight) {
        float lead = side * stance.stride() * weight;
        float spread = weight * (Math.abs(stance.stride()) * 0.46F + stance.drop() * 0.026F);
        leg.xRot += lead;
        leg.yRot += weight * stance.yaw() * DEG * 0.41F - side * spread;
        leg.zRot += side * spread;
        leg.x += stance.lean() * weight * 0.70F;
        leg.z += weight * stance.fore() * 0.46F + lead * 1.6F;
        leg.y += weight * stance.drop() * 0.31F;
    }

    static void copyWear(PlayerModel<?> model) {
        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightPants.copyFrom(model.rightLeg);
        model.leftPants.copyFrom(model.leftLeg);
    }

    record Stance(float yaw, float pitch, float roll, float lean, float drop, float fore, float stride) {
        static final Stance REST = new Stance(0, 0, 0, 0, 0, 0, 0);

        Stance mirror(float side) {
            return new Stance(yaw * side, pitch, roll * side, lean * side, drop, fore, stride * side);
        }

        Stance lerp(Stance to, float t) {
            return new Stance(Mth.lerp(t, yaw, to.yaw), Mth.lerp(t, pitch, to.pitch), Mth.lerp(t, roll, to.roll),
                    Mth.lerp(t, lean, to.lean), Mth.lerp(t, drop, to.drop), Mth.lerp(t, fore, to.fore),
                    Mth.lerp(t, stride, to.stride));
        }
    }

    // Vanilla resets the remaining pose fields on every setupAnim call.
    public record BaseOffsets(float bodyX, float bodyZ, float bodyRoll, float headX, float headZ, float headRoll,
                              float rightHip, float leftHip) {
        BaseOffsets(PlayerModel<?> model) {
            this(model.body.x, model.body.z, model.body.zRot, model.head.x, model.head.z, model.head.zRot,
                    model.rightLeg.x, model.leftLeg.x);
        }

        public void restore(PlayerModel<?> model) {
            model.body.x = bodyX;
            model.body.z = bodyZ;
            model.body.zRot = bodyRoll;
            model.head.x = headX;
            model.head.z = headZ;
            model.head.zRot = headRoll;
            model.rightLeg.x = rightHip;
            model.leftLeg.x = leftHip;
        }
    }
}
