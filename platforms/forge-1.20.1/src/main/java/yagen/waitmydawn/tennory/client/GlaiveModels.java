package yagen.waitmydawn.tennory.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import org.jetbrains.annotations.Nullable;
import yagen.waitmydawn.tennory.Tennory;
import yagen.waitmydawn.tennory.glaive.GlaiveReadiness;

@Mod.EventBusSubscriber(modid = Tennory.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GlaiveModels {
    static final float WORLD_SCALE = 1.35F;
    private static final int DEPLOYMENT_STEPS = 8;
    private static final ModelResourceLocation ITEM = new ModelResourceLocation(id("glaive"), "inventory");

    @SubscribeEvent
    public static void registerAdditional(ModelEvent.RegisterAdditional event) {
        for (int index = 1; index < DEPLOYMENT_STEPS; index++) {
            event.register(frameId(index));
        }
    }

    @SubscribeEvent
    public static void animate(ModelEvent.ModifyBakingResult event) {
        Map<ResourceLocation, BakedModel> models = event.getModels();
        BakedModel original = models.get(ITEM);
        BakedModel[] frames = new BakedModel[DEPLOYMENT_STEPS + 1];
        frames[0] = new HeldFrame(original, null);
        for (int index = 1; index < DEPLOYMENT_STEPS; index++) {
            frames[index] = new HeldFrame(original, models.get(frameId(index)));
        }
        // The item's baked base supplies the full mesh and its atlas bindings for the final held pose.
        frames[DEPLOYMENT_STEPS] = new HeldFrame(original, original);
        models.put(ITEM, new AnimatedItem(original, frames));
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(Tennory.MODID, path);
    }

    private static ResourceLocation frameId(int index) {
        return id("item/glaive_deploy_" + index);
    }

    static void scaleInWorld(PoseStack poses) {
        poses.scale(WORLD_SCALE, WORLD_SCALE, WORLD_SCALE);
    }

    private static final class AnimatedItem extends BakedModelWrapper<BakedModel> {
        private final ItemOverrides overrides;

        private AnimatedItem(BakedModel original, BakedModel[] frames) {
            super(original);
            overrides = new ItemOverrides() {
                @Override
                public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level,
                                          @Nullable LivingEntity entity, int seed) {
                    if (entity instanceof Player player && stack == player.getMainHandItem()) {
                        float partialTick = Minecraft.getInstance().getFrameTime();
                        int index = (int) (GlaiveReadiness.deployment(player, partialTick) * DEPLOYMENT_STEPS);
                        return frames[index];
                    }
                    BakedModel override = original.getOverrides().resolve(original, stack, level, entity, seed);
                    return override == null || override == original ? frames[DEPLOYMENT_STEPS] : override;
                }
            };
        }

        @Override
        public ItemOverrides getOverrides() {
            return overrides;
        }
    }

    private static final class HeldFrame extends BakedModelWrapper<BakedModel> {
        private final @Nullable BakedModel frame;

        private HeldFrame(BakedModel original, @Nullable BakedModel frame) {
            super(original);
            this.frame = frame;
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext context, PoseStack poseStack, boolean leftHand) {
            BakedModel transformed = originalModel.applyTransform(context, poseStack, leftHand);
            if (context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
                scaleInWorld(poseStack);
            }
            return switch (context) {
                case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND ->
                        frame == null ? transformed : frame;
                default -> transformed;
            };
        }
    }
}
