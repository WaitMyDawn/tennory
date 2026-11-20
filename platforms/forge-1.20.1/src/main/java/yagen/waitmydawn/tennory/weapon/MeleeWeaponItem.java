package yagen.waitmydawn.tennory.weapon;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.ForgeTier;
import yagen.waitmydawn.tennory.combat.WeaponStats;

public abstract class MeleeWeaponItem extends SwordItem {
    private static final Tiers NETHERITE = Tiers.NETHERITE;
    private static final ForgeTier WEAPON_TIER = new ForgeTier(NETHERITE.getLevel(), WeaponStats.DURABILITY,
            NETHERITE.getSpeed(), NETHERITE.getAttackDamageBonus(), NETHERITE.getEnchantmentValue(),
            NETHERITE.getTag(), NETHERITE::getRepairIngredient);
    private static final UUID REACH_ID = UUID.nameUUIDFromBytes("tennory:weapon_reach".getBytes(StandardCharsets.UTF_8));

    private final WeaponStats stats;
    private volatile Multimap<Attribute, AttributeModifier> meleeAttributes;

    protected MeleeWeaponItem(Properties properties, WeaponStats stats) {
        super(WEAPON_TIER, 0, (float) (stats.speed() - 4.0), properties.fireResistant());
        this.stats = stats;
    }

    private Multimap<Attribute, AttributeModifier> createMeleeAttributes() {
        Attribute reach = ForgeMod.ENTITY_REACH.get();
        return ImmutableMultimap.<Attribute, AttributeModifier>builder()
                .put(Attributes.ATTACK_DAMAGE, additive(BASE_ATTACK_DAMAGE_UUID, stats.damage() - 1.0))
                .put(Attributes.ATTACK_SPEED, additive(BASE_ATTACK_SPEED_UUID, stats.speed() - 4.0))
                .put(reach, additive(REACH_ID, stats.reach() - reach.getDefaultValue()))
                .build();
    }

    @Override
    public float getDamage() { return (float) (stats.damage() - 1.0); }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        if (slot != EquipmentSlot.MAINHAND) return super.getDefaultAttributeModifiers(slot);
        var modifiers = meleeAttributes;
        if (modifiers == null) meleeAttributes = modifiers = createMeleeAttributes();
        return modifiers;
    }

    private static AttributeModifier additive(UUID id, double amount) {
        return new AttributeModifier(id, "Tennory weapon modifier", amount, AttributeModifier.Operation.ADDITION);
    }
}
