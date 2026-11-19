package yagen.waitmydawn.tennory.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.lang.ref.WeakReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import yagen.waitmydawn.tennory.Tennory;
import yagen.waitmydawn.tennory.glaive.GlaiveReadiness;
import yagen.waitmydawn.tennory.glaive.GlaiveWeaponItem;

@EventBusSubscriber(modid = Tennory.MODID, value = Dist.CLIENT)
public final class GlaiveAnimations implements IClientItemExtensions {
    public static final EnumProxy<HumanoidModel.ArmPose> CHARGE_POSE = new EnumProxy<>(HumanoidModel.ArmPose.class,
            false, (IArmPoseTransformer) GlaiveAnimations::poseChargeArm);
    private static final float THROW_TICKS = 6.0F;

    private static WeakReference<LocalPlayer> throwingPlayer = new WeakReference<>(null);
    private static int throwTick;

    @Override
    public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        return hand == InteractionHand.MAIN_HAND && isCharging(entity, stack) ? CHARGE_POSE.getValue() : null;
    }

    @Override
    public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                           float partialTick, float equip, float swing) {
        if (arm != player.getMainArm()) {
            return false;
        }
        boolean charging = isCharging(player, stack);
        float release = throwProgress(player, partialTick);
        float deployment = GlaiveReadiness.deployment(player, partialTick);
        if (!charging && release >= 1.0F && (deployment >= 1.0F && equip <= 0.30F || swing > 0.0F)) {
            return false;
        }

        float draw = charging ? chargeProgress(player, stack, partialTick) : 0.0F;
        poseWeapon(pose, arm, equip, draw, release);
        return true;
    }

    private static void poseWeapon(PoseStack pose, HumanoidArm arm, float equip, float draw, float release) {
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        float flick = Mth.sin(release * Mth.PI);
        pose.translate(side * (0.56F - 0.08F * draw - 0.32F * flick),
                -0.52F - 0.20F * equip + 0.16F * draw + 0.12F * flick, -0.72F + 0.12F * draw - 0.30F * flick);
        pose.mulPose(Axis.YP.rotationDegrees(side * (31.0F * draw - 46.0F * flick)));
        pose.mulPose(Axis.XP.rotationDegrees(-17.0F * draw + 32.0F * flick));
        pose.mulPose(Axis.ZP.rotationDegrees(side * (-38.0F * draw + 24.0F * flick)));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRelease(LivingEntityUseItemEvent.Stop event) {
        if (event.getEntity() instanceof LocalPlayer player && event.getItem().getItem() instanceof GlaiveWeaponItem weapon
                && weapon.getUseDuration(event.getItem(), player) - event.getDuration() >= weapon.chargeTicks()) {
            throwingPlayer = new WeakReference<>(player);
            throwTick = player.tickCount;
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
        HumanoidArm arm = player.getMainArm();
        boolean right = arm == HumanoidArm.RIGHT;
        PoseStack pose = event.getPoseStack();
        // The vanilla hand renderer can keep the previous stack until its lowering animation finishes.
        if (held.getItem() instanceof GlaiveWeaponItem && held != event.getItemStack()) {
            pose.pushPose();
            poseWeapon(pose, arm, 0.5F, 0.0F, 1.0F);
            ItemDisplayContext view = right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
            client.getItemRenderer().renderStatic(player, held, view, !right, pose, event.getMultiBufferSource(),
                    player.level(), event.getPackedLight(), OverlayTexture.NO_OVERLAY, player.getId());
            pose.popPose();
            event.setCanceled(true);
            return;
        }

        float progress = throwProgress(player, event.getPartialTick());
        if (progress >= 1.0F || player.isInvisible() || !held.isEmpty()) {
            return;
        }

        float flick = Mth.sin(progress * Mth.PI);
        float side = right ? 1.0F : -1.0F;
        pose.pushPose();
        pose.translate(side * (0.64F - 0.34F * flick), -0.60F + 0.20F * flick, -0.72F - 0.32F * flick);
        pose.mulPose(Axis.YP.rotationDegrees(side * (45.0F + 52.0F * flick)));
        pose.translate(-side, 3.6F, 3.5F);
        pose.mulPose(Axis.ZP.rotationDegrees(side * (120.0F - 20.0F * flick)));
        pose.mulPose(Axis.XP.rotationDegrees(200.0F + 18.0F * flick));
        pose.mulPose(Axis.YP.rotationDegrees(-135.0F * side));
        pose.translate(5.6F * side, 0.0F, 0.0F);
        PlayerRenderer renderer = (PlayerRenderer) client.getEntityRenderDispatcher().getRenderer(player);
        if (right) {
            renderer.renderRightHand(pose, event.getMultiBufferSource(), event.getPackedLight(), player);
        } else {
            renderer.renderLeftHand(pose, event.getMultiBufferSource(), event.getPackedLight(), player);
        }
        pose.popPose();
        event.setCanceled(true);
    }

    private static void poseChargeArm(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        float draw = chargeProgress(entity, entity.getUseItem(), partial);
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        part.xRot = -0.42F + 1.22F * draw;
        part.yRot = side * (-0.30F - 0.52F * draw);
        part.zRot = side * (0.14F + 0.67F * draw);
    }

    private static boolean isCharging(LivingEntity entity, ItemStack stack) {
        return entity.isUsingItem() && entity.getUsedItemHand() == InteractionHand.MAIN_HAND && entity.getUseItem().is(stack.getItem());
    }

    private static float chargeProgress(LivingEntity entity, ItemStack stack, float partialTick) {
        if (stack.getItem() instanceof GlaiveWeaponItem weapon) {
            float fraction = Mth.clamp((entity.getTicksUsingItem() + partialTick) / Math.max(1, weapon.chargeTicks()), 0.0F, 1.0F);
            return fraction * fraction * (3.0F - 2.0F * fraction);
        }
        return 0.0F;
    }

    private static float throwProgress(LocalPlayer player, float partialTick) {
        return player == throwingPlayer.get() ? Mth.clamp((player.tickCount - throwTick + partialTick) / THROW_TICKS, 0.0F, 1.0F) : 1.0F;
    }
}
