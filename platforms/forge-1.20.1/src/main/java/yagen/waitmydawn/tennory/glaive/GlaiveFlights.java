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
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import yagen.waitmydawn.tennory.Tennory;
import yagen.waitmydawn.tennory.combat.GlaiveRules;
import yagen.waitmydawn.tennory.network.WeaponPayloads;

@Mod.EventBusSubscriber(modid = Tennory.MODID)
public final class GlaiveFlights {
    private static final String FLIGHT_TAG = "tennory:stored_glaive";
    private GlaiveFlights() {
    }

    // Forge carries PlayerPersisted through respawn alongside the inventory.
    @Nullable
    private static GlaiveFlightItem stored(Player player) {
        Tag tag = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).get(FLIGHT_TAG);
        return tag == null ? null : GlaiveFlightItem.CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(null);
    }

    private static void store(ServerPlayer player, GlaiveFlightItem flight) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        Tag tag = GlaiveFlightItem.CODEC.encodeStart(NbtOps.INSTANCE, flight)
                .getOrThrow(false, message -> Tennory.LOGGER.error("Cannot save glaive in flight: {}", message));
        persisted.put(FLIGHT_TAG, tag);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    static boolean hasStoredWeapon(Player player) {
        GlaiveFlightItem flight = stored(player);
        return flight != null && !flight.stack().isEmpty();
    }

    public static boolean hasActiveProjectile(ServerPlayer player) {
        GlaiveFlightItem flight = stored(player);
        return flight != null && flight.projectileId().isPresent();
    }

    static void launch(ServerPlayer player, ItemStack stack) {
        if (!player.isAlive() || player.isSpectator() || player.getMainHandItem() != stack
                || !(stack.getItem() instanceof GlaiveWeaponItem) || hasStoredWeapon(player)) {
            return;
        }

        ServerLevel level = player.serverLevel();
        GlaiveProjectile projectile = new GlaiveProjectile(level, player,
                (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE), stack,
                GlaiveWeaponItem.detonatesOnImpact(stack));
        projectile.setPos(player.getEyePosition().add(0.0, -0.15, 0.0));
        projectile.setDeltaMovement(player.getLookAngle().scale(GlaiveRules.THROW_SPEED));

        Inventory inventory = player.getInventory();
        int originalSlot = inventory.selected;
        // The attachment and inventory are saved on the same player. Only the attachment can return the weapon.
        store(player,
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
                SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 0.7F, 1.2F);
    }

    public static void activateSpecial(ServerPlayer player) {
        GlaiveFlightItem flight = stored(player);
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
        GlaiveFlightItem flight = stored(player);
        return flight != null && flight.projectileId().filter(projectileId::equals).isPresent();
    }

    static void finish(ServerPlayer player, UUID projectileId) {
        GlaiveFlightItem flight = stored(player);
        if (flight == null || flight.projectileId().filter(projectileId::equals).isEmpty()) {
            return;
        }
        // Invalidate the flight before delivering the item, including when a stale entity is still ticking.
        GlaiveFlightItem pending = flight.awaitingDelivery();
        store(player, pending);
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
        player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).remove(FLIGHT_TAG);
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
        GlaiveFlightItem flight = stored(player);
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
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            reconcile(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GlaiveFlightItem flight = stored(player);
            if (flight != null && flight.projectileId().isPresent()) {
                GlaiveProjectile projectile = findProjectile(player, flight.projectileId().orElseThrow());
                store(player, flight.awaitingDelivery());
                if (projectile != null) {
                    projectile.discard();
                }
            }
        }
    }
}
