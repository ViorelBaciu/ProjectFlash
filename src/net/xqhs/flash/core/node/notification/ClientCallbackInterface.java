package net.xqhs.flash.core.node.notification;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ClientCallbackInterface extends Remote {
    void notifyAgentAdded(String agentName) throws RemoteException;
}
