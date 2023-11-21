package net.xqhs.flash.core.node;
import net.xqhs.flash.core.node.notification.ClientCallbackInterface;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Map;

public interface NodeInterface extends Remote {
    void addAgent(String agentName, String shardName) throws RemoteException;
    Map<String, String> getAgentMap() throws RemoteException;  // Method to return the map of agents
    void registerCallback(ClientCallbackInterface callback) throws RemoteException;  // Register for notifications
    void unregisterCallback(ClientCallbackInterface callback) throws RemoteException;  // Unregister for notifications
}