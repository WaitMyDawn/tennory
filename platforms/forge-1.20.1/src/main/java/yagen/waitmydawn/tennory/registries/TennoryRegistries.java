package yagen.waitmydawn.tennory.registries;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import yagen.waitmydawn.tennory.Tennory;
import yagen.waitmydawn.tennory.glaive.GlaiveItem;
import yagen.waitmydawn.tennory.glaive.GlaiveProjectile;
import yagen.waitmydawn.tennory.loot.AppendIndependentTableModifier;
import yagen.waitmydawn.tennory.weapon.QuassusItem;
import yagen.waitmydawn.tennory.weapon.RedeemerItem;
import yagen.waitmydawn.tennory.weapon.VolleyProjectile;

public final class TennoryRegistries {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Tennory.MODID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Tennory.MODID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Tennory.MODID);
    private static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Tennory.MODID);

    public static final RegistryObject<GlaiveItem> GLAIVE = ITEMS.register("glaive", () -> new GlaiveItem(new Item.Properties()));
    public static final RegistryObject<RedeemerItem> REDEEMER = ITEMS.register("redeemer", () -> new RedeemerItem(new Item.Properties()));
    public static final RegistryObject<QuassusItem> QUASSUS = ITEMS.register("quassus", () -> new QuassusItem(new Item.Properties()));
    public static final RegistryObject<EntityType<GlaiveProjectile>> GLAIVE_PROJECTILE = ENTITIES.register("glaive",
            () -> EntityType.Builder.<GlaiveProjectile>of(GlaiveProjectile::new, MobCategory.MISC)
                    .sized(0.5F, 0.3F).fireImmune().noSave().noSummon().clientTrackingRange(6).updateInterval(1)
                    .build(Tennory.MODID + ":glaive"));
    public static final RegistryObject<EntityType<VolleyProjectile>> VOLLEY_PROJECTILE = ENTITIES.register("weapon_volley",
            () -> EntityType.Builder.<VolleyProjectile>of(VolleyProjectile::new, MobCategory.MISC)
                    .sized(0.1F, 0.1F).noSave().noSummon().clientTrackingRange(8).updateInterval(1)
                    .build(Tennory.MODID + ":weapon_volley"));

    private TennoryRegistries() {}

    public static void register(IEventBus modBus) {
        LOOT.register("append_independent_table", () -> AppendIndependentTableModifier.CODEC);
        TABS.register("items", () -> CreativeModeTab.builder()
                .title(Component.translatable("creativetab.tennory.items"))
                .icon(() -> new ItemStack(GLAIVE.get()))
                .displayItems((parameters, output) -> {
                    output.accept(GLAIVE.get());
                    output.accept(REDEEMER.get());
                    output.accept(QUASSUS.get());
                }).build());
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        TABS.register(modBus);
        LOOT.register(modBus);
    }
}
