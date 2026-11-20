package yagen.waitmydawn.tennory.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import yagen.waitmydawn.tennory.Tennory;
import yagen.waitmydawn.tennory.combat.VolleyProfile;
import yagen.waitmydawn.tennory.weapon.VolleyProjectile;

final class VolleyRenderer extends EntityRenderer<VolleyProjectile> {
    private static final ResourceLocation EMBER_TEXTURE =
            new ResourceLocation(Tennory.MODID, "textures/particle/redeemer_ember.png");
    private static final ResourceLocation BLADE_TEXTURE =
            new ResourceLocation(Tennory.MODID, "textures/particle/quassus_blade.png");
    private static final RenderType EMBERS = RenderType.entityTranslucentEmissive(EMBER_TEXTURE, false);
    private static final RenderType BLADES = RenderType.entityTranslucent(BLADE_TEXTURE, false);

    VolleyRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public Vec3 getRenderOffset(VolleyProjectile volley, float partialTick) {
        VolleyProjectile.Rays rays = volley.rays();
        if (rays == null) {
            return Vec3.ZERO;
        }
        return rays.origin().subtract(Mth.lerp(partialTick, volley.xOld, volley.getX()),
                Mth.lerp(partialTick, volley.yOld, volley.getY()), Mth.lerp(partialTick, volley.zOld, volley.getZ()));
    }

    @Override
    public void render(VolleyProjectile volley, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        double depth = volley.renderedDepth(partialTick);
        double range = volley.profile().range();
        if (depth < 0.6 || depth >= range) {
            return;
        }
        VolleyProjectile.Rays rays = volley.rays();
        if (rays == null) {
            return;
        }
        boolean blades = isFan(volley);
        VertexConsumer vertices = buffers.getBuffer(blades ? BLADES : EMBERS);
        PoseStack.Pose transform = pose.last();
        Vec3 camera = entityRenderDispatcher.camera.getPosition().subtract(rays.origin());
        float rangeVisibility = fadeIn(range - depth, 0.0, 5.0);
        volley.traceVisualImpacts(depth);
        for (int index = 0; index < rays.directions().size(); index++) {
            if (volley.stopped(index, depth)) {
                continue;
            }
            Vec3 direction = rays.directions().get(index);
            if (blades) {
                renderBlade(vertices, transform, rays.up(), direction, camera, depth, rangeVisibility, index, light);
            } else {
                renderEmber(vertices, transform, rays.up(), direction, camera, depth, rangeVisibility, index);
            }
        }
    }

    private static void renderEmber(VertexConsumer vertices, PoseStack.Pose pose, Vec3 up,
                                    Vec3 direction, Vec3 camera, double depth, float rangeVisibility, int index) {
        Vec3 tip = direction.scale(depth);
        Vec3 toCamera = camera.subtract(tip);
        float visibility = fadeIn(depth, 0.75, 2.6) * fadeIn(toCamera.length(), 0.9, 2.75) * rangeVisibility;
        if (visibility <= 0.015F) {
            return;
        }

        double phase = depth * 0.26 + index * 2.40;
        float pulse = (float) (0.5 + 0.5 * Math.sin(phase));
        Vec3 side = direction.cross(toCamera).normalize();
        if (side.lengthSqr() == 0.0) {
            side = direction.cross(up).normalize();
        }
        Vec3 tail = direction.scale(Math.max(0.6, depth - 0.48 - 0.26 * pulse))
                .add(up.scale(0.03 * Math.cos(phase)));
        Vec3 edge = side.scale(0.029 + 0.007 * pulse);
        int tipColor = fadeColor(FastColor.ARGB32.lerp(pulse, 0xC1AC6035, 0xDEEEA35A), visibility);
        int tailColor = fadeColor(FastColor.ARGB32.lerp(pulse, 0x4E653F32, 0x9CA9552F), visibility);
        particle(vertices, pose, tail, tip, edge, side.cross(direction).normalize(),
                tailColor, tipColor, LightTexture.FULL_BRIGHT);

        // A compact camera-facing head remains visible when the streak is seen end-on.
        Vec3 facing = toCamera.normalize();
        Vec3 headHeight = facing.cross(side).scale(0.043 + 0.008 * pulse);
        particle(vertices, pose, tip.subtract(headHeight), tip.add(headHeight), edge, facing,
                tailColor, tipColor, LightTexture.FULL_BRIGHT);
    }

    private static void renderBlade(VertexConsumer vertices, PoseStack.Pose pose, Vec3 aimUp,
                                    Vec3 direction, Vec3 camera, double depth, float rangeVisibility, int index, int light) {
        Vec3 tip = direction.scale(depth);
        Vec3 toCamera = camera.subtract(tip);
        float visibility = fadeIn(toCamera.length(), 0.55, 1.5) * rangeVisibility;
        if (visibility <= 0.015F) {
            return;
        }

        // Preserve spread on the plane perpendicular to the aim, then roll around each blade's own flight axis.
        Vec3 forward = direction.normalize();
        Vec3 across = forward.cross(aimUp).normalize();
        Vec3 up = across.cross(forward);
        double roll = 0.35 + index * 0.51 + depth * 0.56;
        Vec3 side = across.scale(Math.cos(roll)).add(up.scale(Math.sin(roll)));
        Vec3 normal = side.cross(forward);
        if (normal.dot(toCamera) < 0.0) {
            normal = normal.reverse();
        }
        Vec3 tail = tip.subtract(forward.scale(0.47));
        particle(vertices, pose, tail, tip, side.scale(0.113), normal,
                fadeColor(0xE9EEE0D3, visibility), fadeColor(0xFFFFFFFF, visibility), light);
    }

    static void renderMuzzleFlash(PoseStack.Pose pose, MultiBufferSource buffers, float muzzle, float amount) {
        VertexConsumer vertices = buffers.getBuffer(EMBERS);
        Vec3 tail = new Vec3(0.0, muzzle - 0.02, 0.0);
        Vec3 tip = new Vec3(0.0, muzzle + 0.20 * amount, 0.0);
        double radius = 0.075 * amount;
        int tailColor = fadeColor(0x9AAC6035, amount);
        int tipColor = fadeColor(0xC8EDA255, amount);
        particle(vertices, pose, tail, tip, new Vec3(radius, 0.0, 0.0), new Vec3(0.0, 0.0, 1.0),
                tailColor, tipColor, LightTexture.FULL_BRIGHT);
        particle(vertices, pose, tail, tip, new Vec3(0.0, 0.0, radius), new Vec3(1.0, 0.0, 0.0),
                tailColor, tipColor, LightTexture.FULL_BRIGHT);
    }

    private static float fadeIn(double distance, double start, double end) {
        return (float) net.minecraft.util.Mth.clamp((distance - start) / (end - start), 0.0, 1.0);
    }

    private static int fadeColor(int color, float opacity) {
        int alpha = Math.round(FastColor.ARGB32.alpha(color) * opacity);
        return (alpha << 24) | (color & 0xFFFFFF);
    }

    private static void particle(VertexConsumer vertices, PoseStack.Pose pose, Vec3 tail, Vec3 tip,
                                 Vec3 edge, Vec3 normal, int tailColor, int tipColor, int light) {
        vertex(vertices, pose, tip.subtract(edge), 1.0F, 0.0F, tipColor, normal, light);
        vertex(vertices, pose, tip.add(edge), 1.0F, 1.0F, tipColor, normal, light);
        vertex(vertices, pose, tail.add(edge), 0.0F, 1.0F, tailColor, normal, light);
        vertex(vertices, pose, tail.subtract(edge), 0.0F, 0.0F, tailColor, normal, light);
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose, Vec3 point,
                               float u, float v, int color, Vec3 normal, int light) {
        vertices.vertex(pose.pose(), (float) point.x, (float) point.y, (float) point.z)
                .color(color).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(pose.normal(), (float) normal.x, (float) normal.y, (float) normal.z).endVertex();
    }

    private static boolean isFan(VolleyProjectile volley) {
        return volley.profile() == VolleyProfile.QUASSUS;
    }

    @Override
    public ResourceLocation getTextureLocation(VolleyProjectile entity) {
        return isFan(entity) ? BLADE_TEXTURE : EMBER_TEXTURE;
    }
}
