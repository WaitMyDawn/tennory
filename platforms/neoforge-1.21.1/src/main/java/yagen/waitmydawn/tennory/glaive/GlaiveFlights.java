package yagen.waitmydawn.tennory.glaive;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;
import yagen.waitmydawn.tennory.Tennory;
import yagen.waitmydawn.tennory.combat.GlaiveRules;
import yagen.waitmydawn.tennory.network.WeaponPayloads;
import yagen.waitmydawn.tennory.registries.TennoryRegistries;

@EventBusSubscriber(modid = Tennory.MODID)
public final class GlaiveFlights {
    private GlaiveFlights() {
    }

    static boolean hasStoredWeapon(Player player) {
        GlaiveFlightItem flight = player.getExistingDataOrNull(TennoryRegistries.STORED_GLAIVE);
        return flight != null && !flight.stack().isEmpty();
    }

    public static boolean hasActiveProjectile(ServerPlayer player) {
        GlaiveFlightItem flight = player.getExistingDataOrNull(TennoryRegistries.STORED_GLAIVE);
        return flight != null && flight.projectileId().isPresent();
    }

    static void launch(ServerPlayer player, ItemStack stack) {
        if (!player.isAlive() || player.isSpectator() || player.getMainHandItem() != stack
                || !(stack.getItem() instanceof GlaiveWeaponItem) || hasStoredWeapon(player)) {
            return;
        }

        ServerLevel level = player.serverLevel();
        GlaiveProjectile projectile = new GlaiveProjectile(level, player,
                (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE), stack.getItem(),
                GlaiveWeaponItem.detonatesOnImpact(stack));
        projectile.setPos(player.getEyePosition().add(0.0, -0.15, 0.0));
        projectile.setDeltaMovement(player.getLookAngle().scale(GlaiveRules.THROW_SPEED));

        Inventory inventory = player.getInventory();
        int originalSlot = inventory.selected;
        // The attachment and inventory are saved on the same player. Only the attachment can return the weapon.
        player.setData(TennoryRegistries.STORED_GLAIVE,
                new GlaiveFlightItem(stack.copy(), originalSlot, Optional.of(projectile.getUUID())));
        inventory.setItem(originalSlot, ItemStack.EMPTY);
        inventory.setChanged();
        if (!level.addFreshEntity(projectile) || projectile.isRemoved()) {
            finish(player, projectile.getUUID());
            return;
        }
        WeaponPayloads.sendFlightState(player, true);
        WeaponPayloads.sendGlaiveThrow(player);
        player.swing(InteractionHand.MAIN_HAND, true);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.7F, 1.2F);
    }

    public static void activateSpecial(ServerPlayer player) {
        GlaiveFlightItem flight = player.getExistingDataOrNull(TennoryRegistries.STORED_GLAIVE);
        if (flight == null || flight.projectileId().isEmpty() || !player.isAlive() || player.isSpectator()) {
            return;
        }
        UUID projectileId = flight.projectileId().orElseThrow();
        GlaiveProjectile projectile = findProjectile(player, projectileId);
        if (projectile != null) {
            projectile.detonate(player);
        } else {
            finish(player, projectileId);
        }
    }

    static boolean isActive(ServerPlayer player, UUID projectileId) {
        GlaiveFlightItem flight = player.getExistingDataOrNull(TennoryRegistries.STORED_GLAIVE);
        return flight != null && flight.projectileId().filter(projectileId::equals).isPresent();
    }

    static void finish(ServerPlayer player, UUID projectileId) {
        GlaiveFlightItem flight = player.getExistingDataOrNull(TennoryRegistries.STORED_GLAIVE);
        if (flight == null || flight.projectileId().filter(projectileId::equals).isEmpty()) {
            return;
        }
        // Invalidate the flight before delivering the item, including when a stale entity is still ticking.
        GlaiveFlightItem pending = flight.awaitingDelivery();
        player.setData(TennoryRegistries.STORED_GLAIVE, pending);
        WeaponPayloads.sendFlightState(player, false);
        if (player.isAlive() && !deliver(player, pending) && player.connection != null) {
            player.displayClientMessage(Component.translatable("message.tennory.glaive.return_waiting"), true);
        }
    }

    private static boolean deliver(ServerPlayer player, GlaiveFlightItem flight) {
        Inventory inventory = player.getInventory();
        int slot = flight.originalSlot();
        if (!inventory.getItem(slot).isEmpty()) {
            slot = inventory.getFreeSlot();
        }
        if (slot < 0) {
            return false;
        }
        inventory.setItem(slot, flight.stack());
        player.removeData(TennoryRegistries.STORED_GLAIVE);
        inventory.setChanged();
        return true;
    }

    @Nullable
    private static GlaiveProjectile findProjectile(ServerPlayer player, UUID id) {
        if (player.serverLevel().getEntity(id) instanceof GlaiveProjectile projectile
                && !projectile.isRemoved() && projectile.getOwner() == player) {
            return projectile;
        }
        return null;
    }

    static void reconcile(ServerPlayer player) {
        GlaiveFlightItem flight = player.getExistingDataOrNull(TennoryRegistries.STORED_GLAIVE);
        if (flight == null || flight.stack().isEmpty()) {
            return;
        }
        if (flight.projectileId().isEmpty()) {
            if (player.isAlive()) {
                deliver(player, flight);
            }
            return;
        }
        UUID id = flight.projectileId().orElseThrow();
        if (!player.isAlive() || player.isSpectator() || findProjectile(player, id) == null) {
            finish(player, id);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            reconcile(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GlaiveFlightItem flight = player.getExistingDataOrNull(TennoryRegistries.STORED_GLAIVE);
            if (flight != null && flight.projectileId().isPresent()) {
                GlaiveProjectile projectile = findProjectile(player, flight.projectileId().orElseThrow());
                player.setData(TennoryRegistries.STORED_GLAIVE, flight.awaitingDelivery());
                if (projectile != null) {
                    projectile.discard();
                }
            }
        }
    }
}
