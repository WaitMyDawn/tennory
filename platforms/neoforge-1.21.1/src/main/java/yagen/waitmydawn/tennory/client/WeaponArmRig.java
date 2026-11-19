package yagen.waitmydawn.tennory.client;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class WeaponArmRig {
    private static final float ELBOW = 4.0F;
    private static final Set<Direction> SIDES = EnumSet.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST);
    private static final Set<Direction> UPPER_FACES = EnumSet.of(Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST);
    private static final Set<Direction> LOWER_CAP = EnumSet.of(Direction.UP);
    private final Arm left;
    private final Arm right;

    public WeaponArmRig(boolean slim) {
        left = new Arm(false, slim);
        right = new Arm(true, slim);
    }

    void prepare(PlayerModel<?> model, HumanoidArm hand, ArmPose pose, float vanillaBlend, Quaternionf vanillaGrip) {
        boolean leftHand = hand == HumanoidArm.LEFT;
        arm(hand).prepare(leftHand ? model.leftArm : model.rightArm, leftHand ? model.leftSleeve : model.rightSleeve,
                pose, vanillaBlend, vanillaGrip);
    }

    Grip grip(HumanoidArm hand) {
        return arm(hand).grip;
    }

    private Arm arm(HumanoidArm hand) {
        return hand == HumanoidArm.LEFT ? left : right;
    }

    public void clear() {
        left.grip = null;
        right.grip = null;
    }

    public Iterable<ModelPart> bodyParts(PlayerModel<?> player, Iterable<ModelPart> parts) {
        if (left.grip == null && right.grip == null) {
            return parts;
        }
        List<ModelPart> posed = new ArrayList<>();
        for (ModelPart part : parts) {
            if (left.grip != null && part == player.leftArm) {
                posed.add(left.skin.root());
            } else if (left.grip != null && part == player.leftSleeve) {
                posed.add(left.sleeve.root());
            } else if (right.grip != null && part == player.rightArm) {
                posed.add(right.skin.root());
            } else if (right.grip != null && part == player.rightSleeve) {
                posed.add(right.sleeve.root());
            } else {
                posed.add(part);
            }
        }
        return posed;
    }

    private static Quaternionf alongArm(float x, float y, float z) {
        Vector3f direction = new Vector3f(x, y, z).normalize();
        return new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, direction.x, direction.y, direction.z);
    }

    private static Quaternionf face(Vector3f spread, Vector3f towardPlayer) {
        Vector3f z = towardPlayer.normalize();
        Vector3f x = spread.cross(z).normalize();
        Vector3f y = new Vector3f(z).cross(x);
        return new Quaternionf().setFromNormalized(new Matrix3f().setColumn(0, x).setColumn(1, y).setColumn(2, z));
    }

    static void setRotation(ModelPart part, Quaternionf rotation) {
        Vector3f euler = rotation.getEulerAnglesZYX(new Vector3f());
        part.setRotation(euler.x, euler.y, euler.z);
    }

    public interface Access {
        void tennory$finishWeaponPose();

        WeaponArmRig tennory$weaponArmRig();
    }

    record Grip(Vector3f position, Quaternionf rotation) {
    }

    record ArmPose(Quaternionf upper, Quaternionf forearm, Quaternionf grip) {
        static ArmPose facing(Vector3f upper, Vector3f forearm, Vector3f spread, Vector3f normal) {
            return new ArmPose(alongArm(upper.x, upper.y, upper.z), alongArm(forearm.x, forearm.y, forearm.z),
                    face(spread, normal));
        }

        static ArmPose hinged(Vector3f upper, Vector3f forearm, Vector3f spread, Vector3f normal) {
            Vector3f upperAxis = upper.normalize();
            Vector3f forearmAxis = forearm.normalize();
            Quaternionf shoulder = alongArm(upperAxis.x, upperAxis.y, upperAxis.z);
            // Transport the shoulder rotation through the elbow bend so the forearm inherits its twist.
            Quaternionf wrist = new Quaternionf().rotationTo(upperAxis, forearmAxis).mul(shoulder);
            return new ArmPose(shoulder, wrist, face(spread, normal));
        }

        ArmPose lerp(ArmPose target, float blend) {
            return new ArmPose(new Quaternionf(upper).slerp(target.upper, blend),
                    new Quaternionf(forearm).slerp(target.forearm, blend),
                    new Quaternionf(grip).slerp(target.grip, blend));
        }

        ArmPose rollGrip(float radians) {
            return new ArmPose(upper, forearm, new Quaternionf(grip).rotateZ(radians));
        }

        ArmPose mirror() {
            return new ArmPose(mirrorRotation(upper), mirrorRotation(forearm), mirrorRotation(grip));
        }

        private static Quaternionf mirrorRotation(Quaternionf rotation) {
            return new Quaternionf(rotation.x, -rotation.y, -rotation.z, rotation.w);
        }
    }

    private static final class Arm {
        private final Segments skin;
        private final Segments sleeve;
        private final float center;
        private Grip grip;

        private Arm(boolean right, boolean slim) {
            float width = slim ? 3.0F : 4.0F;
            float leftEdge = right ? 1.0F - width : -1.0F;
            center = leftEdge + width * 0.5F;
            skin = Segments.bake(right ? 40 : 32, right ? 16 : 48, leftEdge, width, 0.0F);
            sleeve = Segments.bake(right ? 40 : 48, right ? 32 : 48, leftEdge, width, 0.25F);
        }

        void prepare(ModelPart base, ModelPart baseSleeve, ArmPose pose, float vanillaBlend, Quaternionf vanillaGrip) {
            Quaternionf straight = new Quaternionf().rotationZYX(base.zRot, base.yRot, base.xRot);
            Quaternionf upper = new Quaternionf(pose.upper()).slerp(straight, vanillaBlend);
            Quaternionf fore = new Quaternionf(pose.forearm()).slerp(straight, vanillaBlend);
            Quaternionf elbow = new Quaternionf(upper).invert().mul(fore);
            skin.pose(base, upper, elbow);
            sleeve.pose(baseSleeve, upper, elbow);

            Vector3f hand = upper.transform(new Vector3f(0.0F, ELBOW, 0.0F));
            hand.add(fore.transform(new Vector3f(center, 6.0F, -1.45F)));
            hand.add(base.x, base.y, base.z).mul(1.0F / 16.0F);
            grip = new Grip(hand, new Quaternionf(pose.grip()).slerp(vanillaGrip, vanillaBlend));
        }
    }

    private record Segments(ModelPart root, ModelPart forearm) {
        static Segments bake(int u, int v, float x, float width, float grow) {
            // The side rows continue at the elbow. The hand cap uses the original arm's bottom UV.
            ModelPart.Cube upper = cube(u, v, x, -2.0F, width, 6.0F, grow, grow + 0.9F, UPPER_FACES);
            ModelPart.Cube lower = cube(u, v + 6, x, 0.0F, width, 6.0F, grow, grow + 0.9F, SIDES);
            ModelPart.Cube cap = cube(u, v, x, -6.0F, width, 12.0F, grow, grow, LOWER_CAP);
            ModelPart forearm = new ModelPart(List.of(lower, cap), Map.of());
            forearm.setPos(0.0F, ELBOW, 0.0F);
            return new Segments(new ModelPart(List.of(upper), Map.of("forearm", forearm)), forearm);
        }

        void pose(ModelPart original, Quaternionf upper, Quaternionf elbow) {
            root.copyFrom(original);
            root.visible = original.visible;
            root.skipDraw = original.skipDraw;
            forearm.skipDraw = original.skipDraw;
            setRotation(root, upper);
            setRotation(forearm, elbow);
        }

        private static ModelPart.Cube cube(int u, int v, float x, float y, float width, float length, float grow,
                                           float growY, Set<Direction> faces) {
            return new ModelPart.Cube(u, v, x, y, -2.0F, width, length, 4.0F,
                    grow, growY, grow, false, 64.0F, 64.0F, faces);
        }
    }
}
