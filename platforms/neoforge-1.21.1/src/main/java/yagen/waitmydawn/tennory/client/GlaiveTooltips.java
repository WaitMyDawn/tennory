package yagen.waitmydawn.tennory.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import yagen.waitmydawn.tennory.Tennory;
import yagen.waitmydawn.tennory.glaive.GlaiveWeaponItem;

@EventBusSubscriber(modid = Tennory.MODID, value = Dist.CLIENT)
public final class GlaiveTooltips {
    private GlaiveTooltips() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!(event.getItemStack().getItem() instanceof GlaiveWeaponItem) || event.getEntity() == null) {
            return;
        }
        var tooltip = event.getToolTip();
        if (!Screen.hasShiftDown()) {
            tooltip.add(Component.translatable("tooltip.tennory.glaive.controls_hint").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.tennory.glaive.throw", Component.keybind("key.use"))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.tennory.glaive.detonate", Component.keybind("key.tennory.weapon_special"))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.tennory.glaive.mode_switch", Component.keybind("key.sneak"),
                Component.keybind("key.use")).withStyle(ChatFormatting.GRAY));
    }
}
