package yagen.waitmydawn.tennory.client.mixin;

import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import java.util.Arrays;
import net.bettercombat.client.animation.AnimationRegistry;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yagen.waitmydawn.tennory.client.GlaiveComboStep;
import yagen.waitmydawn.tennory.client.QuassusComboStep;
import yagen.waitmydawn.tennory.client.RedeemerComboStep;
import yagen.waitmydawn.tennory.glaive.GlaiveItem;
import yagen.waitmydawn.tennory.weapon.QuassusItem;
import yagen.waitmydawn.tennory.weapon.RedeemerItem;

@Pseudo
@Mixin(targets = "net.bettercombat.client.animation.CustomAnimationPlayer", remap = false)
abstract class BetterCombatAnimationMixin extends KeyframeAnimationPlayer {
    @Unique
    private static final String[] tennory$FAN_CLIPS = Arrays.stream(QuassusComboStep.values())
            .map(step -> step.clip().toString()).toArray(String[]::new);
    @Unique
    private static final String[] tennory$GLAIVE_CLIPS = Arrays.stream(GlaiveComboStep.values())
            .map(step -> step.clip().toString()).toArray(String[]::new);
    @Unique
    private static final String[] tennory$REDEEMER_CLIPS = Arrays.stream(RedeemerComboStep.values())
            .map(step -> step.clip().toString()).toArray(String[]::new);

    BetterCombatAnimationMixin(KeyframeAnimation animation) {
        super(animation);
    }

    @Inject(method = "getFirstPersonMode", at = @At("HEAD"), cancellable = true, require = 0)
    private void tennory$useWeaponView(float partialTick, CallbackInfoReturnable<FirstPersonMode> callback) {
        var client = Minecraft.getInstance();
        var player = client.player;
        if (player == null || client.getCameraEntity() != player) {
            return;
        }
        String[] clips;
        var weapon = player.getMainHandItem().getItem();
        if (weapon instanceof QuassusItem) clips = tennory$FAN_CLIPS;
        else if (weapon instanceof GlaiveItem) clips = tennory$GLAIVE_CLIPS;
        else if (weapon instanceof RedeemerItem) clips = tennory$REDEEMER_CLIPS;
        else return;
        // Better Combat preserves the registered clip's UUID when making its playback copy.
        var uuid = getData().getUuid();
        for (var clip : clips) {
            var registered = AnimationRegistry.animations.get(clip);
            if (registered != null && uuid.equals(registered.getUuid())) {
                callback.setReturnValue(FirstPersonMode.DISABLED);
                return;
            }
        }
    }
}
