package yagen.waitmydawn.tennory.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import yagen.waitmydawn.tennory.Tennory;
import yagen.waitmydawn.tennory.glaive.GlaiveReadiness;
import yagen.waitmydawn.tennory.glaive.GlaiveWeaponItem;
import yagen.waitmydawn.tennory.network.WeaponPayloads;
import yagen.waitmydawn.tennory.registries.TennoryRegistries;

@EventBusSubscriber(modid = Tennory.MODID, value = Dist.CLIENT)
public final class WeaponInput {
    private static boolean modeUseHeld;

    private WeaponInput() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!Minecraft.getInstance().options.keyUse.isDown()) {
            modeUseHeld = false;
        }
        boolean pressed = false;
        while (TennoryClientSetup.WEAPON_SPECIAL.consumeClick()) {
            pressed = true;
        }
        if (pressed && canUseSpecial()) {
            PacketDistributor.sendToServer(WeaponPayloads.SpecialAction.INSTANCE);
        }
    }

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (event.isUseItem() && player != null && player.getMainHandItem().getItem() instanceof GlaiveWeaponItem) {
            if (!modeUseHeld && !player.isSpectator() && event.getHand() == InteractionHand.MAIN_HAND && player.isShiftKeyDown()) {
                modeUseHeld = true;
                client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            }
            if (modeUseHeld) {
                event.setSwingHand(false);
                event.setCanceled(true);
                return;
            }
        }
        if (event.isAttack() && player != null && player.getMainHandItem().getItem() instanceof GlaiveWeaponItem
                && GlaiveReadiness.deployment(player, 0.0F) < 1.0F) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
        if (event.isPickBlock() && canUseSpecial()
                && TennoryClientSetup.WEAPON_SPECIAL.isActiveAndMatches(event.getKeyMapping().getKey())) {
            event.setCanceled(true);
        }
    }

    private static boolean canUseSpecial() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        return client.screen == null && player != null && player.isAlive() && !player.isSpectator()
                && player.getExistingData(TennoryRegistries.CLIENT_GLAIVE_IN_FLIGHT).orElse(false);
    }
}
