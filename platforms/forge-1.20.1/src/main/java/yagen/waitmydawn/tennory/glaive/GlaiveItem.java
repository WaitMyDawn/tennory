package yagen.waitmydawn.tennory.glaive;

import yagen.waitmydawn.tennory.combat.GlaiveRules;
import yagen.waitmydawn.tennory.combat.WeaponStats;

public final class GlaiveItem extends GlaiveWeaponItem {
    public GlaiveItem(Properties properties) {
        super(properties, WeaponStats.GLAIVE, GlaiveRules.CHARGE_TICKS);
    }
}
