package yagen.waitmydawn.tennory.glaive;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlaiveFlightItemTest {
    @BeforeAll
    static void bootstrapRegistries() {
        // NeoForge's vanilla bootstrap reads the feature flags from the loading mod list.
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void retainsTheFullStackAcrossActiveAndPendingSaveRoundTrips() {
        ItemStack original = new ItemStack(Items.NETHERITE_SWORD);
        original.setDamageValue(123);
        original.set(DataComponents.CUSTOM_NAME, Component.literal("Stored weapon"));
        CompoundTag custom = new CompoundTag();
        custom.putString("external_component_data", "retained");
        original.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        var ops = RegistryOps.create(NbtOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));

        UUID projectile = UUID.randomUUID();
        var active = new GlaiveFlightItem(original, 4, Optional.of(projectile));
        Tag stored = GlaiveFlightItem.CODEC.encodeStart(ops, active).getOrThrow();
        GlaiveFlightItem loaded = GlaiveFlightItem.CODEC.parse(ops, stored).getOrThrow();
        assertEquals(Optional.of(projectile), loaded.projectileId());
        assertEquals(4, loaded.originalSlot());
        assertTrue(ItemStack.matches(original, loaded.stack()));

        Tag pending = GlaiveFlightItem.CODEC.encodeStart(ops, loaded.awaitingDelivery()).getOrThrow();
        GlaiveFlightItem returned = GlaiveFlightItem.CODEC.parse(ops, pending).getOrThrow();
        assertTrue(returned.projectileId().isEmpty());
        assertEquals(4, returned.originalSlot());
        assertTrue(ItemStack.matches(original, returned.stack()));
    }
}
