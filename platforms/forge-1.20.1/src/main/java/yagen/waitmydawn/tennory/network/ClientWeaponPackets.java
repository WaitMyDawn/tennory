package yagen.waitmydawn.tennory.network;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import yagen.waitmydawn.tennory.combat.GlaiveRules;
import yagen.waitmydawn.tennory.config.ClientConfigs;

final class ClientWeaponPackets {
    private static final DustParticleOptions EDGE_SPARK = new DustParticleOptions(new Vector3f(0.96F, 0.68F, 0.25F), 0.85F);
    private static final int EDGE_POINTS = 32;

    private ClientWeaponPackets() {}

    static void flight(boolean active) {
        var player = Minecraft.getInstance().player;
        if (player != null) ClientWeaponState.setActiveFlight(player, active);
    }

    static void volley(WeaponPayloads.VolleyGesture gesture) {
        var level = Minecraft.getInstance().level;
        if (level != null && level.getEntity(gesture.playerId()) instanceof Player player) ClientWeaponState.setVolley(player, gesture);
    }

    static void glaiveThrow(int id, long startTick) {
        var level = Minecraft.getInstance().level;
        if (level != null && level.getEntity(id) instanceof Player player) ClientWeaponState.setThrowTick(player, startTick);
    }

    static void burst(Vec3 center) {
        var observer = Minecraft.getInstance().player;
        if (observer == null) return;
        var level = observer.level();
        level.addParticle(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 0.0, 0.0, 0.0);
        if (!ClientConfigs.SHOW_GLAIVE_BLAST_RADIUS.get()) return;
        for (int index = 0; index < EDGE_POINTS; index++) {
            double angle = 2.0 * Math.PI * index / EDGE_POINTS;
            Vec3 point = center.add(Math.cos(angle) * GlaiveRules.BLAST_RADIUS, 0.0, Math.sin(angle) * GlaiveRules.BLAST_RADIUS);
            if (level.clip(new ClipContext(center, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, observer))
                    .getType() == HitResult.Type.MISS) {
                level.addParticle(EDGE_SPARK, point.x, point.y, point.z, 0.0, 0.0, 0.0);
            }
        }
    }
}
