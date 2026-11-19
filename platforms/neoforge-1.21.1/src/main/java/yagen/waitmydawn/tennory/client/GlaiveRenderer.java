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
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Quaternionf;
import yagen.waitmydawn.tennory.glaive.GlaiveProjectile;

final class GlaiveRenderer extends EntityRenderer<GlaiveProjectile> {
    private final ItemRenderer itemRenderer;
    private final Quaternionf flightPose = new Quaternionf();

    GlaiveRenderer(EntityRendererProvider.Context context) {
        super(context);
        itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(GlaiveProjectile projectile, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.mulPose(projectile.flightPose(partialTick, flightPose));
        poseStack.mulPose(Axis.ZP.rotationDegrees((projectile.tickCount + partialTick) * 40.0F));
        itemRenderer.renderStatic(projectile.getDisplayStack(), ItemDisplayContext.NONE, packedLight, OverlayTexture.NO_OVERLAY,
                poseStack, buffer, projectile.level(), projectile.getId());
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(GlaiveProjectile projectile) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
