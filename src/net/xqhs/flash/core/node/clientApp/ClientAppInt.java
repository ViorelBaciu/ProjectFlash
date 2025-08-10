package net.xqhs.flash.core.node.clientApp;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ClientAppInt extends Remote {
	void addAgentTree(String command) throws RemoteException;

	void printAllAgents() throws RemoteException;
}
