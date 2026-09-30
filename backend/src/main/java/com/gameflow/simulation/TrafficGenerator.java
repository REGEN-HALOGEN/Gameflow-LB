package com.gameflow.simulation;

import com.gameflow.model.GameProfile;
import com.gameflow.model.GameSession;
import com.gameflow.model.PlayerRequest;
import com.gameflow.model.SessionState;
import com.gameflow.session.SessionManager;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Spawns player requests from the seeded cities/games and ends sessions
 * over time. Only ticks while the simulation is RUNNING.
 */
@Component
public class TrafficGenerator {

    private static final String[] CITIES =
            {"Mumbai", "Bangalore", "Delhi", "Singapore", "Chennai", "Hyderabad"};
    private static final String[] RESOLUTIONS = {"720p", "1080p", "1080p", "1440p", "4K"};
    private static final int[] FPS_OPTIONS = {30, 60, 60, 60, 120};

    private final SessionManager sessionManager;
    private final GameCatalog gameCatalog;
    private final Random random = new Random();
    private final AtomicLong playerSeq = new AtomicLong(1041);

    private volatile int targetSessions = 0;

    public TrafficGenerator(SessionManager sessionManager, GameCatalog gameCatalog) {
        this.sessionManager = sessionManager;
        this.gameCatalog = gameCatalog;
    }

    public int getTargetSessions() {
        return targetSessions;
    }

    public void setTargetSessions(int targetSessions) {
        this.targetSessions = Math.max(0, targetSessions);
    }

    public void reset() {
        this.targetSessions = 0;
    }

    public void tick() {
        long active = countActive();
        int deficit = targetSessions - (int) active;

        int toSpawn = 0;
        if (deficit > 0) {
            // Gradual ramp normally, faster catch-up when far behind (surge demo).
            toSpawn = deficit > 15 ? 3 + random.nextInt(4) : Math.min(3, deficit);
        }
        for (int i = 0; i < toSpawn; i++) {
            spawnOne();
        }

        // Sessions end naturally over time (average lifetime of several minutes).
        List<GameSession> snapshot = sessionManager.all();
        for (GameSession session : snapshot) {
            if (session.getState() == SessionState.ACTIVE && random.nextDouble() < 0.0025) {
                sessionManager.terminateSession(session.getId());
            }
        }
    }

    private long countActive() {
        return sessionManager.all().stream()
                .filter(s -> s.getState() == SessionState.ACTIVE
                        || s.getState() == SessionState.CREATING
                        || s.getState() == SessionState.MIGRATING)
                .count();
    }

    private void spawnOne() {
        String city = CITIES[random.nextInt(CITIES.length)];
        List<GameProfile> games = gameCatalog.all();
        GameProfile game = games.get(random.nextInt(games.size()));
        String resolution = RESOLUTIONS[random.nextInt(RESOLUTIONS.length)];
        int fps = resolution.equals("4K") ? 60 : FPS_OPTIONS[random.nextInt(FPS_OPTIONS.length)];
        String playerId = "P-" + playerSeq.incrementAndGet();

        PlayerRequest request = new PlayerRequest(playerId, city, game.getName(), resolution, fps);
        request.setVip(random.nextDouble() < 0.15); // 15% VIP
        sessionManager.createSession(request);
    }
}
