package yagen.waitmydawn.tennory.glaive;

import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.neoforged.neoforge.common.SimpleTier;
import yagen.waitmydawn.tennory.combat.GlaiveRules;

public final class GlaiveItem extends GlaiveWeaponItem {
    private static final Tier NETHERITE = Tiers.NETHERITE;
    private static final Tier GLAIVE_TIER = new SimpleTier(
            NETHERITE.getIncorrectBlocksForDrops(), GlaiveRules.DURABILITY, NETHERITE.getSpeed(),
            NETHERITE.getAttackDamageBonus(), NETHERITE.getEnchantmentValue(), NETHERITE::getRepairIngredient);

    public GlaiveItem(Properties properties) {
        super(GLAIVE_TIER, properties.fireResistant(), GlaiveRules.ATTACK_DAMAGE, GlaiveRules.ATTACK_SPEED,
                GlaiveRules.ATTACK_REACH, GlaiveRules.CHARGE_TICKS);
    }
}
