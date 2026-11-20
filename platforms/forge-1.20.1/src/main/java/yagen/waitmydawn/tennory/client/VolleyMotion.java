package yagen.waitmydawn.tennory.client;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import yagen.waitmydawn.tennory.client.WeaponPlayerPose.Stance;
import yagen.waitmydawn.tennory.combat.VolleyProfile;
import yagen.waitmydawn.tennory.network.ClientWeaponState;
import yagen.waitmydawn.tennory.weapon.VolleyWeaponItem;

final class VolleyMotion {
    private static final FanHand DRAW = new FanHand(0.10F, 0.02F, -0.13F, -48, 29, -32, -28, 25, -32);
    private static final FanHand RELEASE = new FanHand(-0.28F, 0.22F, 0.24F, 26, -33, 58, -79, -19, 23);
    private static final FanHand FOLLOW = new FanHand(-0.14F, 0.08F, 0.12F, 65, -17, 118, -53, -51, 40);
    private static final FanHand GUARD = new FanHand(-0.04F, 0.14F, 0.06F, 13, -17, 23, -33, -22, 20);
    private static final FanHand COUNTER = new FanHand(0.07F, 0.02F, -0.09F, -30, 18, -22, -16, 14, -15);

    private VolleyMotion() {
    }

    static Frame sample(Player player, float partialTick) {
        if (!(player.getMainHandItem().getItem() instanceof VolleyWeaponItem weapon)) {
            return null;
        }
        var gesture = ClientWeaponState.get(player).volley();
        VolleyProfile profile = weapon.profile();
        if (gesture == null || gesture.profileId() != profile.ordinal()
                || gesture.chamber() < 0 || gesture.chamber() >= profile.chamberCount()) {
            return null;
        }
        float age = Math.max(0.0F, player.level().getGameTime() - gesture.startTick() + partialTick);
        return age < profile.motionTicks() ? new Frame(profile, gesture.chamber(), gesture.startTick(), age) : null;
    }

    static float ease(float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    static float flash(float age) {
        return age >= 0.0F && age < 1.5F ? 1.0F - age / 1.5F : 0.0F;
    }

    static Stance gunStance(HumanoidArm mainArm, float aim, float kick) {
        float side = mainArm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        return new Stance(side * (13.0F * aim - 5.0F * kick), 8.0F * aim - 10.0F * kick,
                -side * (3.0F * aim + 2.0F * kick), -side * 0.8F * aim,
                0.65F * aim + 0.18F * kick, -0.85F * aim + 0.57F * kick, side * (0.21F * aim + 0.04F * kick));
    }

    record Frame(VolleyProfile profile, int chamber, long startTick, float age) {
        float sinceRelease() {
            return age - profile.releaseTicks();
        }

        HumanoidArm castingArm() {
            return chamber == 0 ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
        }

        FanHand fanHand(HumanoidArm arm) {
            boolean casting = arm == castingArm();
            return cast(casting ? DRAW : GUARD, casting ? RELEASE : COUNTER, casting ? FOLLOW : COUNTER);
        }

        private <T extends Keyframe<T>> T cast(T draw, T release, T follow) {
            float elapsed = sinceRelease();
            float drawEnd = profile.releaseTicks() - 2.0F;
            if (age < drawEnd) {
                return draw.rest().lerp(draw, ease(age / drawEnd));
            }
            if (elapsed < 0.0F) {
                return draw.lerp(release, ease((elapsed + 2.0F) / 2.0F));
            }
            if (elapsed < 3.0F) {
                return release.lerp(follow, ease(elapsed / 3.0F));
            }
            return follow.lerp(follow.rest(), ease((elapsed - 3.0F) / (profile.motionTicks() - profile.releaseTicks() - 3.0F)));
        }
    }

    private interface Keyframe<T> {
        T rest();

        T lerp(T target, float progress);
    }

    record FanHand(float lateral, float lift, float reach, float yaw, float pitch, float roll,
                   float armPitch, float armYaw, float armRoll) implements Keyframe<FanHand> {
        static final FanHand REST = new FanHand(0, 0, 0, 0, 0, 0, 0, 0, 0);

        @Override
        public FanHand rest() {
            return REST;
        }

        @Override
        public FanHand lerp(FanHand to, float t) {
            return new FanHand(Mth.lerp(t, lateral, to.lateral), Mth.lerp(t, lift, to.lift), Mth.lerp(t, reach, to.reach),
                    Mth.lerp(t, yaw, to.yaw), Mth.lerp(t, pitch, to.pitch), Mth.lerp(t, roll, to.roll),
                    Mth.lerp(t, armPitch, to.armPitch), Mth.lerp(t, armYaw, to.armYaw), Mth.lerp(t, armRoll, to.armRoll));
        }
    }

}
