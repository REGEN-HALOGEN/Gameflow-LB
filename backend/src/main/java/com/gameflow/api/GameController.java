package com.gameflow.api;

import com.gameflow.model.GameProfile;
import com.gameflow.simulation.GameCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameCatalog catalog;

    public GameController(GameCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public List<GameProfile> games() {
        return catalog.all();
    }
}
