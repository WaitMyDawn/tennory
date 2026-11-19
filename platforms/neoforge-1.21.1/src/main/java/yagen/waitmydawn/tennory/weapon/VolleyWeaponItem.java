package yagen.waitmydawn.tennory.weapon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import yagen.waitmydawn.tennory.combat.Chambers;
import yagen.waitmydawn.tennory.combat.VolleyProfile;
import yagen.waitmydawn.tennory.combat.WeaponStats;
import yagen.waitmydawn.tennory.network.WeaponPayloads;
import yagen.waitmydawn.tennory.registries.TennoryRegistries;

public abstract class VolleyWeaponItem extends MeleeWeaponItem {
    public static final Codec<Chambers> CHAMBERS_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("next").forGetter(Chambers::next),
            Codec.LONG.listOf().fieldOf("ready_ticks").forGetter(Chambers::readyTicks)
    ).apply(instance, Chambers::new));

    private final VolleyProfile profile;
    private final double specialMultiplier;
    private final ShotSound shotSound;

    protected VolleyWeaponItem(Properties properties, WeaponStats stats, VolleyProfile profile, ShotSound shotSound) {
        super(properties, stats);
        this.profile = profile;
        this.specialMultiplier = stats.specialMultiplier();
        this.shotSound = shotSound;
    }

    public final VolleyProfile profile() {
        return profile;
    }

    protected boolean canFire(Player player, ItemStack weapon) {
        return true;
    }

    public final void fireSpecial(ServerPlayer player, ItemStack stack) {
        if (!canFire(player, stack)) {
            player.displayClientMessage(Component.translatable("message.tennory.volley.free_offhand"), true);
            return;
        }
        long now = player.server.overworld().getGameTime();
        var component = TennoryRegistries.VOLLEY_CHAMBERS.get();
        Chambers chambers = stack.getOrDefault(component, Chambers.EMPTY);
        int count = profile.chamberCount();
        int chamber = chambers.readyChamber(count, now);
        if (chamber < 0) {
            player.displayClientMessage(Component.translatable("message.tennory.volley.recharging"), true);
            return;
        }
        double damage = player.getAttributeValue(Attributes.ATTACK_DAMAGE) * specialMultiplier;
        VolleyProjectile volley = new VolleyProjectile(player, profile, chamber, damage, shotSound);
        if (!player.serverLevel().addFreshEntity(volley) || volley.isRemoved()) {
            return;
        }
        stack.set(component, chambers.discharge(count, chamber, now + profile.reloadTicks()));
        stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        player.getInventory().setChanged();
        WeaponPayloads.sendVolleyGesture(player, profile, chamber);
    }

    protected record ShotSound(SoundEvent event, float volume, float pitch, float chamberPitchStep) {
        float pitchFor(int chamber) {
            return pitch + chamber * chamberPitchStep;
        }
    }
}
