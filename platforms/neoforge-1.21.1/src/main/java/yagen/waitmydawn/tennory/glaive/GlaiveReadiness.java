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
    private int slot;
    private boolean drawn;

    public static float deployment(Player player, float partialTick) {
        GlaiveReadiness state = player.getExistingDataOrNull(TennoryRegistries.GLAIVE_READINESS);
        if (state == null || !state.matches(player)) {
            return 0.0F;
        }
        return state.drawn ? 1.0F : Math.min(player.getAttackStrengthScale(partialTick), Math.nextDown(1.0F));
    }

    static boolean ready(Player player) {
        trackHand(player);
        return deployment(player, 0.0F) >= 1.0F;
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

    private static void trackHand(Player player) {
        Item item = player.getMainHandItem().getItem();
        if (!(item instanceof GlaiveWeaponItem)) {
            if (player.hasData(TennoryRegistries.GLAIVE_READINESS)) {
                player.removeData(TennoryRegistries.GLAIVE_READINESS);
            }
            return;
        }

        GlaiveReadiness state = player.getData(TennoryRegistries.GLAIVE_READINESS);
        if (!state.matches(player)) {
            state.equipped = item;
            state.slot = player.getInventory().selected;
            state.drawn = false;
            player.resetAttackStrengthTicker();
        }
        // The cooldown controls deployment once per equip. Subsequent swings must not retract the blades.
        if (!state.drawn && player.getAttackStrengthScale(0.0F) >= 1.0F) {
            state.drawn = true;
        }
    }

    private boolean matches(Player player) {
        return equipped == player.getMainHandItem().getItem() && slot == player.getInventory().selected;
    }
}
