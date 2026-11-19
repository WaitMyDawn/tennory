package yagen.waitmydawn.tennory.glaive;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import yagen.waitmydawn.tennory.Tennory;
import yagen.waitmydawn.tennory.registries.TennoryRegistries;

public abstract class GlaiveWeaponItem extends SwordItem {
    private static final int MAX_USE_TICKS = 72000;
    private static final ResourceLocation REACH_ID = ResourceLocation.fromNamespaceAndPath(Tennory.MODID, "glaive_reach");

    private final int chargeTicks;

    protected GlaiveWeaponItem(Tier tier, Properties properties, double attackDamage, double attackSpeed,
                               double attackReach, int chargeTicks) {
        super(tier, properties.attributes(meleeAttributes(attackDamage, attackSpeed, attackReach)));
        this.chargeTicks = chargeTicks;
    }

    private static ItemAttributeModifiers meleeAttributes(double attackDamage, double attackSpeed, double attackReach) {
        // Weapon stats are totals for a player with vanilla base attributes.
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, additive(BASE_ATTACK_DAMAGE_ID, attackDamage - 1.0), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, additive(BASE_ATTACK_SPEED_ID, attackSpeed - 4.0), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ENTITY_INTERACTION_RANGE, additive(REACH_ID, attackReach - Player.DEFAULT_ENTITY_INTERACTION_RANGE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    private static AttributeModifier additive(ResourceLocation id, double amount) {
        return new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) {
            return InteractionResultHolder.pass(stack);
        }
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                boolean enabled = !detonatesOnImpact(stack);
                if (enabled) {
                    stack.set(TennoryRegistries.GLAIVE_VOLATILE.get(), true);
                } else {
                    stack.remove(TennoryRegistries.GLAIVE_VOLATILE.get());
                }
                player.getInventory().setChanged();
                player.displayClientMessage(Component.translatable("message.tennory.glaive.volatile", modeName(enabled)), true);
            }
            return InteractionResultHolder.consume(stack);
        }
        boolean occupied = level.isClientSide()
                ? player.getExistingData(TennoryRegistries.CLIENT_GLAIVE_IN_FLIGHT).orElse(false)
                : GlaiveFlights.hasStoredWeapon(player);
        if (occupied || !GlaiveReadiness.ready(player)) {
            return InteractionResultHolder.pass(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.tennory.glaive.mode", modeName(detonatesOnImpact(stack)))
                .withStyle(ChatFormatting.GRAY));
    }

    public static boolean detonatesOnImpact(ItemStack stack) {
        return stack.getOrDefault(TennoryRegistries.GLAIVE_VOLATILE.get(), false);
    }

    private static Component modeName(boolean enabled) {
        return Component.translatable(enabled ? "mode.tennory.glaive.volatile" : "mode.tennory.glaive.normal");
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return MAX_USE_TICKS;
    }

    public int chargeTicks() {
        return chargeTicks;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (entity instanceof ServerPlayer player && getUseDuration(stack, entity) - timeLeft >= chargeTicks) {
            GlaiveFlights.launch(player, stack);
        }
    }
}
