package yagen.waitmydawn.tennory.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import yagen.waitmydawn.tennory.Tennory;
import yagen.waitmydawn.tennory.glaive.GlaiveWeaponItem;
import yagen.waitmydawn.tennory.weapon.VolleyWeaponItem;

@Mod.EventBusSubscriber(modid = Tennory.MODID, value = Dist.CLIENT)
public final class WeaponTooltips {
    private WeaponTooltips() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        var item = event.getItemStack().getItem();
        if (!(item instanceof GlaiveWeaponItem || item instanceof VolleyWeaponItem) || event.getEntity() == null) {
            return;
        }
        var tooltip = event.getToolTip();
        if (!Screen.hasShiftDown()) {
            tooltip.add(Component.translatable("tooltip.tennory.glaive.controls_hint").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        if (item instanceof VolleyWeaponItem) {
            tooltip.add(Component.translatable("tooltip.tennory.volley.fire",
                    Component.keybind("key.tennory.weapon_special")).withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.tennory.glaive.mode",
                GlaiveWeaponItem.modeName(GlaiveWeaponItem.detonatesOnImpact(event.getItemStack())))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.tennory.glaive.throw", Component.keybind("key.use"))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.tennory.glaive.detonate", Component.keybind("key.tennory.weapon_special"))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.tennory.glaive.mode_switch", Component.keybind("key.sneak"),
                Component.keybind("key.use")).withStyle(ChatFormatting.GRAY));
    }
}
