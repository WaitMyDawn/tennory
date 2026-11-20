package yagen.waitmydawn.tennory.weapon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import yagen.waitmydawn.tennory.combat.Chambers;
import yagen.waitmydawn.tennory.combat.VolleyProfile;
import yagen.waitmydawn.tennory.combat.WeaponStats;
import yagen.waitmydawn.tennory.network.WeaponPayloads;

public abstract class VolleyWeaponItem extends MeleeWeaponItem {
    private static final String CHAMBERS_TAG = "tennory:volley_chambers";
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
        CompoundTag tag = stack.getTag();
        Chambers chambers = tag == null || !tag.contains(CHAMBERS_TAG) ? Chambers.EMPTY
                : CHAMBERS_CODEC.parse(NbtOps.INSTANCE, tag.get(CHAMBERS_TAG)).result().orElse(Chambers.EMPTY);
        int count = profile.chamberCount();
        int chamber = chambers.readyChamber(count, now);
        if (chamber < 0) {
            player.displayClientMessage(Component.translatable("message.tennory.volley.recharging"), true);
            return;
        }
        double damage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        VolleyProjectile volley = new VolleyProjectile(player, profile, chamber, damage, specialMultiplier, shotSound);
        if (!player.serverLevel().addFreshEntity(volley) || volley.isRemoved()) {
            return;
        }
        CHAMBERS_CODEC.encodeStart(NbtOps.INSTANCE, chambers.discharge(count, chamber, now + profile.reloadTicks()))
                .result().ifPresent(value -> stack.getOrCreateTag().put(CHAMBERS_TAG, value));
        stack.hurtAndBreak(1, player, owner -> owner.broadcastBreakEvent(InteractionHand.MAIN_HAND));
        player.getInventory().setChanged();
        WeaponPayloads.sendVolleyGesture(player, profile, chamber);
    }

    protected record ShotSound(SoundEvent event, float volume, float pitch, float chamberPitchStep) {
        float pitchFor(int chamber) {
            return pitch + chamber * chamberPitchStep;
        }
    }
}
