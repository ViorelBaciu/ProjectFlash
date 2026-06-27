package net.xqhs.flash.core.node.clientApp;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.LinkedList;
import java.util.List;

import net.xqhs.flash.core.DeploymentConfiguration;
import net.xqhs.flash.core.Entity;
import net.xqhs.flash.core.agent.AgentWave;
import net.xqhs.flash.core.support.AbstractMessagingShard;
import net.xqhs.flash.core.util.MultiTreeMap;

public class RMIMessagingShard extends AbstractMessagingShard {

	/**
	 * Numele atributului pentru host-ul serverului RMI.
	 */
	public static final String RMI_HOST_NAME = "serverHost";

	/**
	 * Numele atributului pentru port-ul serverului RMI.
	 */
	public static final String RMI_PORT_NAME = "serverPort";

	/**
	 * Numele serviciului RMI înregistrat.
	 */
	public static final String RMI_SERVICE_NAME = "NodeService";

	/**
	 * Numele nodului local, folosit pentru identificare.
	 */
	private String localNodeName;

	/**
	 * O listă de host-uri și port-uri pentru celelalte noduri din sistem, preluate
	 * din configurare.
	 */
	private List<NodeLocation> knownNodes = new LinkedList<>();

	@Override
	public boolean configure(MultiTreeMap config) {
		// Obține numele nodului local din configurare
		this.localNodeName = config.getSingleValue(DeploymentConfiguration.NAME_ATTRIBUTE_NAME);

		// Curăță lista de noduri cunoscute pentru a preveni duplicarea
		knownNodes.clear();

		// Parcurge configurația pentru a găsi și a stoca informațiile
		// despre celelalte noduri cu care se poate comunica.
		// Aici presupunem că poți avea o listă de noduri în configurare.
		List<MultiTreeMap> nodes = config.getTrees("node");
		if (nodes != null) {
			for (MultiTreeMap nodeConfig : nodes) {
				String host = nodeConfig.getSingleValue(RMI_HOST_NAME);
				String portStr = nodeConfig.getSingleValue(RMI_PORT_NAME);
				if (host != null && portStr != null) {
					try {
						int port = Integer.parseInt(portStr);
						knownNodes.add(new NodeLocation(nodeConfig.getSingleValue("name"), host, port));
					} catch (NumberFormatException e) {
						System.err.println("Invalid port number for node " + nodeConfig.getSingleValue("name"));
					}
				}
			}
		}

		return true;
	}

	@Override
	public boolean sendMessage(String source, String destination, String content) {
		// Verifică dacă destinatarul este un nod diferit de cel local
		String destNodeName = AgentWave.extractNodeName(destination);

		if (destNodeName.equals(this.localNodeName)) {
			// Dacă mesajul este pentru un agent local, îl gestionezi intern.
			// Aici nu facem nimic, deoarece logica de livrare locală este
			// gestionată de alte componente.
			return true;
		}

		// Caută informațiile de conectare pentru nodul destinatar
		NodeLocation destNodeLocation = knownNodes.stream().filter(node -> node.name.equals(destNodeName)).findFirst()
				.orElse(null);

		if (destNodeLocation == null) {
			System.err.println("Destination node [" + destNodeName + "] not found in configuration.");
			return false;
		}

		try {
			// Conectează-te la registrul RMI de pe nodul destinatar
			Registry registry = LocateRegistry.getRegistry(destNodeLocation.host, destNodeLocation.port);

			// Caută serviciul RMI expus de nodul destinatar
			ClientAppInt remoteNode = (ClientAppInt) registry.lookup(RMI_SERVICE_NAME + "-" + destNodeLocation.port);

			// Trimiterea mesajului la nodul destinatar folosind metoda remotă
			// Nota: "receiveMessage" este o metodă pe care trebuie să o adaugi
			// în NodeInterface și Node.
			System.out.println("Sending message from " + source + " to " + destination + " via RMI.");
			remoteNode.receiveMessage(source, destination, content);

			return true;

		} catch (Exception e) {
			System.err.println("RMI communication error: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}

	@Override
	public boolean addGeneralContext(EntityProxy<? extends Entity<?>> context) {
		return true;
	}

	@Override
	public boolean isRunning() {
		return true;
	}

	@Override
	public boolean start() {
		return true;
	}

	@Override
	public boolean stop() {
		return true;
	}

	/**
	 * Clasă internă pentru a stoca locația unui nod.
	 */
	private static class NodeLocation {
		String name;
		String host;
		int port;

		NodeLocation(String name, String host, int port) {
			this.name = name;
			this.host = host;
			this.port = port;
		}
	}

	@Override
	public String extractAgentAddress(String endpoint) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getAgentAddress() {
		// TODO Auto-generated method stub
		return null;
	}
}
