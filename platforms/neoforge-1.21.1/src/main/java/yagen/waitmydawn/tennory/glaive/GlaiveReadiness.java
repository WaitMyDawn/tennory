package yagen.waitmydawn.tennory.glaive;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import yagen.waitmydawn.tennory.Tennory;
import yagen.waitmydawn.tennory.registries.TennoryRegistries;

@EventBusSubscriber(modid = Tennory.MODID)
public final class GlaiveReadiness {
    private Item equipped;
    private int slot = -1;
    private boolean drawn = true;
    private boolean animateDeployment;

    public static float deployment(Player player, float partialTick) {
        GlaiveReadiness state = player.getExistingDataOrNull(TennoryRegistries.GLAIVE_READINESS);
        Item held = player.getMainHandItem().getItem();
        if (state == null || state.slot < 0 || !(held instanceof GlaiveWeaponItem)) {
            return 1.0F;
        }
        if (state.slot != player.getInventory().selected) {
            return 0.0F;
        }
        return state.animateDeployment && !state.drawn && state.equipped == held
                ? Math.min(player.getAttackStrengthScale(partialTick), Math.nextDown(1.0F)) : 1.0F;
    }

    public static boolean ready(Player player) {
        return trackHand(player).drawn;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        trackHand(event.getEntity());
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.getMainHandItem().getItem() instanceof GlaiveWeaponItem && !ready(player)) {
            event.setCanceled(true);
        }
    }

    private static GlaiveReadiness trackHand(Player player) {
        Item held = player.getMainHandItem().getItem();
        Item glaive = held instanceof GlaiveWeaponItem ? held : null;
        int selected = player.getInventory().selected;
        GlaiveReadiness state = player.getData(TennoryRegistries.GLAIVE_READINESS);
        if (state.slot != selected || state.equipped != glaive) {
            // Keep the last slot through an empty hand so an item delivered into that slot cannot trigger deployment.
            state.animateDeployment = glaive != null && state.slot >= 0 && state.slot != selected;
            state.slot = selected;
            state.equipped = glaive;
            state.drawn = glaive == null;
            if (!state.drawn) {
                player.resetAttackStrengthTicker();
            }
        }
        // Latch readiness through later swings and charge actions on the same weapon.
        if (!state.drawn && player.getAttackStrengthScale(0.0F) >= 1.0F) {
            state.drawn = true;
        }
        return state;
    }
}
