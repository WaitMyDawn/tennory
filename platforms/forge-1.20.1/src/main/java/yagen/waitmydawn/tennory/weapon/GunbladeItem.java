package yagen.waitmydawn.tennory.weapon;

import yagen.waitmydawn.tennory.combat.VolleyProfile;
import yagen.waitmydawn.tennory.combat.WeaponStats;

public abstract class GunbladeItem extends VolleyWeaponItem {
    protected GunbladeItem(Properties properties, WeaponStats stats, VolleyProfile profile, ShotSound shotSound) {
        super(properties, stats, profile, shotSound);
    }
}
