package yagen.waitmydawn.tennory.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ClientConfigs {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue SHOW_GLAIVE_BLAST_RADIUS = BUILDER
            .comment("Render the particle ring that marks the Glaive explosion radius.")
            .define("showGlaiveBlastRadius", false);
    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private ClientConfigs() {
    }
}
