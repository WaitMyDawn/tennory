package yagen.waitmydawn.tennory.weapon;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.common.SimpleTier;
import yagen.waitmydawn.tennory.Tennory;
import yagen.waitmydawn.tennory.combat.WeaponStats;

public abstract class MeleeWeaponItem extends SwordItem {
    private static final Tier NETHERITE = Tiers.NETHERITE;
    private static final Tier WEAPON_TIER = new SimpleTier(
            NETHERITE.getIncorrectBlocksForDrops(), WeaponStats.DURABILITY, NETHERITE.getSpeed(),
            NETHERITE.getAttackDamageBonus(), NETHERITE.getEnchantmentValue(), NETHERITE::getRepairIngredient);
    private static final ResourceLocation REACH_ID = ResourceLocation.fromNamespaceAndPath(Tennory.MODID, "weapon_reach");

    protected MeleeWeaponItem(Properties properties, WeaponStats stats) {
        super(WEAPON_TIER, properties.fireResistant().attributes(meleeAttributes(stats)));
    }

    private static ItemAttributeModifiers meleeAttributes(WeaponStats stats) {
        // Weapon stats are totals for a player with vanilla base attributes.
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, additive(BASE_ATTACK_DAMAGE_ID, stats.damage() - 1.0), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, additive(BASE_ATTACK_SPEED_ID, stats.speed() - 4.0), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ENTITY_INTERACTION_RANGE, additive(REACH_ID, stats.reach() - Player.DEFAULT_ENTITY_INTERACTION_RANGE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    private static AttributeModifier additive(ResourceLocation id, double amount) {
        return new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE);
    }
}
