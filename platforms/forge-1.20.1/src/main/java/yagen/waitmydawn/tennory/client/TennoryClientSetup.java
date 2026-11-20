package yagen.waitmydawn.tennory.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;
import yagen.waitmydawn.tennory.Tennory;
import yagen.waitmydawn.tennory.registries.TennoryRegistries;

@Mod.EventBusSubscriber(modid = Tennory.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TennoryClientSetup {
    static final KeyMapping WEAPON_SPECIAL = new KeyMapping("key.tennory.weapon_special", KeyConflictContext.IN_GAME,
            InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_MIDDLE, "key.categories.tennory");

    private TennoryClientSetup() {
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(WEAPON_SPECIAL);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(TennoryRegistries.GLAIVE_PROJECTILE.get(), GlaiveRenderer::new);
        event.registerEntityRenderer(TennoryRegistries.VOLLEY_PROJECTILE.get(), VolleyRenderer::new);
    }

    @SubscribeEvent
    public static void addPlayerLayers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            if (!(event.getSkin(skin) instanceof PlayerRenderer renderer)) continue;
            if (renderer != null) {
                renderer.addLayer(new FanCompanionLayer(renderer, event.getContext().getItemInHandRenderer()));
            }
        }
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(BetterCombatSwing::register);
    }
}
