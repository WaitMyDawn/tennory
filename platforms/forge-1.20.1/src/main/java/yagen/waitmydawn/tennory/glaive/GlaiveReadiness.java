package yagen.waitmydawn.tennory.glaive;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.TickEvent;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import yagen.waitmydawn.tennory.Tennory;

@Mod.EventBusSubscriber(modid = Tennory.MODID)
public final class GlaiveReadiness {
    private static final Map<Player, GlaiveReadiness> STATES = Collections.synchronizedMap(new WeakHashMap<>());
    private Item equipped;
    private int slot = -1;
    private boolean drawn = true;
    private boolean animateDeployment;

    public static float deployment(Player player, float partialTick) {
        GlaiveReadiness state = STATES.get(player);
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
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) trackHand(event.player);
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
        GlaiveReadiness state = STATES.computeIfAbsent(player, ignored -> new GlaiveReadiness());
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
