package yagen.waitmydawn.tennory.weapon;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import yagen.waitmydawn.tennory.Tennory;
import yagen.waitmydawn.tennory.combat.VolleyProfile;
import yagen.waitmydawn.tennory.combat.WeaponStats;

public final class RedeemerItem extends GunbladeItem {
    private static final ResourceLocation SHOT = ResourceLocation.fromNamespaceAndPath(Tennory.MODID, "redeemer_shot");

    public RedeemerItem(Properties properties) {
        super(properties, WeaponStats.REDEEMER, VolleyProfile.REDEEMER,
                new ShotSound(SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 2.0F, 0.0F));
    }

    @Override
    protected ResourceLocation specialAttack(int chamber) {
        return SHOT;
    }
}
