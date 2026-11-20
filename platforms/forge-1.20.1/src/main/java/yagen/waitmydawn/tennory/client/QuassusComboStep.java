package yagen.waitmydawn.tennory.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import yagen.waitmydawn.tennory.Tennory;
import yagen.waitmydawn.tennory.client.QuassusBetterCombat.Frame;

public enum QuassusComboStep {
    RIGHT_CUT("quassus_right_cut",
            new Pair(new Hand(-0.17F, -0.02F, 0.10F, 0.12F, 0.70F), Hand.REST),
            new Pair(new Hand(-0.03F, 0.02F, 0.29F, 0.53F, 1.0F), Hand.REST),
            new Pair(new Hand(0.09F, -0.02F, 0.22F, 0.74F, 1.0F), Hand.REST)),
    LEFT_CUT("quassus_left_cut",
            new Pair(Hand.REST, new Hand(-0.18F, -0.02F, 0.10F, 0.12F, 0.70F)),
            new Pair(Hand.REST, new Hand(-0.06F, 0.01F, 0.29F, 0.53F, 1.0F)),
            new Pair(Hand.REST, new Hand(0.07F, -0.03F, 0.22F, 0.74F, 1.0F))),
    CROSS_CUT("quassus_cross_cut",
            new Pair(new Hand(-0.42F, 0.03F, 0.35F, -0.74F, 1.0F), new Hand(-0.53F, -0.06F, 0.29F, -0.74F, 1.0F)),
            new Pair(new Hand(-0.04F, 0.04F, 0.32F, 0.63F, 1.0F), new Hand(-0.07F, -0.02F, 0.30F, 0.63F, 1.0F)),
            new Pair(new Hand(0.09F, -0.02F, 0.26F, 0.78F, 1.0F), new Hand(0.06F, -0.03F, 0.24F, 0.78F, 1.0F)));

    private static final Pair REST = new Pair(Hand.REST, Hand.REST);
    private static final QuassusComboStep[] STEPS = values();
    private final ResourceLocation clip;
    private final String animationId;
    private final Pair draw;
    private final Pair strike;
    private final Pair follow;

    QuassusComboStep(String clip, Pair draw, Pair strike, Pair follow) {
        this.clip = new ResourceLocation(Tennory.MODID, clip);
        this.animationId = this.clip.toString();
        this.draw = draw;
        this.strike = strike;
        this.follow = follow;
    }

    static QuassusComboStep byAnimationId(String id) {
        for (var step : STEPS) {
            if (step.animationId.equals(id)) {
                return step;
            }
        }
        return null;
    }

    public ResourceLocation clip() {
        return clip;
    }

    Frame sample(float progress, float impact, boolean mirrored) {
        float drawEnd = impact * 0.58F;
        float followEnd = Mth.lerp(0.30F, impact, 1.0F);
        float holdEnd = Mth.lerp(0.50F, impact, 1.0F);
        Pair pose;
        if (progress < drawEnd) {
            pose = REST.towards(draw, progress / drawEnd);
        } else if (progress < impact) {
            pose = draw.towards(strike, (progress - drawEnd) / (impact - drawEnd));
        } else if (progress < followEnd) {
            pose = strike.towards(follow, (progress - impact) / (followEnd - impact));
        } else if (progress < holdEnd) {
            pose = follow;
        } else {
            pose = follow.towards(REST, (progress - holdEnd) / (1.0F - holdEnd));
        }
        return new Frame(pose.primary(), pose.secondary(), mirrored);
    }

    record Hand(float lateral, float lift, float reach, float outward, float turn) {
        static final Hand REST = new Hand(0, 0, 0, 0, 0);

        void aim(Quaternionf guard, float side) {
            if (turn == 0.0F) {
                return;
            }
            // The Quassus mesh's blade crown extends from the grip along local +Y.
            Quaternionf cut = new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, side * outward, 0.60F, -0.78F);
            guard.slerp(cut, turn);
        }

        Hand towards(Hand target, float progress) {
            if (equals(target)) {
                return this;
            }
            return new Hand(Mth.lerp(progress, lateral, target.lateral), Mth.lerp(progress, lift, target.lift),
                    Mth.lerp(progress, reach, target.reach), Mth.lerp(progress, outward, target.outward),
                    Mth.lerp(progress, turn, target.turn));
        }
    }

    private record Pair(Hand primary, Hand secondary) {
        Pair towards(Pair target, float progress) {
            float t = VolleyMotion.ease(progress);
            return new Pair(primary.towards(target.primary, t), secondary.towards(target.secondary, t));
        }
    }
}
