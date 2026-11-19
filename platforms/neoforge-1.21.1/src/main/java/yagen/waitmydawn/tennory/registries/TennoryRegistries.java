package yagen.waitmydawn.tennory.registries;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import yagen.waitmydawn.tennory.Tennory;
import yagen.waitmydawn.tennory.glaive.GlaiveFlightItem;
import yagen.waitmydawn.tennory.glaive.GlaiveItem;
import yagen.waitmydawn.tennory.glaive.GlaiveProjectile;
import yagen.waitmydawn.tennory.glaive.GlaiveReadiness;

public final class TennoryRegistries {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Tennory.MODID);
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Tennory.MODID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Tennory.MODID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Tennory.MODID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Tennory.MODID);

    public static final DeferredItem<GlaiveItem> GLAIVE = ITEMS.registerItem("glaive", GlaiveItem::new);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> GLAIVE_VOLATILE =
            COMPONENTS.register("glaive_volatile", () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());
    public static final DeferredHolder<EntityType<?>, EntityType<GlaiveProjectile>> GLAIVE_PROJECTILE =
            ENTITIES.register("glaive", () -> EntityType.Builder.of(GlaiveProjectile::new, MobCategory.MISC)
                    .sized(0.5F, 0.3F)
                    .fireImmune()
                    .noSave()
                    .noSummon()
                    .clientTrackingRange(6)
                    .updateInterval(1)
                    .build(Tennory.MODID + ":glaive"));

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<GlaiveFlightItem>> STORED_GLAIVE =
            ATTACHMENTS.register("stored_glaive", () -> AttachmentType.builder(() -> GlaiveFlightItem.EMPTY)
                    .serialize(GlaiveFlightItem.CODEC, flight -> !flight.stack().isEmpty())
                    .copyOnDeath()
                    .build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> CLIENT_GLAIVE_IN_FLIGHT =
            ATTACHMENTS.register("client_glaive_in_flight", () -> AttachmentType.builder(() -> false).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<GlaiveReadiness>> GLAIVE_READINESS =
            ATTACHMENTS.register("glaive_readiness", () -> AttachmentType.builder(GlaiveReadiness::new).build());

    private TennoryRegistries() {
    }

    public static void register(IEventBus modBus) {
        TABS.register("items", () -> CreativeModeTab.builder()
                .title(Component.translatable("creativetab.tennory.items"))
                .icon(() -> new ItemStack(GLAIVE.get()))
                .displayItems((parameters, output) -> output.accept(GLAIVE.get()))
                .build());
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        TABS.register(modBus);
        ATTACHMENTS.register(modBus);
        COMPONENTS.register(modBus);
    }
}
