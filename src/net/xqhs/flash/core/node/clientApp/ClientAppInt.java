package net.xqhs.flash.core.node.clientApp;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ClientAppInt extends Remote {
	void addAgentTree(String command) throws RemoteException;

	void printAllAgents() throws RemoteException;

	void printAllAgents2() throws RemoteException;
	// List<Integer> getActivePorts() throws RemoteException;

	void addNewAgent(String nodeName, String pylonName, String agentName, String agentType, String agentClass)
			throws RemoteException;
}
