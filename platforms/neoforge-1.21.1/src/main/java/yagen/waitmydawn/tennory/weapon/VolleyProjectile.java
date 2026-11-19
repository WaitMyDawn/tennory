package yagen.waitmydawn.tennory.weapon;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import yagen.waitmydawn.tennory.Tennory;
import yagen.waitmydawn.tennory.combat.Spread;
import yagen.waitmydawn.tennory.combat.VolleyProfile;
import yagen.waitmydawn.tennory.registries.TennoryRegistries;

public final class VolleyProjectile extends Projectile {
    private static final ResourceKey<DamageType> VOLLEY_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(Tennory.MODID, "weapon_volley"));
    private static final EntityDataAccessor<Integer> PROFILE =
            SynchedEntityData.defineId(VolleyProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CHAMBER =
            SynchedEntityData.defineId(VolleyProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STOPPED =
            SynchedEntityData.defineId(VolleyProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> LAUNCH_TICK =
            SynchedEntityData.defineId(VolleyProjectile.class, EntityDataSerializers.LONG);
    private float rayDamage;
    private double traveled;
    private VolleyWeaponItem.ShotSound shotSound;
    private boolean released;

    public VolleyProjectile(EntityType<? extends VolleyProjectile> type, Level level) {
        super(type, level);
        noCulling = true;
    }

    VolleyProjectile(ServerPlayer owner, VolleyProfile profile, int chamber, double damage, VolleyWeaponItem.ShotSound shotSound) {
        this(TennoryRegistries.VOLLEY_PROJECTILE.get(), owner.level());
        setOwner(owner);
        this.shotSound = shotSound;
        setPos(owner.getEyePosition().add(0.0, -0.21, 0.0));
        setYRot(owner.getYRot());
        setXRot(owner.getXRot());
        entityData.set(PROFILE, profile.ordinal());
        entityData.set(CHAMBER, chamber);
        entityData.set(LAUNCH_TICK, level().getGameTime() + profile.releaseTicks());
        rayDamage = (float) (damage / profile.rays(chamber).size());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder data) {
        data.define(PROFILE, 0);
        data.define(CHAMBER, 0);
        data.define(STOPPED, 0);
        data.define(LAUNCH_TICK, 0L);
    }

    public VolleyProfile profile() {
        return VolleyProfile.values()[entityData.get(PROFILE)];
    }

    public Rays rays() {
        Vec3 forward = Vec3.directionFromRotation(getXRot(), getYRot());
        Vec3 right = Vec3.directionFromRotation(0.0F, getYRot() + 90.0F);
        return new Rays(forward, right, right.cross(forward), profile().rays(entityData.get(CHAMBER)));
    }

    public double renderedDepth(float partialTick) {
        double age = level().getGameTime() - entityData.get(LAUNCH_TICK) - 1.0 + partialTick;
        return Math.clamp(age * profile().speed(), 0.0, VolleyProfile.RANGE);
    }

    public boolean stopped(int index) {
        return (entityData.get(STOPPED) & (1 << index)) != 0;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSquared) {
        // The tracked entity stays at the launch point while the rendered rays travel downrange.
        double radius = 2.0 * VolleyProfile.RANGE * getViewScale();
        return distanceSquared < radius * radius;
    }

    @Override
    public void tick() {
        if (level().isClientSide()) {
            super.tick();
            return;
        }
        Entity owner = getOwner();
        if (owner == null || !owner.isAlive() || owner.level() != level() || traveled >= VolleyProfile.RANGE) {
            discard();
            return;
        }
        if (!released) {
            if (level().getGameTime() < entityData.get(LAUNCH_TICK)) {
                return;
            }
            // Aim is sampled at release so the player can track a target during the wind-up.
            setPos(owner.getEyePosition().add(0.0, -0.21, 0.0));
            setYRot(owner.getYRot());
            setXRot(owner.getXRot());
            level().playSound(null, getX(), getY(), getZ(), shotSound.event(), SoundSource.PLAYERS,
                    shotSound.volume(), shotSound.pitchFor(entityData.get(CHAMBER)));
            released = true;
            super.tick();
            return;
        }
        super.tick();
        Rays rays = rays();
        int stopped = entityData.get(STOPPED);
        int count = rays.pattern().size();
        if (stopped == (1 << count) - 1) {
            discard();
            return;
        }
        double endDepth = Math.min(VolleyProfile.RANGE, traveled + profile().speed());
        AABB bounds = new AABB(position().add(rays.forward().scale(traveled)), position().add(rays.forward().scale(endDepth)));
        for (int index = 0; index < count; index++) {
            Vec3 end = position().add(rays.direction(index).scale(endDepth));
            bounds = bounds.minmax(new AABB(end, end));
        }
        List<Entity> candidates = level().getEntities(this, bounds.inflate(0.15),
                target -> target != owner && canHitEntity(target));
        Map<Entity, Integer> hits = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            if ((stopped & (1 << index)) != 0) {
                continue;
            }
            Vec3 direction = rays.direction(index);
            Vec3 from = position().add(direction.scale(traveled));
            Vec3 end = position().add(direction.scale(endDepth));
            var block = level().clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            Vec3 limit = block.getLocation();
            Entity hit = nearestTarget(candidates, from, limit);
            if (hit != null) {
                hits.merge(hit, 1, Integer::sum);
            }
            if (hit != null || block.getType() != HitResult.Type.MISS) {
                stopped |= 1 << index;
            }
        }
        if (!hits.isEmpty()) {
            DamageSource source = damageSources().source(VOLLEY_DAMAGE, this, owner);
            // One hit event per target keeps armor and knockback independent of pellet order.
            hits.forEach((target, pellets) -> target.hurt(source, rayDamage * pellets));
        }
        entityData.set(STOPPED, stopped);
        traveled = endDepth;
    }

    private static Entity nearestTarget(List<Entity> candidates, Vec3 from, Vec3 limit) {
        double nearest = from.distanceToSqr(limit);
        Entity result = null;
        for (Entity target : candidates) {
            AABB box = target.getBoundingBox();
            Vec3 hit = box.contains(from) ? from : box.clip(from, limit).orElse(null);
            if (hit != null && from.distanceToSqr(hit) < nearest) {
                nearest = from.distanceToSqr(hit);
                result = target;
            }
        }
        return result;
    }

    public record Rays(Vec3 forward, Vec3 right, Vec3 up, List<Spread.Ray> pattern) {
        public Vec3 direction(int index) {
            Spread.Ray ray = pattern.get(index);
            return forward.add(right.scale(ray.right())).add(up.scale(ray.up()));
        }
    }
}
