package yagen.waitmydawn.tennory.client.mixin;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yagen.waitmydawn.tennory.client.FanGuardRig;
import yagen.waitmydawn.tennory.client.VolleyAnimations;

@Mixin(PlayerModel.class)
abstract class VolleyPlayerModelMixin implements FanGuardRig.Access {
    @Shadow
    @Final
    private boolean slim;

    @Unique
    private VolleyAnimations.BaseOffsets tennory$baseOffsets;

    @Unique
    private FanGuardRig tennory$guard;

    @Override
    public FanGuardRig tennory$fanGuardRig() {
        return tennory$guard;
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
    private void tennory$restorePose(LivingEntity entity, float walk, float speed, float age, float yaw, float pitch,
                                    CallbackInfo callback) {
        tennory$finishVolleyPose();
    }

    @Override
    public void tennory$finishVolleyPose() {
        if (tennory$baseOffsets != null) {
            tennory$baseOffsets.restore((PlayerModel<?>) (Object) this);
            tennory$baseOffsets = null;
        }
        if (tennory$guard != null) {
            tennory$guard.clear();
        }
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("RETURN"))
    private void tennory$poseWeaponAction(LivingEntity entity, float walk, float speed, float age, float yaw, float pitch,
                                          CallbackInfo callback) {
        // PlayerRenderer's isolated first-person arm pass uses the zero-time neutral model.
        if (age != 0.0F && entity instanceof AbstractClientPlayer player) {
            boolean guard = VolleyAnimations.usesFanGuard(player);
            if (guard && tennory$guard == null) {
                tennory$guard = new FanGuardRig(slim);
            }
            tennory$baseOffsets = VolleyAnimations.poseModel((PlayerModel<?>) (Object) this, player, speed,
                    guard ? tennory$guard : null);
        }
    }

    @Inject(method = "bodyParts", at = @At("RETURN"), cancellable = true)
    private void tennory$guardParts(CallbackInfoReturnable<Iterable<ModelPart>> callback) {
        if (tennory$guard != null) {
            callback.setReturnValue(tennory$guard.bodyParts((PlayerModel<?>) (Object) this, callback.getReturnValue()));
        }
    }
}
