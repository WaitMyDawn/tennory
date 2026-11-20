package yagen.waitmydawn.tennory.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.tennory.client.GlaiveAnimations;
import yagen.waitmydawn.tennory.client.VolleyAnimations;

@Mixin(ItemInHandLayer.class)
abstract class VolleyItemInHandLayerMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void tennory$renderWeaponGrip(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm,
                                          PoseStack poses, MultiBufferSource buffers, int light, CallbackInfo callback) {
        var layer = (ItemInHandLayer<?, ?>) (Object) this;
        if (entity instanceof AbstractClientPlayer player && layer.getParentModel() instanceof PlayerModel<?> model
                && (GlaiveAnimations.renderHeld(model, player, stack, arm, poses, buffers, light)
                || VolleyAnimations.renderHeld(model, player, stack, arm, poses, buffers, light))) {
            callback.cancel();
        }
    }
}
