package yagen.waitmydawn.tennory.glaive;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import yagen.waitmydawn.tennory.combat.WeaponStats;
import yagen.waitmydawn.tennory.registries.TennoryRegistries;
import yagen.waitmydawn.tennory.weapon.MeleeWeaponItem;

public abstract class GlaiveWeaponItem extends MeleeWeaponItem {
    private static final int MAX_USE_TICKS = 72000;

    private final int chargeTicks;

    protected GlaiveWeaponItem(Properties properties, WeaponStats stats, int chargeTicks) {
        super(properties, stats);
        this.chargeTicks = chargeTicks;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) {
            return InteractionResultHolder.pass(stack);
        }
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                boolean enabled = !detonatesOnImpact(stack);
                if (enabled) {
                    stack.set(TennoryRegistries.GLAIVE_VOLATILE.get(), true);
                } else {
                    stack.remove(TennoryRegistries.GLAIVE_VOLATILE.get());
                }
                player.getInventory().setChanged();
                player.displayClientMessage(Component.translatable("message.tennory.glaive.volatile", modeName(enabled)), true);
            }
            return InteractionResultHolder.consume(stack);
        }
        boolean occupied = level.isClientSide()
                ? player.getExistingData(TennoryRegistries.CLIENT_GLAIVE_IN_FLIGHT).orElse(false)
                : GlaiveFlights.hasStoredWeapon(player);
        if (occupied || !GlaiveReadiness.ready(player)) {
            return InteractionResultHolder.pass(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    public static boolean detonatesOnImpact(ItemStack stack) {
        return stack.getOrDefault(TennoryRegistries.GLAIVE_VOLATILE.get(), false);
    }

    public static Component modeName(boolean enabled) {
        return Component.translatable(enabled ? "mode.tennory.glaive.volatile" : "mode.tennory.glaive.normal");
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return MAX_USE_TICKS;
    }

    public int chargeTicks() {
        return chargeTicks;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (entity instanceof ServerPlayer player && getUseDuration(stack, entity) - timeLeft >= chargeTicks) {
            GlaiveFlights.launch(player, stack);
        }
    }
}
