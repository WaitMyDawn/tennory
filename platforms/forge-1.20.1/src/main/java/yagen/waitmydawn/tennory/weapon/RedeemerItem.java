package yagen.waitmydawn.tennory.weapon;

import net.minecraft.sounds.SoundEvents;
import yagen.waitmydawn.tennory.combat.VolleyProfile;
import yagen.waitmydawn.tennory.combat.WeaponStats;

public final class RedeemerItem extends GunbladeItem {
    public RedeemerItem(Properties properties) {
        super(properties, WeaponStats.REDEEMER, VolleyProfile.REDEEMER,
                new ShotSound(SoundEvents.GENERIC_EXPLODE, 1.0F, 2.0F, 0.0F));
    }
}
