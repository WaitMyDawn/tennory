package yagen.waitmydawn.tennory.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import yagen.waitmydawn.tennory.combat.VolleyProfile;
import yagen.waitmydawn.tennory.weapon.VolleyProjectile;

final class VolleyRenderer extends EntityRenderer<VolleyProjectile> {
    VolleyRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(VolleyProjectile volley, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        double depth = volley.renderedDepth(partialTick);
        if (depth < 0.45) {
            return;
        }
        VolleyProjectile.Rays rays = volley.rays();
        boolean blades = volley.profile() == VolleyProfile.QUASSUS;
        VertexConsumer vertices = buffers.getBuffer(blades ? RenderType.debugQuads() : RenderType.lightning());
        for (int index = 0; index < rays.pattern().size(); index++) {
            if (volley.stopped(index)) {
                continue;
            }
            Vec3 direction = rays.direction(index);
            Vec3 tip = direction.scale(depth);
            if (blades) {
                double roll = depth * 0.84 + index * 0.32;
                Vec3 edge = rays.right().scale(Math.cos(roll) * 0.105).add(rays.up().scale(Math.sin(roll) * 0.105));
                Vec3 mid = tip.subtract(direction.scale(0.21));
                Vec3 tail = tip.subtract(direction.scale(0.48));
                quad(vertices, pose.last(), tip, mid.add(edge), tail, mid.subtract(edge), 0xFFE5FBFF);
                quad(vertices, pose.last(), tip, mid, tail, mid.subtract(edge), 0xFF70B6CA);
                quad(vertices, pose.last(), tip, mid.subtract(edge), tail, mid.add(edge), 0xFFADDEE9);
            } else {
                Vec3 edge = rays.right().scale(0.026);
                Vec3 tail = direction.scale(Math.max(0.18, depth - 1.05));
                quad(vertices, pose.last(), tip, tip.subtract(edge), tail, tip.add(edge), 0xEFFFCA6D);
                quad(vertices, pose.last(), tip, tip.add(edge), tail, tip.subtract(edge), 0xEFFFCA6D);
                Vec3 vertical = rays.up().scale(0.026);
                quad(vertices, pose.last(), tip, tip.subtract(vertical), tail, tip.add(vertical), 0xF9FFDF86);
                quad(vertices, pose.last(), tip, tip.add(vertical), tail, tip.subtract(vertical), 0xF9FFDF86);
            }
        }
    }

    private static void quad(VertexConsumer vertices, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
        vertex(vertices, pose, a, color);
        vertex(vertices, pose, b, color);
        vertex(vertices, pose, c, color);
        vertex(vertices, pose, d, color);
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose, Vec3 point, int color) {
        vertices.addVertex(pose, (float) point.x, (float) point.y, (float) point.z).setColor(color);
    }

    @Override
    public ResourceLocation getTextureLocation(VolleyProjectile entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
