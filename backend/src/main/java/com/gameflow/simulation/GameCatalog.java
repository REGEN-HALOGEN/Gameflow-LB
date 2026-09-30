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
        add(new GameProfile("NEON_STRIKE", "NEON STRIKE", "Competitive Shooter",
                true, 0.55, 0.45, 0.85, 0.50, "RTX_3080"));
        add(new GameProfile("ORBITAL_RACER", "ORBITAL RACER", "Arcade Racer",
                true, 0.60, 0.40, 0.60, 0.55, "RTX_3080"));
        add(new GameProfile("IRON_FRONT", "IRON FRONT", "Open World",
                false, 0.90, 0.85, 0.50, 0.70, "RTX_4090"));
        add(new GameProfile("NIGHT_CITY", "NIGHT CITY", "Open-World RPG",
                false, 0.85, 0.80, 0.55, 0.65, "RTX_4090"));
    }

    private void add(GameProfile profile) {
        games.add(profile);
        byName.put(profile.getName(), profile);
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
