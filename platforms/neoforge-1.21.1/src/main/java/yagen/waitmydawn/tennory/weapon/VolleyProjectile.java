package yagen.waitmydawn.tennory.weapon;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
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
import org.jetbrains.annotations.Nullable;
import yagen.waitmydawn.tennory.Tennory;
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
    private static final EntityDataAccessor<CompoundTag> LAUNCH =
            SynchedEntityData.defineId(VolleyProjectile.class, EntityDataSerializers.COMPOUND_TAG);

    private float rayDamage;
    private double traveled;
    private VolleyWeaponItem.ShotSound shotSound;
    private long launchTick;
    @Nullable
    private Rays rays;
    @Nullable
    private double[] visualImpactDepths;
    private int visuallyStopped;
    private double checkedVisualDepth;

    public VolleyProjectile(EntityType<? extends VolleyProjectile> type, Level level) {
        super(type, level);
        noCulling = true;
    }

    VolleyProjectile(ServerPlayer owner, VolleyProfile profile, int chamber, double damage, VolleyWeaponItem.ShotSound shotSound) {
        this(TennoryRegistries.VOLLEY_PROJECTILE.get(), owner.level());
        setOwner(owner);
        this.shotSound = shotSound;
        setPos(muzzle(owner));
        entityData.set(PROFILE, profile.ordinal());
        entityData.set(CHAMBER, chamber);
        launchTick = level().getGameTime() + profile.releaseTicks();
        rayDamage = (float) (damage / profile.rays(chamber).size());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder data) {
        data.define(PROFILE, 0);
        data.define(CHAMBER, 0);
        data.define(STOPPED, 0);
        data.define(LAUNCH, new CompoundTag());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (!level().isClientSide() || !LAUNCH.equals(accessor)) {
            return;
        }
        CompoundTag data = entityData.get(LAUNCH);
        Vec3 origin = new Vec3(data.getDouble("x"), data.getDouble("y"), data.getDouble("z"));
        rays = createRays(origin, data.getFloat("yaw"), data.getFloat("pitch"));
        // Use the entity's local age after the launch snapshot so clock corrections cannot rewind the tracers.
        launchTick = tickCount - Math.max(0L, level().getGameTime() - data.getLong("tick"));
    }

    public VolleyProfile profile() {
        return VolleyProfile.values()[entityData.get(PROFILE)];
    }

    @Nullable
    public Rays rays() {
        return rays;
    }

    public double renderedDepth(float partialTick) {
        if (rays == null) {
            return 0.0;
        }
        long tick = level().isClientSide() ? tickCount : level().getGameTime();
        double age = tick - launchTick - 1.0 + partialTick;
        return Math.clamp(age * profile().speed(), 0.0, VolleyProfile.RANGE);
    }

    public boolean stopped(int index, double depth) {
        return (entityData.get(STOPPED) & (1 << index)) != 0
                || visualImpactDepths != null && depth >= visualImpactDepths[index];
    }

    public void traceVisualImpacts(double depth) {
        Rays shot = rays;
        if (!level().isClientSide() || shot == null || depth <= checkedVisualDepth) {
            return;
        }
        if (visualImpactDepths == null) {
            visualImpactDepths = new double[shot.directions().size()];
            Arrays.fill(visualImpactDepths, Double.POSITIVE_INFINITY);
        }
        double[] impactDepths = visualImpactDepths;
        double speed = profile().speed();
        double endDepth = Math.min(VolleyProfile.RANGE, Math.ceil(depth / speed) * speed);
        // Trace once per travel step. This bounds the render before the server's hit mask arrives.
        visuallyStopped = traceSegment(shot, checkedVisualDepth, endDepth, visuallyStopped | entityData.get(STOPPED),
                (index, impact) -> {
                    Vec3 direction = shot.directions().get(index);
                    impactDepths[index] = impact.position().subtract(shot.origin()).dot(direction) / direction.lengthSqr();
                });
        checkedVisualDepth = endDepth;
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
        if (rays == null) {
            if (level().getGameTime() >= launchTick) {
                release(owner);
                super.tick();
            }
            return;
        }
        super.tick();
        int stopped = entityData.get(STOPPED);
        if (stopped == (1 << rays.directions().size()) - 1) {
            discard();
            return;
        }
        double endDepth = Math.min(VolleyProfile.RANGE, traveled + profile().speed());
        Map<Entity, Integer> hits = new LinkedHashMap<>();
        int newMask = traceSegment(rays, traveled, endDepth, stopped, (index, impact) -> {
            if (impact.target() != null) {
                hits.merge(impact.target(), 1, Integer::sum);
            }
        });
        if (!hits.isEmpty()) {
            DamageSource source = damageSources().source(VOLLEY_DAMAGE, this, owner);
            // One hit event per target keeps armor and knockback independent of pellet order.
            hits.forEach((target, pellets) -> target.hurt(source, rayDamage * pellets));
        }
        entityData.set(STOPPED, newMask);
        traveled = endDepth;
    }

    private void release(Entity owner) {
        // Freeze both origin and aim at release, before position and rotation tracking can arrive independently.
        Vec3 origin = muzzle(owner);
        float yaw = owner.getYRot();
        float pitch = owner.getXRot();
        launchTick = level().getGameTime();
        rays = createRays(origin, yaw, pitch);
        setPos(origin);
        CompoundTag data = new CompoundTag();
        data.putDouble("x", origin.x);
        data.putDouble("y", origin.y);
        data.putDouble("z", origin.z);
        data.putFloat("yaw", yaw);
        data.putFloat("pitch", pitch);
        data.putLong("tick", launchTick);
        entityData.set(LAUNCH, data);
        level().playSound(null, origin.x, origin.y, origin.z, shotSound.event(), SoundSource.PLAYERS,
                shotSound.volume(), shotSound.pitchFor(entityData.get(CHAMBER)));
    }

    private Rays createRays(Vec3 origin, float yaw, float pitch) {
        Vec3 forward = Vec3.directionFromRotation(pitch, yaw);
        Vec3 right = Vec3.directionFromRotation(0.0F, yaw + 90.0F);
        Vec3 up = right.cross(forward);
        List<Vec3> directions = profile().rays(entityData.get(CHAMBER)).stream()
                .map(ray -> forward.add(right.scale(ray.right())).add(up.scale(ray.up())))
                .toList();
        return new Rays(origin, forward, up, directions);
    }

    private int traceSegment(Rays shot, double startDepth, double endDepth, int stopped, ImpactConsumer onImpact) {
        if (stopped == (1 << shot.directions().size()) - 1) {
            return stopped;
        }
        Vec3 origin = shot.origin();
        AABB bounds = new AABB(origin.add(shot.forward().scale(startDepth)), origin.add(shot.forward().scale(endDepth)));
        for (Vec3 direction : shot.directions()) {
            Vec3 end = origin.add(direction.scale(endDepth));
            bounds = bounds.minmax(new AABB(end, end));
        }
        Entity owner = getOwner();
        List<Entity> candidates = level().getEntities(this, bounds.inflate(0.15),
                target -> target != owner && canHitEntity(target));
        for (int index = 0; index < shot.directions().size(); index++) {
            int bit = 1 << index;
            if ((stopped & bit) != 0) {
                continue;
            }
            Vec3 direction = shot.directions().get(index);
            Vec3 from = origin.add(direction.scale(startDepth));
            Vec3 end = origin.add(direction.scale(endDepth));
            Impact impact = firstImpact(from, end, candidates);
            if (impact != null) {
                stopped |= bit;
                onImpact.hit(index, impact);
            }
        }
        return stopped;
    }

    @Nullable
    private Impact firstImpact(Vec3 from, Vec3 end, List<Entity> candidates) {
        var block = level().clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 limit = block.getLocation();
        double nearest = from.distanceToSqr(limit);
        Entity target = null;
        Vec3 position = limit;
        for (Entity candidate : candidates) {
            AABB box = candidate.getBoundingBox();
            Vec3 hit = box.contains(from) ? from : box.clip(from, limit).orElse(null);
            if (hit != null && from.distanceToSqr(hit) < nearest) {
                nearest = from.distanceToSqr(hit);
                target = candidate;
                position = hit;
            }
        }
        return target != null || block.getType() != HitResult.Type.MISS ? new Impact(position, target) : null;
    }

    private static Vec3 muzzle(Entity owner) {
        return owner.getEyePosition().add(0.0, -0.21, 0.0);
    }

    public record Rays(Vec3 origin, Vec3 forward, Vec3 up, List<Vec3> directions) {
    }

    private record Impact(Vec3 position, @Nullable Entity target) {
    }

    @FunctionalInterface
    private interface ImpactConsumer {
        void hit(int index, Impact impact);
    }
}
