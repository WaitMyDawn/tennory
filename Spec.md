# Tennory platform parity

Forge 1.20.1 uses Java 17, Forge 47.3.22, ForgeGradle 6.0.54, and official 1.20.1 mappings. NeoForge 1.21.1 uses Java 21 and NeoForge 21.1.235. Saves and network protocols are scoped to their Minecraft version.

| Contract | Forge adapter | Runtime evidence | Status |
| --- | --- | --- | --- |
| Mod and item registration | Forge registries retain `tennory:glaive`, `tennory:redeemer`, `tennory:quassus`, and the `items` creative tab | The release server summoned all three registered items. The client resolved their baked models. | passed |
| Glaive launch, detonation, and recovery | Forge player persisted NBT holds the original stack, slot, and projectile UUID. The server reconciles missing projectiles and marks flights pending delivery on logout. | The client received its original stack after detonation and after discarding the projectile. A disconnect during flight followed by reconnect restored exactly one stack with its original NBT. | passed |
| Glaive collision and Volatile mode | Shared collision and range rules, plus item NBT for Volatile mode | Ricochet angles and automatic detonation on impact did not have dedicated runtime assertions. | not_run |
| Volley | Shared chamber rules and 64-block Redeemer / 32-block Quassus maximums. Forge item NBT tracks chamber timers. | Test targets with a leading hitbox edge at 63.9 and 31.9 blocks took volley damage. | passed |
| Area damage | `tennory:glaive_burst` uses distance falloff and block exposure. The vanilla explosion tag is absent. | The test rejected a vanilla explosion source and measured 59.04 damage on that same target from the Glaive burst. | passed |
| Input and configuration | Directional Forge channel for the special key, flight state, and gestures. The blast-radius indicator defaults to off. | The middle-mouse key triggered a server-side detonation. A second client on a dedicated server received the remote player's Glaive throw and Redeemer volley gestures. | passed |
| Loot | Forge global loot modifiers perform one independent roll per Ancient City chest (7.5%) or Monster Room chest (2%) and share the same weapon pool with NeoForge. | Applied the live loot tables to 1,024 seeds each. The bonus appeared 72 times for Ancient City and 20 times for Monster Room. | passed |
| Better Combat | Three attacks per weapon, with ranges of 2.5, 4.0, and 3.4 blocks. Forge uses Better Combat 1.9's attack animation registry. | Better Combat 1.9.0, Player Animator 1.0.2-rc1, and Cloth Config 11.1.136. Nine unique clips played in both camera modes and produced 18 hit events across the two views. The standalone client also passed. | passed |
| Yagens-Attributes | Forge uses Tennory's native damage sources. The optional Yagens-Attributes integration resides in NeoForge. | The updated NeoForge client passed the attack, damage, status, status immunity, and canceled damage scenarios with Yagens-Attributes 2.0.0. | passed |

## Build and runtime evidence

- Root `gradlew --offline --no-daemon --max-workers=1 build` passed for the original NeoForge target and again for the two-target build. The root used Java 21 and dispatched the Forge wrapper with Java 17. Logs are in `build/forge-port/neoforge-baseline.log` and `build/forge-port/verified-build.log`.
- `python -B scripts/verify.py --project . --release` exited zero. It checks the exact release JAR set, bytecode versions, resource contents at their packaged paths, Forge mixin mappings, and byte-for-byte collection from each target. Artifact hashes are in `build/forge-port/release-verification.json`.
- The standalone and Better Combat Forge development clients both reached a disposable world through `runClient`. Verdicts are in `build/forge-port/final-standalone/result.txt` and `build/forge-port/registry-better-combat/result.txt`.
- The reobfuscated Forge JAR booted on an isolated dedicated server, registered the three weapons, accepted two consecutive modded logins, and shut down cleanly. See `build/forge-port/confirmed-release-loopback/console.log` and `build/forge-port/confirmed-reconnect-client/result.txt`.
- The two-client dedicated server run verified remote animation packets in `build/forge-port/tracking-retry-observer/result.txt`. The initial login timed out during the Forge handshake. The client connected on retry and both clients shut down after the gestures were received.
- The NeoForge development client ran the existing special-attack regression probe with Yagens-Attributes. See `build/special-attack-smoke/forge-port-neoforge-regression/result.txt`.
