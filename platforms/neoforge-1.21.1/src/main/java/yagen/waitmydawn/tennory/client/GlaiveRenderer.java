package yagen.waitmydawn.tennory.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import yagen.waitmydawn.tennory.glaive.GlaiveProjectile;

final class GlaiveRenderer extends EntityRenderer<GlaiveProjectile> {
    private static final double DISC_RADIUS = 0.43 * GlaiveModels.WORLD_SCALE;
    private final ItemRenderer itemRenderer;
    private final Quaternionf flightPose = new Quaternionf();

    GlaiveRenderer(EntityRendererProvider.Context context) {
        super(context);
        itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(GlaiveProjectile projectile, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        if (overlapsThrowerAtLaunch(projectile, partialTick)) {
            return;
        }
        poseStack.pushPose();
        poseStack.mulPose(projectile.flightPose(partialTick, flightPose));
        poseStack.mulPose(Axis.ZP.rotationDegrees((projectile.tickCount + partialTick) * 40.0F));
        GlaiveModels.scaleInWorld(poseStack);
        itemRenderer.renderStatic(projectile.getDisplayStack(), ItemDisplayContext.NONE, packedLight, OverlayTexture.NO_OVERLAY,
                poseStack, buffer, projectile.level(), projectile.getId());
        poseStack.popPose();
    }

    private static boolean overlapsThrowerAtLaunch(GlaiveProjectile projectile, float partialTick) {
        Entity owner = projectile.getOwner();
        if (projectile.tickCount > 3 || owner == null) {
            return false;
        }
        // The collision ray starts at eye height. Keep the disc hidden while its interpolated mesh still overlaps the thrower.
        Vec3 ownerOffset = renderPosition(owner, partialTick).subtract(owner.position());
        return owner.getBoundingBox().move(ownerOffset).inflate(DISC_RADIUS).contains(renderPosition(projectile, partialTick));
    }

    private static Vec3 renderPosition(Entity entity, float partialTick) {
        return new Vec3(Mth.lerp(partialTick, entity.xOld, entity.getX()),
                Mth.lerp(partialTick, entity.yOld, entity.getY()), Mth.lerp(partialTick, entity.zOld, entity.getZ()));
    }

    @Override
    public ResourceLocation getTextureLocation(GlaiveProjectile projectile) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
