package yagen.waitmydawn.tennory.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfigs {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue SHOW_GLAIVE_BLAST_RADIUS = BUILDER
            .comment("Render the particle ring that marks the Glaive explosion radius.")
            .define("showGlaiveBlastRadius", true);
    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfigs() {
    }
}
