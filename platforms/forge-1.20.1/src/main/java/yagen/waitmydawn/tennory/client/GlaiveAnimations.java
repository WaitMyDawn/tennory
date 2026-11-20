package yagen.waitmydawn.tennory.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import yagen.waitmydawn.tennory.Tennory;
import yagen.waitmydawn.tennory.client.WeaponArmRig.ArmPose;
import yagen.waitmydawn.tennory.client.WeaponPlayerPose.BaseOffsets;
import yagen.waitmydawn.tennory.client.WeaponPlayerPose.Stance;
import yagen.waitmydawn.tennory.glaive.GlaiveReadiness;
import yagen.waitmydawn.tennory.glaive.GlaiveItem;
import yagen.waitmydawn.tennory.glaive.GlaiveWeaponItem;
import yagen.waitmydawn.tennory.network.ClientWeaponState;

@Mod.EventBusSubscriber(modid = Tennory.MODID, value = Dist.CLIENT)
public final class GlaiveAnimations implements IClientItemExtensions {
    private static final Stance WINDUP = new Stance(24.0F, -4.0F, -3.0F, -0.45F, 1.05F, 0.75F, 0.23F);
    private static final Stance FOLLOW_THROUGH = new Stance(-32.0F, 9.0F, 5.0F, 0.45F, 0.95F, -1.10F, -0.23F);
    private static final ArmPose THROWING_DRAW = ArmPose.hinged(
            new Vector3f(-0.32F, 0.76F, 0.56F), new Vector3f(-0.48F, 0.52F, 0.71F),
            new Vector3f(0.0F, -0.92F, 0.39F), new Vector3f(-0.94F, 0.12F, -0.30F));
    private static final ArmPose GUARD_DRAW = ArmPose.hinged(
            new Vector3f(0.24F, 0.18F, -0.95F), new Vector3f(-0.88F, 0.045F, -0.47F),
            new Vector3f(0.0F, 1.0F, 0.0F), new Vector3f(0.0F, 0.0F, 1.0F));
    private static final ArmPose THROWING_CAST = ArmPose.hinged(
            new Vector3f(-0.24F, 0.26F, -0.93F), new Vector3f(0.18F, 0.12F, -0.98F),
            new Vector3f(-0.35F, -0.88F, -0.25F), new Vector3f(-0.78F, 0.28F, 0.59F));
    private static final ArmPose GUARD_CAST = ArmPose.hinged(
            new Vector3f(0.26F, 0.34F, -0.90F), new Vector3f(-0.89F, 0.12F, -0.44F),
            new Vector3f(0.0F, 1.0F, 0.0F), new Vector3f(0.0F, 0.0F, 1.0F));

    @Override
    public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                           float partialTick, float equip, float swing) {
        if (arm != player.getMainArm()
                || (GlaiveReadiness.deployment(player, partialTick) >= 1.0F && equip <= 0.30F || swing > 0.0F)) {
            return false;
        }
        poseWeapon(pose, arm, equip, 0.0F, 0.0F);
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRelease(LivingEntityUseItemEvent.Stop event) {
        if (event.getEntity() instanceof LocalPlayer player && event.getItem().getItem() instanceof GlaiveWeaponItem weapon
                && weapon.getUseDuration(event.getItem()) - event.getDuration() >= weapon.chargeTicks()) {
            ClientWeaponState.setThrowTick(player, player.level().getGameTime());
        }
    }

    @SubscribeEvent
    public static void renderFirstPerson(RenderHandEvent event) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        GlaiveMotion motion = GlaiveMotion.sample(player, event.getPartialTick());
        if (held.isEmpty() && (motion != null
                || ClientWeaponState.get(player).activeFlight())) {
            event.setCanceled(true);
            return;
        }
        if (motion == null && held.getItem() instanceof GlaiveItem
                && GlaiveReadiness.deployment(player, event.getPartialTick()) >= 1.0F && renderCombo(event, player, held)) {
            return;
        }
        // After a catch, the vanilla hand renderer can retain its previous stack until its equip transition finishes.
        if (!(held.getItem() instanceof GlaiveWeaponItem) || motion == null && held == event.getItemStack()) {
            return;
        }
        HumanoidArm arm = player.getMainArm();
        boolean right = arm == HumanoidArm.RIGHT;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        poseWeapon(pose, arm, motion == null ? 0.5F : 0.0F,
                motion == null ? 0.0F : motion.draw(), motion == null ? 0.0F : motion.cast());
        ItemDisplayContext view = right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        client.getItemRenderer().renderStatic(player, held, view, !right, pose, event.getMultiBufferSource(),
                player.level(), event.getPackedLight(), OverlayTexture.NO_OVERLAY, player.getId());
        pose.popPose();
        event.setCanceled(true);
    }

    private static boolean renderCombo(RenderHandEvent event, LocalPlayer player, ItemStack stack) {
        var swing = BetterCombatSwing.sample(player, event.getPartialTick());
        var step = swing == null ? null : GlaiveComboStep.byAnimation(swing.animation());
        if (step == null) {
            return false;
        }
        var view = step.sample(swing.progress(), swing.impact());
        HumanoidArm arm = player.getMainArm();
        boolean left = arm == HumanoidArm.LEFT;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(left ? -view.x() : view.x(), view.y(), view.z());
        pose.mulPose(view.rotation(arm));
        pose.scale(0.70F, 0.70F, 0.70F);
        Minecraft.getInstance().getItemRenderer().renderStatic(player, stack, ItemDisplayContext.NONE, left, pose,
                event.getMultiBufferSource(), player.level(), event.getPackedLight(), OverlayTexture.NO_OVERLAY, player.getId());
        pose.popPose();
        event.setCanceled(true);
        return true;
    }

    private static void poseWeapon(PoseStack pose, HumanoidArm arm, float equip, float draw, float cast) {
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        pose.translate(side * (0.56F + 0.09F * draw - 0.31F * cast), -0.52F - 0.20F * equip - 0.13F * draw + 0.16F * cast,
                -0.72F + 0.15F * draw - 0.35F * cast);
        pose.mulPose(Axis.YP.rotationDegrees(side * (24.0F * draw - 45.0F * cast)));
        pose.mulPose(Axis.XP.rotationDegrees(-17.0F * draw + 23.0F * cast));
        pose.mulPose(Axis.ZP.rotationDegrees(side * (-35.0F * draw + 24.0F * cast)));
    }

    public static boolean usesArmRig(AbstractClientPlayer player) {
        return !player.isPassenger() && sample(player) != null && WeaponPlayerPose.canPose(player)
                && !(player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof ArmorItem);
    }

    public static BaseOffsets poseModel(PlayerModel<?> model, AbstractClientPlayer player, float walk, WeaponArmRig rig) {
        GlaiveMotion motion = sample(player);
        if (motion == null || motion.weight() <= 0.001F || model.riding || !WeaponPlayerPose.canPose(player)) {
            return null;
        }
        HumanoidArm main = player.getMainArm();
        float side = main == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        float phase = motion.cast() / motion.weight();
        Stance stance = Stance.REST.lerp(WINDUP.lerp(FOLLOW_THROUGH, phase).mirror(side), motion.weight());
        BaseOffsets offsets = WeaponPlayerPose.applyStance(model, stance, walk);
        ArmPose throwing = THROWING_DRAW.lerp(THROWING_CAST, phase);
        ArmPose guard = GUARD_DRAW.lerp(GUARD_CAST, phase);
        float vanillaBlend = 1.0F - motion.weight();
        poseArm(model, rig, main, side > 0.0F ? throwing : throwing.mirror(), vanillaBlend);
        poseArm(model, player.getOffhandItem().isEmpty() ? rig : null, main.getOpposite(),
                side > 0.0F ? guard : guard.mirror(), vanillaBlend);
        WeaponPlayerPose.copyWear(model);
        return offsets;
    }

    private static void poseArm(PlayerModel<?> model, WeaponArmRig rig, HumanoidArm hand, ArmPose pose, float vanillaBlend) {
        boolean right = hand == HumanoidArm.RIGHT;
        ModelPart limb = right ? model.rightArm : model.leftArm;
        Quaternionf idle = new Quaternionf().rotationZYX(limb.zRot, limb.yRot, limb.xRot);
        if (rig != null) {
            float side = right ? 1.0F : -1.0F;
            Quaternionf grip = idle.rotateX(-Mth.HALF_PI).rotateY(Mth.PI - side * Mth.HALF_PI)
                    .rotateZ(-side * 32.0F * Mth.DEG_TO_RAD);
            rig.prepare(model, hand, pose, vanillaBlend, grip);
        } else {
            Quaternionf arm = new Quaternionf(pose.upper()).slerp(pose.forearm(), 0.38F).slerp(idle, vanillaBlend);
            WeaponArmRig.setRotation(limb, arm);
        }
    }

    public static boolean renderHeld(PlayerModel<?> model, AbstractClientPlayer player, ItemStack stack, HumanoidArm arm,
                                     PoseStack pose, MultiBufferSource buffers, int light) {
        if (!(stack.getItem() instanceof GlaiveWeaponItem) || arm != player.getMainArm()) {
            return false;
        }
        WeaponArmRig rig = ((WeaponArmRig.Access) model).tennory$weaponArmRig();
        WeaponArmRig.Grip grip = rig == null ? null : rig.grip(arm);
        if (grip == null) {
            return false;
        }
        Vector3f anchor = grip.position();
        pose.pushPose();
        pose.translate(anchor.x, anchor.y, anchor.z);
        pose.mulPose(grip.rotation());
        // The disc's center is below the grip inset; move the inner face clear of the forearm.
        pose.translate(0.0F, -0.065F, 0.075F);
        GlaiveModels.scaleInWorld(pose);
        Minecraft.getInstance().getItemRenderer().renderStatic(player, stack, ItemDisplayContext.NONE,
                arm == HumanoidArm.LEFT, pose, buffers, player.level(), light, OverlayTexture.NO_OVERLAY, player.getId());
        pose.popPose();
        return true;
    }

    private static GlaiveMotion sample(AbstractClientPlayer player) {
        return GlaiveMotion.sample(player, Minecraft.getInstance().getFrameTime());
    }
}
