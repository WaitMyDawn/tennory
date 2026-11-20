package yagen.waitmydawn.tennory.glaive;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import yagen.waitmydawn.tennory.Tennory;
import yagen.waitmydawn.tennory.combat.GlaiveRules;
import yagen.waitmydawn.tennory.combat.WeaponStats;
import yagen.waitmydawn.tennory.network.WeaponPayloads;
import yagen.waitmydawn.tennory.registries.TennoryRegistries;

public final class GlaiveProjectile extends Projectile {
    private static final ResourceLocation THROWN_ATTACK = new ResourceLocation(Tennory.MODID, "glaive_throw");
    private static final ResourceLocation BURST_ATTACK = new ResourceLocation(Tennory.MODID, "glaive_burst");
    private static final ResourceKey<DamageType> BURST_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, BURST_ATTACK);
    private static final double SURFACE_OFFSET = 0.001;
    private static final double ENTITY_MARGIN = 0.25;
    private static final double CATCH_DISTANCE = 0.65;
    private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);
    private static final Quaternionf LEVEL_POSE = new Quaternionf().rotationX(Mth.HALF_PI);
    private static final EntityDataAccessor<ItemStack> DISPLAY_STACK =
            SynchedEntityData.defineId(GlaiveProjectile.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Quaternionf> FLIGHT_POSE =
            SynchedEntityData.defineId(GlaiveProjectile.class, EntityDataSerializers.QUATERNION);

    private int bounces;
    private boolean returning;
    private boolean detonated;
    private boolean impactDetonation;
    private float attackDamage;
    @Nullable
    private Entity lastHitTarget;
    private final Set<UUID> returnHits = new HashSet<>();
    private Vec3 lerpTarget = Vec3.ZERO;
    private int lerpSteps;
    private final Quaternionf previousPose = new Quaternionf(LEVEL_POSE);
    private final Quaternionf renderPose = new Quaternionf(LEVEL_POSE);

    public GlaiveProjectile(EntityType<? extends GlaiveProjectile> type, Level level) {
        super(type, level);
    }

    GlaiveProjectile(ServerLevel level, ServerPlayer owner, float attackDamage, ItemStack weapon, boolean impactDetonation) {
        this(TennoryRegistries.GLAIVE_PROJECTILE.get(), level);
        setOwner(owner);
        this.attackDamage = attackDamage;
        this.impactDetonation = impactDetonation;
        entityData.set(DISPLAY_STACK, new ItemStack(weapon.getItem()));
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DISPLAY_STACK, ItemStack.EMPTY);
        entityData.define(FLIGHT_POSE, LEVEL_POSE);
    }

    public ItemStack getDisplayStack() {
        return entityData.get(DISPLAY_STACK);
    }

    public Quaternionf flightPose(float partialTick, Quaternionf output) {
        return previousPose.slerp(renderPose, partialTick, output);
    }

    @Override
    public void tick() {
        super.tick();
        if (isRemoved()) {
            return;
        }
        if (!(level() instanceof ServerLevel level)) {
            previousPose.set(renderPose);
            renderPose.slerp(entityData.get(FLIGHT_POSE), 0.7F);
            if (lerpSteps > 0) {
                setPos(position().lerp(lerpTarget, 1.0 / lerpSteps));
                lerpSteps--;
            }
            return;
        }
        if (!(getOwner() instanceof ServerPlayer owner) || owner.level() != level || !owner.isAlive()
                || owner.isSpectator() || !GlaiveFlights.isActive(owner, getUUID())) {
            discard();
            return;
        }
        if (returning) {
            tickReturn(level, owner);
        } else {
            tickOutbound(level, owner);
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
        lerpTarget = new Vec3(x, y, z);
        lerpSteps = teleport ? 1 : Math.max(1, steps);
    }

    private void tickOutbound(ServerLevel level, ServerPlayer owner) {
        Vec3 direction = getDeltaMovement().normalize();
        double remaining = GlaiveRules.THROW_SPEED;
        while (remaining > SURFACE_OFFSET && !returning && !isRemoved()) {
            Vec3 start = position();
            Vec3 fromOwner = start.subtract(owner.position());
            double distance = GlaiveRules.outboundDistance(fromOwner.lengthSqr(), fromOwner.dot(direction), remaining);
            if (distance <= SURFACE_OFFSET) {
                returning = true;
                break;
            }
            Vec3 end = start.add(direction.scale(distance));
            AABB path = pathBounds(start, end);
            if (!hasLoadedPath(level, path)) {
                recover(owner);
                return;
            }
            FlightHit hit = firstImpact(level, owner, start, end, path);
            if (hit == null) {
                setPos(end);
                returning = distance < remaining;
                break;
            }

            Entity target = hit.target();
            Vec3 normal = Vec3.atLowerCornerOf(hit.face().getNormal());
            setPos(target == null && !hit.insideBlock() ? hit.position().add(normal.scale(SURFACE_OFFSET)) : hit.position());
            if (target != null) {
                lastHitTarget = target;
                target.hurt(damageSources().thrown(this, owner), attackDamage);
            }
            bounces++;
            if (impactDetonation) {
                detonate(owner);
                return;
            }
            if (bounces >= GlaiveRules.BOUNCE_LIMIT || hit.insideBlock()) {
                returning = true;
                break;
            }

            LivingEntity next = target instanceof LivingEntity ? nearestRicochetTarget(level, owner, target, hit.position()) : null;
            Vec3 guided = next != null ? aimRicochetAt(next) : Vec3.ZERO;
            direction = guided.lengthSqr() > 0.0 ? guided : variedReflection(direction, normal);
            if (target != null) {
                setPos(hit.position().add(direction.scale(SURFACE_OFFSET)));
            }
            setFlightTilt(direction, Mth.randomBetween(random, -26.0F, 26.0F));
            remaining -= start.distanceTo(hit.position()) + SURFACE_OFFSET;
        }
        setDeltaMovement(direction.scale(GlaiveRules.THROW_SPEED));
    }

    @Nullable
    private FlightHit firstImpact(ServerLevel level, ServerPlayer owner, Vec3 start, Vec3 end, AABB path) {
        // Steering toward a nearby target can carry the projectile through the collider it just struck.
        if (lastHitTarget != null && !lastHitTarget.getBoundingBox().inflate(ENTITY_MARGIN + SURFACE_OFFSET).contains(start)) {
            lastHitTarget = null;
        }
        // Vanilla clipping excludes the exact ray endpoint. Probe past it, then check the real movement budget.
        Vec3 movement = end.subtract(start);
        double distance = movement.length();
        double maxDistanceSquared = Mth.square(distance + 1.0e-7);
        Vec3 probeEnd = distance > 0.0 ? end.add(movement.scale(SURFACE_OFFSET / distance)) : end;
        ClipContext context = new ClipContext(start, probeEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this);
        BlockHitResult block = level.clip(context);
        if (block.isInside()) {
            // The shape's inward probe can report 'inside' when the ray actually started just outside the face.
            BlockPos pos = block.getBlockPos();
            BlockHitResult boundary = AABB.clip(context.getBlockShape(level.getBlockState(pos), level, pos).toAabbs(),
                    start, probeEnd, pos);
            if (boundary != null) {
                block = boundary;
            }
        }
        FlightHit closest = block.getType() == HitResult.Type.BLOCK && start.distanceToSqr(block.getLocation()) <= maxDistanceSquared
                ? new FlightHit(block.getLocation(), block.getDirection(), null, block.isInside()) : null;
        double nearestDistance = closest == null ? Double.POSITIVE_INFINITY : start.distanceToSqr(closest.position());
        for (Entity target : level.getEntities(this, path,
                entity -> entity != owner && entity != lastHitTarget && entity.isAlive() && !entity.isSpectator() && entity.isPickable())) {
            AABB bounds = target.getBoundingBox().inflate(ENTITY_MARGIN);
            BlockHitResult contact = AABB.clip(List.of(bounds), start, probeEnd, BlockPos.ZERO);
            if (contact == null) {
                continue;
            }
            double contactDistance = start.distanceToSqr(contact.getLocation());
            if (contactDistance <= maxDistanceSquared && contactDistance < nearestDistance) {
                nearestDistance = contactDistance;
                closest = new FlightHit(contact.getLocation(), contact.getDirection(), target, false);
            }
        }
        return closest;
    }

    @Nullable
    private LivingEntity nearestRicochetTarget(ServerLevel level, ServerPlayer owner, Entity struck, Vec3 contact) {
        double radius = GlaiveRules.RICOCHET_RADIUS;
        double nearestDistance = radius * radius;
        LivingEntity nearest = null;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, new AABB(contact, contact).inflate(radius + SURFACE_OFFSET),
                entity -> entity != owner && entity != struck && entity.isAlive() && !entity.isSpectator() && entity.isPickable())) {
            double distance = candidate.getBoundingBox().distanceToSqr(contact);
            if (distance <= nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private Vec3 aimRicochetAt(LivingEntity target) {
        AABB bounds = target.getBoundingBox();
        Vec3 point = bounds.getCenter().add(bounds.getXsize() * Mth.randomBetween(random, -0.25F, 0.25F),
                bounds.getYsize() * Mth.randomBetween(random, -0.25F, 0.25F),
                bounds.getZsize() * Mth.randomBetween(random, -0.25F, 0.25F));
        return point.subtract(position()).normalize();
    }

    private Vec3 variedReflection(Vec3 incoming, Vec3 normal) {
        Vec3 reflected = incoming.subtract(normal.scale(2.0 * incoming.dot(normal)));
        float yaw = (float) Math.toDegrees(Math.atan2(-reflected.x, reflected.z));
        float pitch = flightPitch(reflected);
        Vec3 varied = Vec3.directionFromRotation(Mth.clamp(pitch + Mth.randomBetween(random, -12.0F, 12.0F), -89.0F, 89.0F),
                yaw + Mth.randomBetween(random, -18.0F, 18.0F));
        double alignment = varied.dot(normal);
        return alignment < 0.0 ? varied.subtract(normal.scale(2.0 * alignment)) : varied;
    }

    private void tickReturn(ServerLevel level, ServerPlayer owner) {
        Vec3 start = position();
        Vec3 catchPoint = owner.getEyePosition().add(0.0, -0.25, 0.0);
        Vec3 toOwner = catchPoint.subtract(start);
        double distance = toOwner.length();
        if (distance <= CATCH_DISTANCE) {
            recover(owner);
            return;
        }

        double speed = GlaiveRules.RETURN_SPEED;
        Vec3 straight = toOwner.scale(1.0 / distance);
        Vec3 direction = detonated && distance > speed
                ? straight.add(straight.cross(UP).normalize().scale(GlaiveRules.detonationReturnCurve(distance))).normalize()
                : straight;
        Vec3 end = start.add(direction.scale(Math.min(speed, distance)));
        AABB path = pathBounds(start, end);
        if (!hasLoadedPath(level, path)) {
            recover(owner);
            return;
        }

        DamageSource source = damageSources().thrown(this, owner);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, path,
                entity -> entity != owner && entity.isAlive() && !entity.isSpectator())) {
            AABB bounds = target.getBoundingBox().inflate(ENTITY_MARGIN);
            if ((bounds.contains(start) || bounds.clip(start, end).isPresent()) && returnHits.add(target.getUUID())) {
                target.hurt(source, attackDamage);
            }
        }
        setDeltaMovement(direction.scale(speed));
        setPos(end);
        if (end.distanceToSqr(catchPoint) <= CATCH_DISTANCE * CATCH_DISTANCE) {
            recover(owner);
        } else if (detonated) {
            bankTowardOwner(owner.getEyePosition().subtract(end));
        } else {
            entityData.set(FLIGHT_POSE, LEVEL_POSE);
        }
    }

    private void setFlightTilt(Vec3 heading, float bank) {
        float pitch = Mth.clamp(flightPitch(heading), -40.0F, 40.0F);
        double horizontal = heading.horizontalDistance();
        float x = horizontal <= SURFACE_OFFSET ? 0.0F : (float) (heading.x / horizontal);
        float z = horizontal <= SURFACE_OFFSET ? 1.0F : (float) (heading.z / horizontal);
        entityData.set(FLIGHT_POSE, new Quaternionf().rotationX((z * pitch + x * bank) * Mth.DEG_TO_RAD)
                .rotateZ((-x * pitch + z * bank) * Mth.DEG_TO_RAD).rotateX(Mth.HALF_PI));
    }

    private void bankTowardOwner(Vec3 towardEye) {
        // Tilt local +Z (the face normal) in the look-at frame so it stays 45 degrees off the eye line at all yaws.
        float yaw = (float) Math.atan2(towardEye.x, towardEye.z);
        float pitch = (float) Math.atan2(-towardEye.y, towardEye.horizontalDistance());
        entityData.set(FLIGHT_POSE, new Quaternionf().rotationY(yaw).rotateX(pitch)
                .rotateX(-Mth.PI / 4.0F));
    }

    private static float flightPitch(Vec3 heading) {
        return (float) Math.toDegrees(Math.atan2(-heading.y, heading.horizontalDistance()));
    }

    void detonate(ServerPlayer owner) {
        if (detonated || isRemoved() || getOwner() != owner || !(level() instanceof ServerLevel level)) {
            return;
        }
        detonated = true;
        returning = true;
        Vec3 center = position();
        bankTowardOwner(owner.getEyePosition().subtract(center));
        double radius = GlaiveRules.BLAST_RADIUS;
        DamageSource source = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(BURST_DAMAGE), this, owner);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius + SURFACE_OFFSET),
                entity -> entity != owner && entity.isAlive() && !entity.isSpectator())) {
            double distanceSquared = target.getBoundingBox().distanceToSqr(center);
            if (distanceSquared > radius * radius) {
                continue;
            }
            float exposure = Explosion.getSeenPercent(center, target);
            if (exposure > 0.0F) {
                double falloff = 1.0 - 0.4 * Mth.clamp(2.0 * Math.sqrt(distanceSquared) / radius - 1.0, 0.0, 1.0);
                double multiplier = WeaponStats.GLAIVE.specialMultiplier() * exposure * falloff;
                if (target.hurt(source, (float) (attackDamage * multiplier))) {
                    target.knockback(exposure * falloff, center.x - target.getX(), center.z - target.getZ());
                }
            }
        }
        WeaponPayloads.sendGlaiveBurst(level, center);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE,
                SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    private void recover(ServerPlayer owner) {
        GlaiveFlights.finish(owner, getUUID());
        discard();
    }

    private static AABB pathBounds(Vec3 start, Vec3 end) {
        return new AABB(start, end).inflate(ENTITY_MARGIN);
    }

    private static boolean hasLoadedPath(ServerLevel level, AABB path) {
        return level.hasChunksAt(Mth.floor(path.minX), Mth.floor(path.minZ), Mth.floor(path.maxX), Mth.floor(path.maxZ));
    }

    private record FlightHit(Vec3 position, Direction face, @Nullable Entity target, boolean insideBlock) {
    }
}
