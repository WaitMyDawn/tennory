package yagen.waitmydawn.tennory.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

import java.util.Optional;

public final class AppendIndependentTableModifier extends LootModifier {
    public static final MapCodec<AppendIndependentTableModifier> CODEC = RecordCodecBuilder.mapCodec(instance ->
            codecStart(instance)
                    .and(ResourceKey.codec(Registries.LOOT_TABLE).fieldOf("table").forGetter(modifier -> modifier.table))
                    .apply(instance, AppendIndependentTableModifier::new));

    private final ResourceKey<LootTable> table;

    private AppendIndependentTableModifier(LootItemCondition[] conditions, ResourceKey<LootTable> table) {
        super(conditions);
        this.table = table;
    }

    @Override
    @SuppressWarnings("deprecation")
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        context.getResolver().get(Registries.LOOT_TABLE, table).ifPresent(bonusTable -> {
            // Keep bonus draws off the source table's random stream and avoid reapplying global loot modifiers.
            LootContext bonusContext = new LootContext.Builder(context)
                    .withOptionalRandomSource(context.getLevel().getRandomSequence(table.location()))
                    .withQueriedLootTableId(table.location())
                    .create(Optional.empty());
            bonusTable.value().getRandomItemsRaw(bonusContext,
                    LootTable.createStackSplitter(context.getLevel(), generatedLoot::add));
        });
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
