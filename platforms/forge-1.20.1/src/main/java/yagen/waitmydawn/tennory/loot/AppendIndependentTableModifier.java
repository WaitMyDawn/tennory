package yagen.waitmydawn.tennory.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

public final class AppendIndependentTableModifier extends LootModifier {
    public static final Codec<AppendIndependentTableModifier> CODEC = RecordCodecBuilder.create(instance ->
            codecStart(instance).and(instance.group(
                    ResourceLocation.CODEC.fieldOf("table").forGetter(modifier -> modifier.table),
                    Codec.floatRange(0, 1).fieldOf("chance").forGetter(modifier -> modifier.chance)
            )).apply(instance, AppendIndependentTableModifier::new));

    private final ResourceLocation table;
    private final float chance;

    private AppendIndependentTableModifier(LootItemCondition[] conditions, ResourceLocation table, float chance) {
        super(conditions);
        this.table = table;
        this.chance = chance;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        var level = context.getLevel();
        var random = level.getRandomSequence(table);
        if (random.nextFloat() < chance) {
            // Use the bonus table's random sequence and bypass global modifiers on the nested draw.
            long seed = random.nextLong();
            LootContext bonus = new LootContext.Builder(context).withOptionalRandomSeed(seed == 0 ? 1 : seed)
                    .withQueriedLootTableId(table).create(table);
            level.getServer().getLootData().getLootTable(table).getRandomItemsRaw(bonus,
                    LootTable.createStackSplitter(level, generatedLoot::add));
        }
        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() { return CODEC; }
}
