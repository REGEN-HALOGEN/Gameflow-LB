package com.gameflow.simulation;

import com.gameflow.model.GameProfile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seed catalog of game workloads. Different games stress the simulated
 * servers differently (GPU-heavy open world vs latency-sensitive shooter).
 */
@Component
public class GameCatalog {

    private final List<GameProfile> games = new ArrayList<>();
    private final Map<String, GameProfile> byName = new LinkedHashMap<>();

    public GameCatalog() {
        // ── RTX_3050 tier — light games, low-end cloud instances ──────────────
        add(new GameProfile("MINECRAFT", "MINECRAFT", "Sandbox",
                false, 0.22, 0.18, 0.35, 0.40, "RTX_3050"));
        add(new GameProfile("ROCKET_LEAGUE", "ROCKET LEAGUE", "Sports Arena",
                true,  0.30, 0.25, 0.80, 0.45, "RTX_3050"));

        // ── RTX_3070 tier — mid-range competitive titles ───────────────────────
        add(new GameProfile("VALORANT", "VALORANT", "Competitive Shooter",
                true,  0.42, 0.38, 0.90, 0.50, "RTX_3070"));
        add(new GameProfile("APEX_LEGENDS", "APEX LEGENDS", "Battle Royale",
                true,  0.58, 0.52, 0.82, 0.60, "RTX_3070"));
        add(new GameProfile("FORTNITE", "FORTNITE", "Battle Royale",
                true,  0.52, 0.47, 0.78, 0.55, "RTX_3070"));

        // ── RTX_3080 tier — high-fidelity AAA, no ray tracing ─────────────────
        add(new GameProfile("FORZA_HORIZON_5", "FORZA HORIZON 5", "Racing Sim",
                true,  0.72, 0.65, 0.60, 0.58, "RTX_3080"));
        add(new GameProfile("GOD_OF_WAR", "GOD OF WAR", "Action Adventure",
                false, 0.78, 0.72, 0.42, 0.68, "RTX_3080"));
        add(new GameProfile("RED_DEAD_2", "RED DEAD REDEMPTION 2", "Open World",
                false, 0.82, 0.78, 0.48, 0.72, "RTX_3080"));
        add(new GameProfile("WITCHER_3", "WITCHER 3", "Action RPG",
                false, 0.75, 0.70, 0.45, 0.65, "RTX_3080"));

        // ── RTX_4090 tier — maxed-out ray tracing, flagship AAA ───────────────
        add(new GameProfile("CYBERPUNK_2077", "CYBERPUNK 2077", "Open-World RPG",
                false, 0.92, 0.88, 0.55, 0.72, "RTX_4090"));
        add(new GameProfile("ALAN_WAKE_2", "ALAN WAKE 2", "Survival Horror",
                false, 0.94, 0.90, 0.40, 0.70, "RTX_4090"));

        // ── RTX_4090_TI tier — next-gen, extreme workloads ────────────────────
        add(new GameProfile("GTA_VI", "GTA 6", "Open World Action",
                false, 0.97, 0.95, 0.65, 0.80, "RTX_4090_TI"));
        add(new GameProfile("STAR_CITIZEN", "STAR CITIZEN", "Space Sim MMO",
                false, 0.98, 0.97, 0.70, 0.85, "RTX_4090_TI"));
    }

    private void add(GameProfile profile) {
        games.add(profile);
        byName.put(profile.getName(), profile);
        byName.put(profile.getId(), profile);
    }

    public List<GameProfile> all() {
        return List.copyOf(games);
    }

    public GameProfile byName(String name) {
        return byName.get(name);
    }

    /** Name -> profile map, handed to game-server impls for load modelling. */
    public Map<String, GameProfile> asMap() {
        return Map.copyOf(byName);
    }
}
