package yagen.waitmydawn.tennory.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import yagen.waitmydawn.tennory.Tennory;

public enum GlaiveComboStep {
    RISING_CUT("glaive_rising_cut",
            new MeleeViewPose(0.64F, -0.56F, -0.96F, -65, 18, -37),
            new MeleeViewPose(0.40F, -0.31F, -1.18F, -30, -14, 24),
            new MeleeViewPose(0.47F, -0.27F, -1.16F, -38, -10, 52), 0),
    BACK_SWEEP("glaive_back_sweep",
            new MeleeViewPose(0.43F, -0.34F, -1.03F, -50, 2, 20),
            new MeleeViewPose(0.52F, -0.43F, -1.20F, -27, -4, -38),
            new MeleeViewPose(0.71F, -0.46F, -1.14F, -43, 7, -61), 0),
    ORBIT_CUT("glaive_orbit_cut",
            new MeleeViewPose(0.50F, -0.36F, -1.04F, -55, 9, -20),
            new MeleeViewPose(0.20F, -0.15F, -1.82F, -27, 25, 78),
            new MeleeViewPose(0.30F, 0.04F, -1.78F, -39, 38, 231), 360);

    private static final MeleeViewPose HELD = held(0);
    private static final GlaiveComboStep[] STEPS = values();
    private final ResourceLocation clip;
    private final String animation;
    private final MeleeViewPose draw;
    private final MeleeViewPose strike;
    private final MeleeViewPose follow;
    private final MeleeViewPose catchPose;

    GlaiveComboStep(String clip, MeleeViewPose draw, MeleeViewPose strike, MeleeViewPose follow, int rotation) {
        this.clip = ResourceLocation.fromNamespaceAndPath(Tennory.MODID, clip);
        animation = this.clip.toString();
        this.draw = draw;
        this.strike = strike;
        this.follow = follow;
        catchPose = held(rotation);
    }

    private static MeleeViewPose held(int rotation) {
        return new MeleeViewPose(0.55F, -0.48F, -0.95F, -62, 9, -22 + rotation);
    }

    public ResourceLocation clip() {
        return clip;
    }

    static GlaiveComboStep byAnimation(String animation) {
        for (var step : STEPS) {
            if (step.animation.equals(animation)) {
                return step;
            }
        }
        return null;
    }

    MeleeViewPose sample(float progress, float impact) {
        float windup = impact * 0.48F;
        float followThrough = Mth.lerp(0.46F, impact, 1.0F);
        if (progress < windup) {
            return HELD.towards(draw, VolleyMotion.ease(progress / windup));
        }
        if (progress < impact) {
            return draw.towards(strike, (progress - windup) / (impact - windup));
        }
        if (progress < followThrough) {
            return strike.towards(follow, (progress - impact) / (followThrough - impact));
        }
        return follow.towards(catchPose, VolleyMotion.ease((progress - followThrough) / (1.0F - followThrough)));
    }

}
