package yagen.waitmydawn.tennory;

import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import yagen.waitmydawn.tennory.config.ClientConfigs;
import yagen.waitmydawn.tennory.config.ServerConfigs;
import yagen.waitmydawn.tennory.core.ModIdentity;
import yagen.waitmydawn.tennory.network.WeaponPayloads;
import yagen.waitmydawn.tennory.registries.TennoryRegistries;

@Mod(Tennory.MODID)
public final class Tennory {
    public static final String MODID = ModIdentity.ID;
    public static final Logger LOGGER = LogUtils.getLogger();

    public Tennory() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext context = ModLoadingContext.get();
        TennoryRegistries.register(modBus);
        WeaponPayloads.register();
        context.registerConfig(ModConfig.Type.CLIENT, ClientConfigs.SPEC, ModIdentity.CLIENT_CONFIG);
        context.registerConfig(ModConfig.Type.SERVER, ServerConfigs.SPEC, ModIdentity.SERVER_CONFIG);
    }
}
