package yagen.waitmydawn.tennory.weapon;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import yagen.waitmydawn.tennory.combat.VolleyProfile;
import yagen.waitmydawn.tennory.combat.WeaponStats;

public abstract class FanItem extends VolleyWeaponItem {
    protected FanItem(Properties properties, WeaponStats stats, VolleyProfile profile) {
        super(properties, stats, profile, new ShotSound(SoundEvents.TRIDENT_THROW.value(), 0.8F, 1.0F, 0.12F));
    }

    @Override
    protected boolean canFire(Player player, ItemStack weapon) {
        ItemStack offhand = player.getOffhandItem();
        return offhand.isEmpty() || offhand.is(weapon.getItem());
    }

    public static boolean needsCompanion(Player player) {
        return player.getMainHandItem().getItem() instanceof FanItem && player.getOffhandItem().isEmpty();
    }
}
