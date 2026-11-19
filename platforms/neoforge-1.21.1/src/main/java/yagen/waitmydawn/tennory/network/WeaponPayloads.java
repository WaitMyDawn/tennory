package yagen.waitmydawn.tennory.network;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.joml.Vector3f;
import yagen.waitmydawn.tennory.Tennory;
import yagen.waitmydawn.tennory.combat.GlaiveRules;
import yagen.waitmydawn.tennory.config.ClientConfigs;
import yagen.waitmydawn.tennory.glaive.GlaiveFlights;
import yagen.waitmydawn.tennory.registries.TennoryRegistries;

public final class WeaponPayloads {
    private WeaponPayloads() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("4")
                .playToServer(SpecialAction.TYPE, StreamCodec.unit(SpecialAction.INSTANCE), (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        GlaiveFlights.activateSpecial(player);
                    }
                })
                .playToClient(GlaiveFlightState.TYPE, GlaiveFlightState.STREAM_CODEC,
                        (payload, context) -> context.player().setData(TennoryRegistries.CLIENT_GLAIVE_IN_FLIGHT, payload.active()))
                .playToClient(GlaiveBurst.TYPE, GlaiveBurst.STREAM_CODEC, (payload, context) -> payload.show(context.player()));
    }

    public static void sendFlightState(ServerPlayer player, boolean active) {
        if (player.connection != null) {
            PacketDistributor.sendToPlayer(player, new GlaiveFlightState(active));
        }
    }

    public static void sendGlaiveBurst(ServerLevel level, Vec3 center) {
        PacketDistributor.sendToPlayersNear(level, null, center.x, center.y, center.z, 32.0,
                new GlaiveBurst(center.x, center.y, center.z));
    }

    public record SpecialAction() implements CustomPacketPayload {
        public static final SpecialAction INSTANCE = new SpecialAction();
        public static final Type<SpecialAction> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Tennory.MODID, "weapon_special"));

        @Override
        public Type<SpecialAction> type() {
            return TYPE;
        }
    }

    private record GlaiveFlightState(boolean active) implements CustomPacketPayload {
        static final Type<GlaiveFlightState> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Tennory.MODID, "glaive_flight_state"));
        static final StreamCodec<RegistryFriendlyByteBuf, GlaiveFlightState> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.BOOL, GlaiveFlightState::active, GlaiveFlightState::new);

        @Override
        public Type<GlaiveFlightState> type() {
            return TYPE;
        }
    }

    private record GlaiveBurst(double x, double y, double z) implements CustomPacketPayload {
        private static final Type<GlaiveBurst> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Tennory.MODID, "glaive_burst"));
        private static final StreamCodec<RegistryFriendlyByteBuf, GlaiveBurst> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, GlaiveBurst::x, ByteBufCodecs.DOUBLE, GlaiveBurst::y,
                ByteBufCodecs.DOUBLE, GlaiveBurst::z, GlaiveBurst::new);
        private static final DustParticleOptions EDGE_SPARK = new DustParticleOptions(new Vector3f(0.96F, 0.68F, 0.25F), 0.85F);
        private static final int EDGE_POINTS = 32;

        private void show(Player observer) {
            Level level = observer.level();
            level.addParticle(ParticleTypes.EXPLOSION, x, y, z, 0.0, 0.0, 0.0);
            if (!ClientConfigs.SHOW_GLAIVE_BLAST_RADIUS.get()) {
                return;
            }
            Vec3 center = new Vec3(x, y, z);
            for (int index = 0; index < EDGE_POINTS; index++) {
                double angle = 2.0 * Math.PI * index / EDGE_POINTS;
                Vec3 point = center.add(Math.cos(angle) * GlaiveRules.BLAST_RADIUS, 0.0,
                        Math.sin(angle) * GlaiveRules.BLAST_RADIUS);
                if (level.clip(new ClipContext(center, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, observer))
                        .getType() == HitResult.Type.MISS) {
                    level.addParticle(EDGE_SPARK, point.x, point.y, point.z, 0.0, 0.0, 0.0);
                }
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
