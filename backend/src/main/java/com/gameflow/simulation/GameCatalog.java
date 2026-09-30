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
        add(new GameProfile("VALORANT", "VALORANT", "Competitive Shooter",
                true, 0.55, 0.45, 0.85, 0.50, "RTX_3080"));
        add(new GameProfile("FORZA_HORIZON_5", "FORZA HORIZON 5", "Arcade Racer",
                true, 0.60, 0.55, 0.60, 0.55, "RTX_3080"));
        add(new GameProfile("WITCHER_3", "WITCHER 3", "Action RPG",
                false, 0.80, 0.75, 0.50, 0.65, "RTX_3080"));
        add(new GameProfile("CYBERPUNK_2077", "CYBERPUNK 2077", "Open-World RPG",
                false, 0.90, 0.85, 0.55, 0.70, "RTX_4090"));
        add(new GameProfile("GTA_VI", "GTA 6", "Open World Action",
                false, 0.95, 0.90, 0.60, 0.75, "RTX_4090"));
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
