package com.gameflow.rmi;

import com.gameflow.model.GameSession;
import com.gameflow.model.PlayerRequest;
import com.gameflow.model.ServerMetrics;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Remote interface every game-server node exposes. The load balancer
 * talks to game servers exclusively through these RMI stubs.
 */
public interface GameServerRemote extends Remote {

    ServerMetrics getMetrics() throws RemoteException;

    boolean healthCheck() throws RemoteException;

    boolean canAcceptSession() throws RemoteException;

    GameSession createSession(PlayerRequest request) throws RemoteException;

    void terminateSession(String sessionId) throws RemoteException;
}
