package com.gameflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * GameFlow LB — cloud-gaming load balancer simulator.
 *
 * Architecture:
 *   React frontend --REST/WebSocket--> this Spring Boot load balancer
 *        --Java RMI--> simulated game-server nodes (GameServerRemote)
 */
@SpringBootApplication
@EnableScheduling
public class GameFlowApplication {

    public static final long START_TIME = System.currentTimeMillis();

    public static void main(String[] args) {
        // RMI stubs must carry a reachable host address.
        System.setProperty("java.rmi.server.hostname",
                System.getProperty("java.rmi.server.hostname", "127.0.0.1"));
        SpringApplication.run(GameFlowApplication.class, args);
    }

    public static long uptimeSec() {
        return (System.currentTimeMillis() - START_TIME) / 1000;
    }
}
