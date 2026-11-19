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
import yagen.waitmydawn.tennory.client.GlaiveAnimations;
import yagen.waitmydawn.tennory.client.VolleyAnimations;
import yagen.waitmydawn.tennory.client.WeaponArmRig;
import yagen.waitmydawn.tennory.client.WeaponPlayerPose;

@Mixin(PlayerModel.class)
abstract class VolleyPlayerModelMixin implements WeaponArmRig.Access {
    @Shadow
    @Final
    private boolean slim;

    @Unique
    private WeaponPlayerPose.BaseOffsets tennory$baseOffsets;

    @Unique
    private WeaponArmRig tennory$armRig;

    @Override
    public WeaponArmRig tennory$weaponArmRig() {
        return tennory$armRig;
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
    private void tennory$restorePose(LivingEntity entity, float walk, float speed, float age, float yaw, float pitch,
                                    CallbackInfo callback) {
        tennory$finishWeaponPose();
    }

    @Override
    public void tennory$finishWeaponPose() {
        if (tennory$baseOffsets != null) {
            tennory$baseOffsets.restore((PlayerModel<?>) (Object) this);
            tennory$baseOffsets = null;
        }
        if (tennory$armRig != null) {
            tennory$armRig.clear();
        }
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("RETURN"))
    private void tennory$poseWeaponAction(LivingEntity entity, float walk, float speed, float age, float yaw, float pitch,
                                          CallbackInfo callback) {
        // PlayerRenderer's isolated first-person arm pass uses the zero-time neutral model.
        if (age != 0.0F && entity instanceof AbstractClientPlayer player) {
            var model = (PlayerModel<?>) (Object) this;
            boolean segmented = VolleyAnimations.usesFanGuard(player) || GlaiveAnimations.usesArmRig(player);
            if (segmented && tennory$armRig == null) {
                tennory$armRig = new WeaponArmRig(slim);
            }
            WeaponArmRig rig = segmented ? tennory$armRig : null;
            tennory$baseOffsets = GlaiveAnimations.poseModel(model, player, speed, rig);
            if (tennory$baseOffsets == null) {
                tennory$baseOffsets = VolleyAnimations.poseModel(model, player, speed, rig);
            }
        }
    }

    @Inject(method = "bodyParts", at = @At("RETURN"), cancellable = true)
    private void tennory$guardParts(CallbackInfoReturnable<Iterable<ModelPart>> callback) {
        if (tennory$armRig != null) {
            callback.setReturnValue(tennory$armRig.bodyParts((PlayerModel<?>) (Object) this, callback.getReturnValue()));
        }
    }
}
