package yagen.waitmydawn.tennory.client;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Quaternionf;

record MeleeViewPose(float x, float y, float z, float yaw, float pitch, float roll) {
    MeleeViewPose towards(MeleeViewPose next, float t) {
        return new MeleeViewPose(Mth.lerp(t, x, next.x), Mth.lerp(t, y, next.y), Mth.lerp(t, z, next.z),
                Mth.lerp(t, yaw, next.yaw), Mth.lerp(t, pitch, next.pitch), Mth.lerp(t, roll, next.roll));
    }

    Quaternionf rotation(HumanoidArm arm) {
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        return new Quaternionf().rotationYXZ(side * yaw * Mth.DEG_TO_RAD, pitch * Mth.DEG_TO_RAD, side * roll * Mth.DEG_TO_RAD);
    }
}
