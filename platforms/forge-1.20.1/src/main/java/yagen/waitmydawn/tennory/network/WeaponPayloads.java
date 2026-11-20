package yagen.waitmydawn.tennory.network;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import yagen.waitmydawn.tennory.Tennory;
import yagen.waitmydawn.tennory.combat.VolleyProfile;
import yagen.waitmydawn.tennory.glaive.GlaiveFlights;
import yagen.waitmydawn.tennory.weapon.VolleyWeaponItem;

public final class WeaponPayloads {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(Tennory.MODID, "weapons"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private WeaponPayloads() {}

    public static void register() {
        CHANNEL.messageBuilder(SpecialAction.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((action, buffer) -> {}).decoder(buffer -> SpecialAction.INSTANCE)
                .consumerMainThread((message, context) -> handleSpecial(context.get().getSender())).add();
        registerClient(1, VolleyGesture.class, VolleyGesture::write, VolleyGesture::read,
                value -> ClientWeaponPackets.volley(value));
        registerClient(2, GlaiveFlightState.class, (value, buffer) -> buffer.writeBoolean(value.active()),
                buffer -> new GlaiveFlightState(buffer.readBoolean()), value -> ClientWeaponPackets.flight(value.active()));
        registerClient(3, GlaiveThrowGesture.class, (value, buffer) -> {
                    buffer.writeVarInt(value.playerId());
                    buffer.writeVarLong(value.startTick());
                }, buffer -> new GlaiveThrowGesture(buffer.readVarInt(), buffer.readVarLong()),
                value -> ClientWeaponPackets.glaiveThrow(value.playerId(), value.startTick()));
        registerClient(4, GlaiveBurst.class, (value, buffer) -> {
                    buffer.writeDouble(value.x());
                    buffer.writeDouble(value.y());
                    buffer.writeDouble(value.z());
                }, buffer -> new GlaiveBurst(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                value -> ClientWeaponPackets.burst(new Vec3(value.x(), value.y(), value.z())));
    }

    private static <T> void registerClient(int id, Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder,
                                           Function<FriendlyByteBuf, T> decoder, Consumer<T> clientHandler) {
        CHANNEL.messageBuilder(type, id, NetworkDirection.PLAY_TO_CLIENT).encoder(encoder).decoder(decoder)
                .consumerMainThread((value, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> clientHandler.accept(value)))
                .add();
    }

    private static void handleSpecial(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator()) return;
        if (GlaiveFlights.hasActiveProjectile(player)) {
            GlaiveFlights.activateSpecial(player);
        } else {
            ItemStack stack = player.getMainHandItem();
            if (stack.getItem() instanceof VolleyWeaponItem weapon) weapon.fireSpecial(player, stack);
        }
    }

    public static void requestSpecial() { CHANNEL.sendToServer(SpecialAction.INSTANCE); }

    public static void sendFlightState(ServerPlayer player, boolean active) {
        if (player.connection != null) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new GlaiveFlightState(active));
    }

    public static void sendGlaiveBurst(ServerLevel level, Vec3 center) {
        CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(center.x, center.y, center.z,
                32.0, level.dimension())), new GlaiveBurst(center.x, center.y, center.z));
    }

    public static void sendGlaiveThrow(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> player),
                new GlaiveThrowGesture(player.getId(), player.level().getGameTime()));
    }

    public static void sendVolleyGesture(ServerPlayer player, VolleyProfile profile, int chamber) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new VolleyGesture(player.getId(), profile.ordinal(), chamber, player.level().getGameTime()));
    }

    public record VolleyGesture(int playerId, int profileId, int chamber, long startTick) {
        private void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(playerId);
            buffer.writeVarInt(profileId);
            buffer.writeVarInt(chamber);
            buffer.writeVarLong(startTick);
        }

        private static VolleyGesture read(FriendlyByteBuf buffer) {
            return new VolleyGesture(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarLong());
        }
    }

    private enum SpecialAction { INSTANCE }
    private record GlaiveFlightState(boolean active) {}
    private record GlaiveThrowGesture(int playerId, long startTick) {}
    private record GlaiveBurst(double x, double y, double z) {}
}
