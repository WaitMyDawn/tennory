package yagen.waitmydawn.tennory.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import yagen.waitmydawn.tennory.Tennory;

public final class WeaponAttackCompat {
    private static final String MOD = "yagens_attributes";
    private static final Link LINK = ModList.get().isLoaded(MOD) ? link() : null;
    public static final Snapshot NATIVE = new Snapshot(null);

    private WeaponAttackCompat() {}

    public static final class Snapshot {
        private final Object value;

        private Snapshot(Object value) {
            this.value = value;
        }

        public boolean hurt(Entity target, DamageSource source, double damage) {
            return hurt(target, source, damage, 1, 1, 1);
        }

        public boolean hurt(Entity target, DamageSource source, double baseDamage, double multiplier, int hits, int portions) {
            if (value == null) return target.hurt(source, (float) (baseDamage * multiplier / portions) * hits);
            try {
                return (boolean) LINK.hurt().invokeExact(value, target, source, baseDamage, multiplier, hits, portions);
            } catch (RuntimeException | Error failure) {
                throw failure;
            } catch (Throwable failure) {
                // Retrying after entry could deliver the same attack twice.
                throw new IllegalStateException("Yagens-Attributes attack settlement failed", failure);
            }
        }
    }

    public static Snapshot capture(ServerPlayer player, ItemStack stack, ResourceLocation attack) {
        if (LINK == null) return NATIVE;
        try {
            Object value = (Object) LINK.capture().invokeExact(player, stack, attack);
            return value == null ? NATIVE : new Snapshot(value);
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException("Yagens-Attributes attack capture failed", failure);
        }
    }

    private record Link(MethodHandle capture, MethodHandle hurt) {}

    private static Link link() {
        try {
            var api = Class.forName("yagen.waitmydawn.yagens_attributes.api.WeaponAttackApi");
            var capture = api.getMethod("capture", ServerPlayer.class, ItemStack.class, ResourceLocation.class);
            var hurt = api.getMethod("hurt", capture.getReturnType(), Entity.class, DamageSource.class, double.class, double.class,
                    int.class, int.class);
            var lookup = MethodHandles.publicLookup();
            return new Link(lookup.unreflect(capture).asType(MethodType.methodType(Object.class,
                    ServerPlayer.class, ItemStack.class, ResourceLocation.class)), lookup.unreflect(hurt).asType(
                    MethodType.methodType(boolean.class, Object.class, Entity.class, DamageSource.class, double.class, double.class,
                            int.class, int.class)));
        } catch (ReflectiveOperationException | LinkageError failure) {
            Tennory.LOGGER.warn("Yagens-Attributes weapon attack API unavailable; using native special attacks", failure);
            return null;
        }
    }
}
