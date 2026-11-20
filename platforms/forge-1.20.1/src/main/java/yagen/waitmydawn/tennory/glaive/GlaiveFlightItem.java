package yagen.waitmydawn.tennory.glaive;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;

public record GlaiveFlightItem(ItemStack stack, int originalSlot, Optional<UUID> projectileId) {
    public static final GlaiveFlightItem EMPTY = new GlaiveFlightItem(ItemStack.EMPTY, 0, Optional.empty());
    public static final Codec<GlaiveFlightItem> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.CODEC.fieldOf("item").forGetter(GlaiveFlightItem::stack),
            Codec.intRange(0, 8).fieldOf("original_slot").forGetter(GlaiveFlightItem::originalSlot),
            UUIDUtil.CODEC.optionalFieldOf("projectile").forGetter(GlaiveFlightItem::projectileId)
    ).apply(instance, GlaiveFlightItem::new));

    GlaiveFlightItem awaitingDelivery() {
        return new GlaiveFlightItem(stack, originalSlot, Optional.empty());
    }
}
