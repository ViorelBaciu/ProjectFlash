package net.xqhs.flash.core.node.clientApp;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Map;

public interface ClientAppInt extends Remote {
	void addAgentTree(String command) throws RemoteException;

	void printAllAgents() throws RemoteException;

	void printAllAgents2() throws RemoteException;

	public boolean stop() throws RemoteException;
	// List<Integer> getActivePorts() throws RemoteException;

	public Map<String, String> listEntities() throws RemoteException;


//	void addNewAgent(String nodeName, String pylonName, String agentName, String agentType, String agentClass)
//			throws RemoteException;
//
//	void activatePingPong(String pylonName, String pingerAgentName, String pongerAgentName, String nodeName);

	/*
	 * void activatePingPong(String nodeName, String pylonName, String
	 * pingerAgentName, String pongerAgentName) throws RemoteException;
	 */
	void receiveMessage(String source, String destination, String content) throws RemoteException;

}
