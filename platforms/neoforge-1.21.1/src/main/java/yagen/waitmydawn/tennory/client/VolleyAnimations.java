package yagen.waitmydawn.tennory.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
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
import yagen.waitmydawn.tennory.combat.VolleyProfile;
import yagen.waitmydawn.tennory.weapon.FanItem;
import yagen.waitmydawn.tennory.weapon.GunbladeItem;

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
        ((FanGuardRig.Access) event.getRenderer().getModel()).tennory$finishVolleyPose();
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
            drawFirstPersonFan(event, player, arm, companion ? player.getMainHandItem() : held,
                    VolleyMotion.sample(player, event.getPartialTick()), companion);
            event.setCanceled(true);
        } else if (main && held.getItem() instanceof GunbladeItem) {
            GunbladeView.Pose pose = GUN_VIEW.sample(player, event.getPartialTick(), event.getSwingProgress());
            if (pose.aim() > 0.0F) {
                drawFirstPersonGun(event, player, arm, held, pose);
                event.setCanceled(true);
            }
        }
    }

    private static void drawFirstPersonFan(RenderHandEvent event, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                           VolleyMotion.Frame frame, boolean companion) {
        VolleyMotion.FanHand move = frame != null && frame.profile() == VolleyProfile.QUASSUS
                ? frame.fanHand(arm) : VolleyMotion.FanHand.REST;
        float side = viewSide(arm);
        boolean left = arm == HumanoidArm.LEFT;
        float swing = Mth.sin(Mth.sqrt(event.getSwingProgress()) * Mth.PI);
        float x = side * ((left ? 0.81F : 0.69F) + move.lateral() - 0.28F * swing);
        float y = (left ? -0.49F : -0.52F) + move.lift() + 0.14F * swing;
        y -= companion ? 0.0F : 0.55F * event.getEquipProgress();
        float z = (left ? -0.90F : -0.93F) - move.reach() - 0.22F * swing;
        float baseRoll = left ? -92.0F : 3.0F;
        float baseYaw = left ? 34.0F : -27.0F - RIGHT_FAN_YAW_TURN;
        Quaternionf wrist = new Quaternionf().rotationYXZ(
                (baseYaw + side * move.yaw()) * DEG, (10.0F + move.pitch() - 55.0F * swing) * DEG,
                (baseRoll + side * (move.roll() + 31.0F * swing)) * DEG);
        PoseStack poses = event.getPoseStack();
        poses.pushPose();
        poses.translate(x, y, z);
        poses.mulPose(wrist);
        drawWeapon(player, stack, arm, poses, event.getMultiBufferSource(), event.getPackedLight(), 0.72F * FAN_HOLD_SCALE, 0.0F);
        poses.popPose();
    }

    private static void drawFirstPersonGun(RenderHandEvent event, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                           GunbladeView.Pose pose) {
        float aim = pose.aim();
        float recoil = pose.recoil();
        float side = viewSide(arm);
        float swing = event.getSwingProgress();
        float swingRoot = Mth.sqrt(swing) * Mth.PI;
        float swingArc = Mth.sin(swingRoot);
        float x = side * Mth.lerp(aim, 0.56F - 0.4F * swingArc, 0.47F);
        float y = Mth.lerp(aim, -0.52F - 0.60F * event.getEquipProgress() + 0.2F * Mth.sin(2.0F * swingRoot),
                -0.33F + 0.014F * recoil);
        float z = Mth.lerp(aim, -0.72F - 0.2F * Mth.sin(swing * Mth.PI), -0.67F + 0.067F * recoil);
        // Match the vanilla swing at zero blend so the melee handoff has no discontinuity.
        Quaternionf sword = new Quaternionf().rotationYXZ(side * (45.0F - 20.0F * Mth.sin(swing * swing * Mth.PI)) * DEG,
                0.0F, -side * 20.0F * swingArc * DEG)
                .rotateX(-80.0F * swingArc * DEG)
                .rotateY(-85.0F * side * DEG)
                .rotateZ(side * 24.0F * DEG);
        Quaternionf aimed = new Quaternionf().rotationYXZ(side * 13.0F * DEG,
                (-82.0F - 3.0F * recoil) * DEG, 0.0F).rotateY(-side * 34.0F * DEG);
        PoseStack poses = event.getPoseStack();
        poses.pushPose();
        poses.translate(x, y, z);
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
            FanGuardRig rig = ((FanGuardRig.Access) model).tennory$fanGuardRig();
            FanGuardRig.Grip grip = rig == null ? null : rig.grip(arm);
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
            wrist = fanWrist(limbRotation, arm, fanMotion(player, partialTick));
        } else {
            wrist = limbRotation.rotateX(-Mth.HALF_PI).rotateY(Mth.PI - side * Mth.HALF_PI)
                    .rotateZ(-side * GUN_SWORD_ANGLE * DEG);
            if (player.getMainHandItem().getItem() instanceof GunbladeItem && arm == player.getMainArm()) {
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
                && canPose(player);
    }

    private static boolean canPose(AbstractClientPlayer player) {
        return !player.isFallFlying() && !player.isVisuallySwimming() && !player.isSleeping() && !NinjasAnimationCompat.ownsPose();
    }

    public static BaseOffsets poseModel(PlayerModel<?> model, AbstractClientPlayer player, float walk, FanGuardRig rig) {
        var held = player.getMainHandItem().getItem();
        boolean fan = held instanceof FanItem;
        if (!fan && !(held instanceof GunbladeItem) || !canPose(player)) {
            return null;
        }
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        if (fan) {
            return poseFan(model, player, walk, rig, partialTick);
        }
        GunbladeView.Pose gun = gunPose(player, partialTick);
        if (gun.aim() <= 0.0F) {
            return null;
        }
        BaseOffsets offsets = applyStance(model, VolleyMotion.gunStance(player.getMainArm(), gun.aim(), gun.recoil()), walk);
        boolean right = player.getMainArm() == HumanoidArm.RIGHT;
        poseGunArm(model, right ? model.rightArm : model.leftArm, player.getMainArm(), gun, true);
        if (player.getOffhandItem().isEmpty()) {
            poseGunArm(model, right ? model.leftArm : model.rightArm, player.getMainArm().getOpposite(), gun, false);
        }
        copyWear(model);
        return offsets;
    }

    private static GunbladeView.Pose gunPose(AbstractClientPlayer player, float partialTick) {
        GunbladeView view = player instanceof LocalPlayer ? GUN_VIEW : REMOTE_GUN_VIEWS.computeIfAbsent(player, key -> new GunbladeView());
        boolean swinging = player.swinging && player.swingingArm == InteractionHand.MAIN_HAND;
        return view.sample(player, partialTick, swinging ? 1.0F : 0.0F);
    }

    private static VolleyMotion.Frame fanMotion(AbstractClientPlayer player, float partialTick) {
        VolleyMotion.Frame frame = VolleyMotion.sample(player, partialTick);
        return frame != null && frame.profile() == VolleyProfile.QUASSUS ? frame : null;
    }

    private static BaseOffsets poseFan(PlayerModel<?> model, AbstractClientPlayer player, float walk, FanGuardRig rig,
                                       float partialTick) {
        var frame = fanMotion(player, partialTick);
        var motion = FAN_GUARD_MOTIONS.computeIfAbsent(player, key -> new FanGuardMotion());
        var pose = motion.sample(frame, player.level().getGameTime() + (double) partialTick);
        BaseOffsets offsets = pose.inGuard() ? null : applyStance(model, pose.stance(), walk);
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
        copyWear(model);
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

    private static BaseOffsets applyStance(PlayerModel<?> model, VolleyMotion.Stance stance, float walk) {
        if (model.riding) {
            return null;
        }
        BaseOffsets offsets = new BaseOffsets(model);
        float footWeight = 1.0F - 0.69F * Mth.clamp(walk, 0.0F, 1.0F);
        float yaw = stance.yaw() * DEG;
        float pitch = stance.pitch() * DEG;
        float roll = stance.roll() * DEG;
        Quaternionf turn = new Quaternionf().rotationZYX(roll, yaw, pitch);
        float hipY = (model.rightLeg.y + model.leftLeg.y) * 0.5F;
        offsetAtHips(model.body, turn, hipY, stance);
        offsetAtHips(model.head, turn, hipY, stance);
        offsetAtHips(model.rightArm, turn, hipY, stance);
        offsetAtHips(model.leftArm, turn, hipY, stance);
        model.body.xRot += pitch;
        model.body.yRot += yaw;
        model.body.zRot += roll;
        model.head.zRot += roll * 0.23F;
        braceLeg(model.rightLeg, 1.0F, stance, footWeight);
        braceLeg(model.leftLeg, -1.0F, stance, footWeight);
        return offsets;
    }

    private static void offsetAtHips(ModelPart part, Quaternionf turn, float hipY, VolleyMotion.Stance stance) {
        Vector3f origin = new Vector3f(part.x, part.y - hipY, part.z);
        turn.transform(origin);
        part.setPos(origin.x + stance.lean(), origin.y + hipY + stance.drop(), origin.z + stance.fore());
    }

    private static void braceLeg(ModelPart leg, float side, VolleyMotion.Stance stance, float weight) {
        float lead = side * stance.stride() * weight;
        float spread = weight * (Math.abs(stance.stride()) * 0.46F + stance.drop() * 0.026F);
        leg.xRot += lead;
        leg.yRot += weight * stance.yaw() * DEG * 0.41F - side * spread;
        leg.zRot += side * spread;
        leg.x += stance.lean() * weight * 0.70F;
        leg.z += weight * stance.fore() * 0.46F + lead * 1.6F;
        leg.y += weight * stance.drop() * 0.31F;
    }

    private static void poseFanArm(ModelPart limb, HumanoidArm arm, VolleyMotion.FanHand grip, VolleyMotion.Stance stance) {
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

    private static void copyWear(PlayerModel<?> model) {
        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightPants.copyFrom(model.rightLeg);
        model.leftPants.copyFrom(model.leftLeg);
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
            drawMuzzleFlash(poses.last(), buffers.getBuffer(RenderType.lightning()), flash);
        }
    }

    private static void drawMuzzleFlash(PoseStack.Pose pose, VertexConsumer vertices, float amount) {
        float radius = 0.13F * amount;
        float tip = GUN_MUZZLE + 0.27F * amount;
        float center = GUN_MUZZLE + 0.03F;
        for (int face = 0; face < 4; face++) {
            float angle = face * Mth.HALF_PI * 0.5F;
            float x = Mth.cos(angle) * radius;
            float z = Mth.sin(angle) * radius;
            vertices.addVertex(pose, 0.0F, GUN_MUZZLE - 0.04F, 0.0F).setColor(0xFFFFE4A4);
            vertices.addVertex(pose, x, center, z).setColor(0xEFFF9C43);
            vertices.addVertex(pose, 0.0F, tip, 0.0F).setColor(0xFFFFF0CE);
            vertices.addVertex(pose, -x, center, -z).setColor(0xEFFF9C43);
        }
    }

    // Vanilla resets the remaining pose fields on every setupAnim call.
    public record BaseOffsets(float bodyX, float bodyZ, float bodyRoll, float headX, float headZ, float headRoll,
                              float rightHip, float leftHip) {
        BaseOffsets(PlayerModel<?> model) {
            this(model.body.x, model.body.z, model.body.zRot, model.head.x, model.head.z, model.head.zRot,
                    model.rightLeg.x, model.leftLeg.x);
        }

        public void restore(PlayerModel<?> model) {
            model.body.x = bodyX;
            model.body.z = bodyZ;
            model.body.zRot = bodyRoll;
            model.head.x = headX;
            model.head.z = headZ;
            model.head.zRot = headRoll;
            model.rightLeg.x = rightHip;
            model.leftLeg.x = leftHip;
        }
    }
}
