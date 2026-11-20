package yagen.waitmydawn.tennory.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import yagen.waitmydawn.tennory.weapon.FanItem;

final class FanCompanionLayer extends ItemInHandLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    FanCompanionLayer(PlayerRenderer playerRenderer, ItemInHandRenderer itemInHandRenderer) {
        super(playerRenderer, itemInHandRenderer);
    }

    @Override
    public void render(PoseStack poses, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbAmount, float partialTick, float age, float yaw, float pitch) {
        if (!FanItem.needsCompanion(player)) {
            return;
        }
        HumanoidArm arm = player.getMainArm().getOpposite();
        ItemDisplayContext display = arm == HumanoidArm.LEFT ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        renderArmWithItem(player, player.getMainHandItem(), display, arm, poses, buffers, light);
    }
}
