package yagen.waitmydawn.tennory.weapon;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import yagen.waitmydawn.tennory.Tennory;
import yagen.waitmydawn.tennory.combat.VolleyProfile;
import yagen.waitmydawn.tennory.combat.WeaponStats;

public final class QuassusItem extends FanItem {
    private static final List<ResourceLocation> SWEEPS = List.of(
            ResourceLocation.fromNamespaceAndPath(Tennory.MODID, "quassus_first_sweep"),
            ResourceLocation.fromNamespaceAndPath(Tennory.MODID, "quassus_second_sweep"));

    public QuassusItem(Properties properties) {
        super(properties, WeaponStats.QUASSUS, VolleyProfile.QUASSUS);
    }

    @Override
    protected ResourceLocation specialAttack(int chamber) {
        return SWEEPS.get(chamber);
    }
}
