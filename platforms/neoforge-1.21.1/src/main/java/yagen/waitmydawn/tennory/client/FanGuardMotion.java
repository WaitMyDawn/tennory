package yagen.waitmydawn.tennory.client;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Vector3f;
import yagen.waitmydawn.tennory.client.WeaponArmRig.ArmPose;
import yagen.waitmydawn.tennory.client.WeaponPlayerPose.Stance;
import yagen.waitmydawn.tennory.combat.VolleyProfile;

final class FanGuardMotion {
    private static final Pose GUARD = new Pose(
            ArmPose.facing(new Vector3f(0.17F, 0.69F, -0.70F), new Vector3f(-0.83F, 0.16F, -0.54F),
                    new Vector3f(0.10F, 0.99F, -0.10F), new Vector3f(-0.30F, -0.14F, 0.94F))
                    .rollGrip(Mth.HALF_PI),
            ArmPose.facing(new Vector3f(-0.47F, 0.39F, -0.79F), new Vector3f(-0.18F, -0.94F, -0.29F),
                    new Vector3f(-0.32F, -0.89F, -0.18F), new Vector3f(0.97F, -0.05F, 0.23F)),
            new Stance(0, 0, 0, 0, 0, 0, 0));
    private static final Pose RIGHT_DRAW = new Pose(
            GUARD.left(),
            ArmPose.facing(new Vector3f(-0.65F, 0.59F, -0.48F), new Vector3f(-0.30F, -0.95F, 0.10F),
                    new Vector3f(-0.48F, -0.83F, 0.18F), new Vector3f(0.76F, 0.08F, 0.65F)),
            new Stance(22, 4, -7, -0.9F, 1.0F, 0.35F, -0.13F));
    private static final Pose RIGHT_RELEASE = new Pose(
            ArmPose.facing(new Vector3f(0.72F, 0.56F, 0.41F), new Vector3f(0.22F, 0.09F, 0.97F),
                    new Vector3f(0.42F, 0.54F, 0.73F), new Vector3f(-0.72F, -0.54F, 0.44F)),
            ArmPose.facing(new Vector3f(-0.04F, 0.11F, -0.99F), new Vector3f(0.26F, -0.12F, -0.96F),
                    new Vector3f(0.36F, -0.29F, -0.88F), new Vector3f(0.22F, -0.95F, 0.27F)),
            new Stance(-39, 15, 9, 0.25F, 0.95F, -1.35F, -0.34F));
    private static final Pose RIGHT_FOLLOW = new Pose(
            ArmPose.facing(new Vector3f(0.55F, 0.68F, 0.49F), new Vector3f(0.29F, -0.42F, 0.86F),
                    new Vector3f(0.35F, 0.22F, 0.91F), new Vector3f(-0.73F, -0.51F, 0.43F)),
            ArmPose.facing(new Vector3f(0.34F, 0.24F, -0.91F), new Vector3f(0.78F, 0.18F, -0.60F),
                    new Vector3f(0.92F, 0.18F, -0.34F), new Vector3f(0.04F, -0.89F, 0.45F)),
            new Stance(-48, 12, 12, 0.85F, 0.55F, -0.75F, -0.25F));
    private static final Pose LEFT_READY = new Pose(
            ArmPose.facing(new Vector3f(0.49F, 0.60F, 0.63F), new Vector3f(0.18F, -0.54F, 0.83F),
                    new Vector3f(0.40F, 0.11F, 0.91F), new Vector3f(-0.74F, -0.53F, 0.43F)),
            ArmPose.facing(new Vector3f(0.28F, 0.26F, -0.93F), new Vector3f(0.82F, 0.12F, -0.56F),
                    new Vector3f(0.80F, 0.52F, -0.27F), new Vector3f(0.33F, -0.84F, 0.44F)),
            new Stance(-38, 9, 7, 0.43F, 0.42F, -0.55F, -0.18F));
    private static final Pose LEFT_RELEASE = RIGHT_RELEASE.mirror();
    private static final Pose LEFT_FOLLOW = RIGHT_FOLLOW.mirror();
    private static final float FOLLOW_TICKS = 3.0F;
    private static final float LEFT_READY_TICKS = 9.0F;
    private static final float RIGHT_RECOVERY_TICKS = 7.0F;

    private Pose entry = GUARD;
    private long started = Long.MIN_VALUE;
    private int chamber;

    Pose sample(VolleyMotion.Frame frame, double now) {
        if (frame != null && frame.startTick() > started) {
            // Evaluate the previous swing at the actual input boundary before switching the leading hand.
            entry = poseAt(frame.startTick());
            started = frame.startTick();
            chamber = frame.chamber();
        }
        return poseAt(now);
    }

    private Pose poseAt(double now) {
        float age = (float) (now - started);
        float release = VolleyProfile.QUASSUS.releaseTicks();
        float follow = release + FOLLOW_TICKS;
        boolean right = chamber == 0;
        float lowerAt = right ? follow + LEFT_READY_TICKS : follow;
        float end = right ? lowerAt + RIGHT_RECOVERY_TICKS : VolleyProfile.QUASSUS.motionTicks();
        if (age >= end) {
            return GUARD;
        }
        Pose draw = right ? RIGHT_DRAW : LEFT_READY;
        Pose strike = right ? RIGHT_RELEASE : LEFT_RELEASE;
        Pose finish = right ? RIGHT_FOLLOW : LEFT_FOLLOW;
        float drawEnd = release - 2.0F;
        if (age < drawEnd) {
            return entry.lerp(draw, VolleyMotion.ease(age / drawEnd));
        }
        if (age < release) {
            return draw.lerp(strike, VolleyMotion.ease((age - drawEnd) / (release - drawEnd)));
        }
        if (age < follow) {
            return strike.lerp(finish, VolleyMotion.ease((age - release) / FOLLOW_TICKS));
        }
        if (right && age < lowerAt) {
            return finish.lerp(LEFT_READY, VolleyMotion.ease((age - follow) / LEFT_READY_TICKS));
        }
        return (right ? LEFT_READY : finish).lerp(GUARD, VolleyMotion.ease((age - lowerAt) / (end - lowerAt)));
    }

    record Pose(ArmPose left, ArmPose right, Stance stance) {
        boolean inGuard() {
            return this == GUARD;
        }

        ArmPose arm(HumanoidArm arm) {
            return arm == HumanoidArm.LEFT ? left : right;
        }

        Pose lerp(Pose target, float t) {
            return new Pose(left.lerp(target.left, t), right.lerp(target.right, t), stance.lerp(target.stance, t));
        }

        Pose mirror() {
            return new Pose(right.mirror(), left.mirror(), stance.mirror(-1.0F));
        }
    }
}
