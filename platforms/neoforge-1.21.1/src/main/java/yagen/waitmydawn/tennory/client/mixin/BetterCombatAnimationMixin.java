package yagen.waitmydawn.tennory.client.mixin;

import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
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
    private static final ResourceLocation[] tennory$FAN_CLIPS = Arrays.stream(QuassusComboStep.values())
            .map(QuassusComboStep::clip).toArray(ResourceLocation[]::new);
    @Unique
    private static final ResourceLocation[] tennory$GLAIVE_CLIPS = Arrays.stream(GlaiveComboStep.values())
            .map(GlaiveComboStep::clip).toArray(ResourceLocation[]::new);
    @Unique
    private static final ResourceLocation[] tennory$REDEEMER_CLIPS = Arrays.stream(RedeemerComboStep.values())
            .map(RedeemerComboStep::clip).toArray(ResourceLocation[]::new);

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
        ResourceLocation[] clips;
        switch (player.getMainHandItem().getItem()) {
            case QuassusItem fan -> clips = tennory$FAN_CLIPS;
            case GlaiveItem glaive -> clips = tennory$GLAIVE_CLIPS;
            case RedeemerItem redeemer -> clips = tennory$REDEEMER_CLIPS;
            default -> { return; }
        }
        // Better Combat preserves the registered clip's UUID when making its playback copy.
        var uuid = getData().getUuid();
        for (var clip : clips) {
            if (PlayerAnimationRegistry.getAnimation(clip) instanceof KeyframeAnimation registered
                    && uuid.equals(registered.getUuid())) {
                callback.setReturnValue(FirstPersonMode.DISABLED);
                return;
            }
        }
    }
}
