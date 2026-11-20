package yagen.waitmydawn.tennory.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import yagen.waitmydawn.tennory.Tennory;

public enum RedeemerComboStep {
    LEFT_SLASH("redeemer_left_slash",
            new MeleeViewPose(0.60F, -0.49F, -0.88F, -39, 8, -22),
            new MeleeViewPose(0.13F, -0.25F, -1.01F, -22, -19, 57),
            new MeleeViewPose(-0.11F, -0.31F, -1.04F, 11, -24, 77)),
    RIGHT_SLASH("redeemer_right_slash",
            new MeleeViewPose(-0.21F, -0.34F, -1.08F, 5, -14, 62),
            new MeleeViewPose(0.22F, -0.26F, -1.03F, -18, -24, -48),
            new MeleeViewPose(0.52F, -0.31F, -1.02F, -35, -18, -55)),
    THRUST("redeemer_thrust",
            new MeleeViewPose(0.55F, -0.49F, -0.82F, -18, -58, -10),
            new MeleeViewPose(0.23F, -0.37F, -1.39F, 0, -77, 6),
            new MeleeViewPose(0.28F, -0.42F, -1.26F, -5, -74, 6));

    private static final MeleeViewPose HELD = new MeleeViewPose(0.56F, -0.52F, -0.72F, -40, 0, 24);
    private static final RedeemerComboStep[] STEPS = values();
    private final ResourceLocation clip;
    private final String animation;
    private final MeleeViewPose draw;
    private final MeleeViewPose strike;
    private final MeleeViewPose follow;

    RedeemerComboStep(String clip, MeleeViewPose draw, MeleeViewPose strike, MeleeViewPose follow) {
        this.clip = ResourceLocation.fromNamespaceAndPath(Tennory.MODID, clip);
        animation = this.clip.toString();
        this.draw = draw;
        this.strike = strike;
        this.follow = follow;
    }

    public ResourceLocation clip() {
        return clip;
    }

    static RedeemerComboStep byAnimation(String animation) {
        for (var step : STEPS) {
            if (step.animation.equals(animation)) {
                return step;
            }
        }
        return null;
    }

    MeleeViewPose sample(float progress, float impact) {
        float windup = impact * 0.5F;
        float followThrough = Mth.lerp(0.32F, impact, 1.0F);
        if (progress < windup) {
            return HELD.towards(draw, VolleyMotion.ease(progress / windup));
        }
        if (progress < impact) {
            return draw.towards(strike, (progress - windup) / (impact - windup));
        }
        if (progress < followThrough) {
            return strike.towards(follow, VolleyMotion.ease((progress - impact) / (followThrough - impact)));
        }
        return follow.towards(HELD, VolleyMotion.ease((progress - followThrough) / (1.0F - followThrough)));
    }
}
