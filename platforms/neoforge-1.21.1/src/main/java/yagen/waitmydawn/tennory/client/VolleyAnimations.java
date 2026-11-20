package yagen.waitmydawn.tennory.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import yagen.waitmydawn.tennory.Tennory;
import yagen.waitmydawn.tennory.client.WeaponPlayerPose.BaseOffsets;
import yagen.waitmydawn.tennory.client.WeaponPlayerPose.Stance;
import yagen.waitmydawn.tennory.combat.VolleyProfile;
import yagen.waitmydawn.tennory.weapon.FanItem;
import yagen.waitmydawn.tennory.weapon.GunbladeItem;
import yagen.waitmydawn.tennory.weapon.RedeemerItem;

@EventBusSubscriber(modid = Tennory.MODID, value = Dist.CLIENT)
public final class VolleyAnimations {
    private static final float DEG = Mth.DEG_TO_RAD;
    private static final float GUN_MUZZLE = 0.65F;
    private static final float FAN_HOLD_SCALE = 2.0F;
    private static final float RIGHT_FAN_YAW_TURN = 60.0F;
    private static final float GUN_SWORD_ANGLE = 6.0F;
    private static final GunbladeView GUN_VIEW = new GunbladeView();
    private static final Map<AbstractClientPlayer, GunbladeView> REMOTE_GUN_VIEWS = new WeakHashMap<>();
    private static final Map<AbstractClientPlayer, FanGuardMotion> FAN_GUARD_MOTIONS = new WeakHashMap<>();

    private VolleyAnimations() {
    }

    @SubscribeEvent
    public static void tickGunPoses(ClientTickEvent.Post event) {
        GUN_VIEW.update(Minecraft.getInstance().player);
        REMOTE_GUN_VIEWS.forEach((player, view) -> view.update(player));
        FAN_GUARD_MOTIONS.keySet().removeIf(player -> !(player.getMainHandItem().getItem() instanceof FanItem));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void finishPlayerPose(RenderPlayerEvent.Post event) {
        ((WeaponArmRig.Access) event.getRenderer().getModel()).tennory$finishWeaponPose();
    }

    @SubscribeEvent
    public static void renderFirstPerson(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isScoping()) {
            return;
        }
        boolean main = event.getHand() == InteractionHand.MAIN_HAND;
        HumanoidArm arm = main ? player.getMainArm() : player.getMainArm().getOpposite();
        ItemStack held = player.getItemInHand(event.getHand());
        boolean companion = !main && FanItem.needsCompanion(player) && event.getItemStack().isEmpty();
        if (companion || held.getItem() instanceof FanItem && held.is(event.getItemStack().getItem())) {
            var special = fanMotion(player, event.getPartialTick());
            var combo = special == null ? QuassusBetterCombat.sampleFirstPerson(player, event.getPartialTick()) : null;
            var move = special != null ? special.fanHand(arm) : VolleyMotion.FanHand.REST;
            drawFirstPersonFan(event, player, arm, companion ? player.getMainHandItem() : held, move,
                    combo == null ? null : combo.hand(arm), companion);
            event.setCanceled(true);
        } else if (main && held.getItem() instanceof GunbladeItem) {
            var swing = held.getItem() instanceof RedeemerItem ? BetterCombatSwing.sample(player, event.getPartialTick()) : null;
            var step = swing == null ? null : RedeemerComboStep.byAnimation(swing.animation());
            var combo = step == null ? null : step.sample(swing.progress(), swing.impact());
            GunbladeView.Pose pose = GUN_VIEW.sample(player, event.getPartialTick(), combo == null ? event.getSwingProgress() : 1.0F);
            if (combo != null || pose.aim() > 0.0F) {
                drawFirstPersonGun(event, player, arm, held, pose, combo);
                event.setCanceled(true);
            }
        }
    }

    private static void drawFirstPersonFan(RenderHandEvent event, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                           VolleyMotion.FanHand move, QuassusComboStep.Hand combo, boolean companion) {
        float side = viewSide(arm);
        boolean left = arm == HumanoidArm.LEFT;
        float swing = combo == null ? Mth.sin(Mth.sqrt(event.getSwingProgress()) * Mth.PI) : 0.0F;
        float x = side * ((left ? 0.81F : 0.69F) + move.lateral() - 0.28F * swing);
        float y = (left ? -0.49F : -0.52F) + move.lift() + 0.14F * swing;
        y -= companion ? 0.0F : 0.55F * event.getEquipProgress();
        float z = (left ? -0.90F : -0.93F) - move.reach() - 0.22F * swing;
        float baseRoll = left ? -92.0F : 3.0F;
        float baseYaw = left ? 34.0F : -27.0F - RIGHT_FAN_YAW_TURN;
        Quaternionf wrist = new Quaternionf().rotationYXZ(
                (baseYaw + side * move.yaw()) * DEG, (10.0F + move.pitch() - 55.0F * swing) * DEG,
                (baseRoll + side * (move.roll() + 31.0F * swing)) * DEG);
        if (combo != null) {
            x += side * combo.lateral();
            y += combo.lift();
            z -= combo.reach();
            combo.aim(wrist, side);
        }
        PoseStack poses = event.getPoseStack();
        poses.pushPose();
        poses.translate(x, y, z);
        poses.mulPose(wrist);
        drawWeapon(player, stack, arm, poses, event.getMultiBufferSource(), event.getPackedLight(), 0.72F * FAN_HOLD_SCALE, 0.0F);
        poses.popPose();
    }

    private static void drawFirstPersonGun(RenderHandEvent event, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                           GunbladeView.Pose pose, MeleeViewPose combo) {
        float aim = pose.aim();
        float recoil = pose.recoil();
        float side = viewSide(arm);
        float swing = event.getSwingProgress();
        float swingRoot = Mth.sqrt(swing) * Mth.PI;
        float swingArc = Mth.sin(swingRoot);
        float x = combo == null ? 0.56F - 0.4F * swingArc : combo.x();
        float y = combo == null ? -0.52F + 0.2F * Mth.sin(2.0F * swingRoot) : combo.y();
        float z = combo == null ? -0.72F - 0.2F * Mth.sin(swing * Mth.PI) : combo.z();
        // Vanilla equip progress drops with attack cooldown. Combo poses already include recovery.
        if (combo == null) {
            y -= 0.60F * event.getEquipProgress();
        }
        Quaternionf sword = combo != null ? combo.rotation(arm)
                : new Quaternionf().rotationYXZ(side * (45.0F - 20.0F * Mth.sin(swing * swing * Mth.PI)) * DEG,
                        0.0F, -side * 20.0F * swingArc * DEG)
                        .rotateX(-80.0F * swingArc * DEG)
                        .rotateY(-85.0F * side * DEG)
                        .rotateZ(side * 24.0F * DEG);
        Quaternionf aimed = new Quaternionf().rotationYXZ(side * 13.0F * DEG,
                (-82.0F - 3.0F * recoil) * DEG, 0.0F).rotateY(-side * 34.0F * DEG);
        PoseStack poses = event.getPoseStack();
        poses.pushPose();
        poses.translate(side * Mth.lerp(aim, x, 0.47F), Mth.lerp(aim, y, -0.33F + 0.014F * recoil),
                Mth.lerp(aim, z, -0.67F + 0.067F * recoil));
        poses.mulPose(sword.slerp(aimed, aim));
        drawWeapon(player, stack, arm, poses, event.getMultiBufferSource(), event.getPackedLight(), 0.65F, pose.flash() * aim);
        poses.popPose();
    }

    public static boolean renderHeld(PlayerModel<?> model, AbstractClientPlayer player, ItemStack stack, HumanoidArm arm,
                                     PoseStack poses, MultiBufferSource buffers, int light) {
        boolean fan = stack.getItem() instanceof FanItem;
        if (!fan && !(stack.getItem() instanceof GunbladeItem)) {
            return false;
        }
        if (NinjasAnimationCompat.ownsPose()) {
            return false;
        }
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        if (fan) {
            WeaponArmRig rig = ((WeaponArmRig.Access) model).tennory$weaponArmRig();
            WeaponArmRig.Grip grip = rig == null ? null : rig.grip(arm);
            if (grip != null) {
                poses.pushPose();
                Vector3f anchor = grip.position();
                poses.translate(anchor.x, anchor.y, anchor.z);
                poses.mulPose(grip.rotation());
                drawWeapon(player, stack, arm, poses, buffers, light, 0.90F * FAN_HOLD_SCALE, 0.0F);
                poses.popPose();
                return true;
            }
        }
        float side = viewSide(arm);
        ModelPart limb = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        Quaternionf limbRotation = rotation(limb);
        Quaternionf wrist;
        float muzzle = 0.0F;
        if (fan) {
            var special = fanMotion(player, partialTick);
            wrist = fanWrist(limbRotation, arm, special);
            if (special == null) {
                QuassusBetterCombat.rotateWrist(player, arm, partialTick, wrist);
            }
        } else {
            wrist = limbRotation.rotateX(-Mth.HALF_PI).rotateY(Mth.PI - side * Mth.HALF_PI)
                    .rotateZ(-side * GUN_SWORD_ANGLE * DEG);
            if (player.getMainHandItem().getItem() instanceof GunbladeItem && arm == player.getMainArm()) {
                if (usesRedeemerMeleePose(player)) {
                    BetterCombatSwing.applyItemRotation(player, arm, partialTick, wrist);
                }
                GunbladeView.Pose gun = gunPose(player, partialTick);
                Quaternionf aimed = new Quaternionf().rotationYXZ(model.head.yRot,
                        -Mth.HALF_PI + model.head.xRot - 4.5F * DEG * gun.recoil(), 0.0F);
                wrist.slerp(aimed, gun.aim());
                muzzle = gun.flash() * gun.aim();
            }
        }
        poses.pushPose();
        model.translateToHand(arm, poses);
        if (fan) {
            poses.translate(0.0F, 0.60F, -0.095F);
        } else {
            poses.translate(-side / 16.0F, 0.625F, -0.145F);
        }
        // Cancel the shoulder rotation after locating the wrist so the gun can point with the head at full aim.
        poses.mulPose(rotation(limb).invert());
        poses.mulPose(wrist);
        drawWeapon(player, stack, arm, poses, buffers, light, fan ? 0.90F * FAN_HOLD_SCALE : 0.88F, muzzle);
        poses.popPose();
        return true;
    }

    public static boolean usesFanGuard(AbstractClientPlayer player) {
        return player.getMainHandItem().getItem() instanceof FanItem
                && !(player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof ArmorItem)
                && WeaponPlayerPose.canPose(player)
                && !usesAnimatedFanPose(player, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));
    }

    public static BaseOffsets poseModel(PlayerModel<?> model, AbstractClientPlayer player, float walk, WeaponArmRig rig) {
        var held = player.getMainHandItem().getItem();
        boolean fan = held instanceof FanItem;
        if (!fan && !(held instanceof GunbladeItem) || !WeaponPlayerPose.canPose(player)) {
            return null;
        }
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        if (fan) {
            return usesAnimatedFanPose(player, partialTick) ? null : poseFan(model, player, walk, rig, partialTick);
        }
        GunbladeView.Pose gun = gunPose(player, partialTick);
        if (gun.aim() <= 0.0F) {
            return null;
        }
        BaseOffsets offsets = WeaponPlayerPose.applyStance(model, VolleyMotion.gunStance(player.getMainArm(), gun.aim(), gun.recoil()), walk);
        boolean right = player.getMainArm() == HumanoidArm.RIGHT;
        poseGunArm(model, right ? model.rightArm : model.leftArm, player.getMainArm(), gun, true);
        if (player.getOffhandItem().isEmpty()) {
            poseGunArm(model, right ? model.leftArm : model.rightArm, player.getMainArm().getOpposite(), gun, false);
        }
        WeaponPlayerPose.copyWear(model);
        return offsets;
    }

    private static GunbladeView.Pose gunPose(AbstractClientPlayer player, float partialTick) {
        GunbladeView view = player instanceof LocalPlayer ? GUN_VIEW : REMOTE_GUN_VIEWS.computeIfAbsent(player, key -> new GunbladeView());
        boolean swinging = player.swinging && player.swingingArm == InteractionHand.MAIN_HAND || usesRedeemerMeleePose(player);
        return view.sample(player, partialTick, swinging ? 1.0F : 0.0F);
    }

    private static boolean usesRedeemerMeleePose(AbstractClientPlayer player) {
        return player.getMainHandItem().getItem() instanceof RedeemerItem && BetterCombatSwing.hasPlayerAnimation(player);
    }

    private static VolleyMotion.Frame fanMotion(AbstractClientPlayer player, float partialTick) {
        VolleyMotion.Frame frame = VolleyMotion.sample(player, partialTick);
        return frame != null && frame.profile() == VolleyProfile.QUASSUS ? frame : null;
    }

    private static boolean usesAnimatedFanPose(AbstractClientPlayer player, float partialTick) {
        // Specials need the segmented guard and wrist poses even during a melee animation's fade-out.
        return QuassusBetterCombat.ownsThirdPersonPose(player) && fanMotion(player, partialTick) == null;
    }

    private static BaseOffsets poseFan(PlayerModel<?> model, AbstractClientPlayer player, float walk, WeaponArmRig rig,
                                       float partialTick) {
        var frame = fanMotion(player, partialTick);
        var motion = FAN_GUARD_MOTIONS.computeIfAbsent(player, key -> new FanGuardMotion());
        var pose = motion.sample(frame, player.level().getGameTime() + (double) partialTick);
        BaseOffsets offsets = pose.inGuard() ? null : WeaponPlayerPose.applyStance(model, pose.stance(), walk);
        if (rig == null) {
            if (frame != null) {
                poseFanArm(model.rightArm, HumanoidArm.RIGHT, frame.fanHand(HumanoidArm.RIGHT), pose.stance());
                poseFanArm(model.leftArm, HumanoidArm.LEFT, frame.fanHand(HumanoidArm.LEFT), pose.stance());
            } else if (model.attackTime <= 0.0F) {
                for (HumanoidArm arm : HumanoidArm.values()) {
                    if (holdsFan(player, arm)) {
                        ModelPart limb = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
                        limb.xRot = arm == HumanoidArm.RIGHT ? -1.45F : -0.67F;
                        limb.zRot = arm == HumanoidArm.RIGHT ? 0.27F : -0.16F;
                    }
                }
            }
        }
        WeaponPlayerPose.copyWear(model);
        if (rig != null) {
            for (HumanoidArm arm : HumanoidArm.values()) {
                if (holdsFan(player, arm)) {
                    ModelPart limb = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
                    float melee = arm == player.getMainArm() ? Mth.sin(Mth.sqrt(model.attackTime) * Mth.PI) : 0.0F;
                    rig.prepare(model, arm, pose.arm(arm), melee, fanWrist(rotation(limb), arm, null));
                }
            }
        }
        return offsets;
    }

    private static boolean holdsFan(AbstractClientPlayer player, HumanoidArm arm) {
        return arm == player.getMainArm() || FanItem.needsCompanion(player) || player.getOffhandItem().getItem() instanceof FanItem;
    }

    private static Quaternionf fanWrist(Quaternionf limbRotation, HumanoidArm arm, VolleyMotion.Frame frame) {
        var move = frame == null ? VolleyMotion.FanHand.REST : frame.fanHand(arm);
        float side = viewSide(arm);
        return limbRotation.rotateYXZ(side * (18.0F + move.yaw() * 0.65F) * DEG, -move.pitch() * DEG,
                ((arm == HumanoidArm.LEFT ? 90.0F : 174.0F) - side * move.roll() * 0.62F) * DEG);
    }

    private static void poseFanArm(ModelPart limb, HumanoidArm arm, VolleyMotion.FanHand grip, Stance stance) {
        float side = viewSide(arm);
        limb.xRot += grip.armPitch() * DEG;
        limb.yRot += (stance.yaw() * 0.38F + side * grip.armYaw()) * DEG;
        limb.zRot += (stance.roll() * 0.35F + side * grip.armRoll()) * DEG;
    }

    private static void poseGunArm(PlayerModel<?> model, ModelPart limb, HumanoidArm arm, GunbladeView.Pose gun,
                                   boolean shootingHand) {
        float aim = gun.aim();
        float side = viewSide(arm);
        limb.xRot = Mth.lerp(aim, limb.xRot, (-79.0F - 4.0F * gun.recoil()) * DEG + model.head.xRot);
        limb.yRot = Mth.lerp(aim, limb.yRot, model.head.yRot - side * (shootingHand ? 12.0F : 29.0F) * DEG);
        limb.zRot = Mth.lerp(aim, limb.zRot, side * (shootingHand ? 5.0F : 17.0F) * DEG);
    }

    private static Quaternionf rotation(ModelPart part) {
        return new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot);
    }

    private static float viewSide(HumanoidArm arm) {
        return arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
    }

    private static void drawWeapon(AbstractClientPlayer player, ItemStack stack, HumanoidArm arm, PoseStack poses,
                                   MultiBufferSource buffers, int light, float scale, float flash) {
        poses.scale(scale, scale, scale);
        Minecraft.getInstance().getItemRenderer().renderStatic(player, stack, ItemDisplayContext.NONE,
                arm == HumanoidArm.LEFT, poses, buffers, player.level(), light, OverlayTexture.NO_OVERLAY, player.getId());
        if (flash > 0.0F) {
            VolleyRenderer.renderMuzzleFlash(poses.last(), buffers, GUN_MUZZLE, flash);
        }
    }
}
