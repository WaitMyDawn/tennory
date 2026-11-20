package yagen.waitmydawn.tennory.network;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.player.Player;

/** Transient client animation state belongs to the lifetime of the tracked player instance. */
public final class ClientWeaponState {
    private static final Map<Player, ClientWeaponState> PLAYERS = new WeakHashMap<>();
    private static final ClientWeaponState EMPTY = new ClientWeaponState();

    private boolean activeFlight;
    private long throwTick = Long.MIN_VALUE;
    private WeaponPayloads.VolleyGesture volley;

    private ClientWeaponState() {}

    public static ClientWeaponState get(Player player) { return PLAYERS.getOrDefault(player, EMPTY); }
    public boolean activeFlight() { return activeFlight; }
    public long throwTick() { return throwTick; }
    public WeaponPayloads.VolleyGesture volley() { return volley; }

    public static void setActiveFlight(Player player, boolean active) { live(player).activeFlight = active; }
    public static void setThrowTick(Player player, long start) { live(player).throwTick = start; }
    public static void setVolley(Player player, WeaponPayloads.VolleyGesture volley) { live(player).volley = volley; }

    private static ClientWeaponState live(Player player) {
        return PLAYERS.computeIfAbsent(player, ignored -> new ClientWeaponState());
    }
}
