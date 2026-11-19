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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import org.jetbrains.annotations.Nullable;
import yagen.waitmydawn.tennory.Tennory;
import yagen.waitmydawn.tennory.glaive.GlaiveReadiness;

@EventBusSubscriber(modid = Tennory.MODID, value = Dist.CLIENT)
public final class GlaiveModels {
    private static final int DEPLOYMENT_STEPS = 8;
    private static final ModelResourceLocation ITEM = ModelResourceLocation.inventory(id("glaive"));

    @SubscribeEvent
    public static void registerAdditional(ModelEvent.RegisterAdditional event) {
        for (int index = 1; index < DEPLOYMENT_STEPS; index++) {
            event.register(frameId(index));
        }
    }

    @SubscribeEvent
    public static void animate(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        BakedModel original = models.get(ITEM);
        BakedModel[] frames = new BakedModel[DEPLOYMENT_STEPS + 1];
        frames[0] = original;
        for (int index = 1; index < DEPLOYMENT_STEPS; index++) {
            frames[index] = new HeldFrame(original, models.get(frameId(index)));
        }
        // The item's baked base supplies the full mesh and its atlas bindings for the final held pose.
        frames[DEPLOYMENT_STEPS] = new HeldFrame(original, original);
        models.put(ITEM, new AnimatedItem(original, frames));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Tennory.MODID, path);
    }

    private static ModelResourceLocation frameId(int index) {
        return ModelResourceLocation.standalone(id("item/glaive_deploy_" + index));
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
                        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
                        int index = (int) (GlaiveReadiness.deployment(player, partialTick) * DEPLOYMENT_STEPS);
                        return frames[index];
                    }
                    BakedModel override = original.getOverrides().resolve(original, stack, level, entity, seed);
                    return override == null ? original : override;
                }
            };
        }

        @Override
        public ItemOverrides getOverrides() {
            return overrides;
        }
    }

    private static final class HeldFrame extends BakedModelWrapper<BakedModel> {
        private final BakedModel frame;

        private HeldFrame(BakedModel original, BakedModel frame) {
            super(original);
            this.frame = frame;
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext context, PoseStack poseStack, boolean leftHand) {
            BakedModel transformed = originalModel.applyTransform(context, poseStack, leftHand);
            return switch (context) {
                case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> frame;
                default -> transformed;
            };
        }
    }
}
