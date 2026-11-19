package yagen.waitmydawn.tennory;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
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

    public Tennory(IEventBus modBus, ModContainer container) {
        TennoryRegistries.register(modBus);
        modBus.addListener(WeaponPayloads::register);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfigs.SPEC, ModIdentity.CLIENT_CONFIG);
        container.registerConfig(ModConfig.Type.SERVER, ServerConfigs.SPEC, ModIdentity.SERVER_CONFIG);
    }
}
