package net.xqhs.flash.core.node.clientApp;

import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import net.xqhs.flash.core.DeploymentConfiguration;
import net.xqhs.flash.core.DeploymentConfiguration.CtxtTriple;
import net.xqhs.flash.core.node.Node;
import net.xqhs.flash.core.util.MultiTreeMap;
import net.xqhs.util.logging.UnitComponent;

public class ClientApp extends UnicastRemoteObject implements ClientCallbackInterface, Serializable, Remote {

	
	
	
	private static ClientApp instance;
	// private EntityLoader entityLoader;
	private Node node;
	private MultiTreeMap nodeConfiguration;
	private final Lock lock = new ReentrantLock();

	protected ClientApp(Node node, MultiTreeMap nodeConfiguration) throws RemoteException {
		super();
		this.node = node;
		this.nodeConfiguration = nodeConfiguration;
	}

	public static synchronized ClientApp getInstance(Node node, MultiTreeMap nodeConfiguration) throws RemoteException {
		if (instance == null) {
			instance = new ClientApp(node, nodeConfiguration);
		}
		return instance;
	}

	public MultiTreeMap getRootTree() {
		lock.lock();
		try {
			return nodeConfiguration.getATree("root");
		} finally {
			lock.unlock();
		}
	}

	public void setRootTree(MultiTreeMap rootTree) {
		lock.lock();
		try {
			nodeConfiguration.addAgentToRootTree("root", rootTree, false, true);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void notifyAgentAdded(String agentName) throws RemoteException {
		System.out.println("Notification: Agent " + agentName + " has been added.");
	}

	public void addAgents(String command) {
		try {
			String[] args = command.split("\\s+");
			DeploymentConfiguration deploymentConfig = new DeploymentConfiguration();

			MultiTreeMap rootTree = nodeConfiguration.getATree("root");
			if (rootTree == null) {
				rootTree = new MultiTreeMap();
				setRootTree(rootTree);
			}

			deploymentConfig.readCLIArgs(Arrays.asList(args).iterator(), new CtxtTriple("deployment", null, rootTree),
					rootTree, new LinkedList<>(), new HashMap<>(), new UnitComponent("ClientApp"));

			System.out.println("Agents successfully added in rootTree: " + rootTree);

			System.out.println("Agents successfully added: " + nodeConfiguration);
		} catch (Exception e) {
			System.err.println("Error adding agents: " + e.getMessage());
            e.printStackTrace();
		}
	}

	public void printAllAgents() {

		MultiTreeMap rootTree = nodeConfiguration.getATree("root");

		if (rootTree == null || (rootTree.getSimpleNames().isEmpty() && rootTree.getHierarchicalNames().isEmpty())) {
			System.out.println("There are no Agents in rootTree");
			return;
		}

		System.out.println("Lista agenților existenți în rootTree:\n");

		for (String agentName : rootTree.getSimpleNames()) {
			System.out.println("[Agent] " + agentName);
		}

		for (String agentName : rootTree.getHierarchicalNames()) {
			MultiTreeMap subTree = rootTree.getATree(agentName);
			if (subTree != null) {
				System.out.println("[MainAgent] " + agentName);
				printAgentTree(agentName, subTree, 1);
			}
		}
	}

	private void printAgentTree(String parentName, MultiTreeMap subTree, int level) {
		StringBuilder indent = new StringBuilder();
		for (int i = 0; i < level * 4; i++) {
			indent.append(" ");
		}
		for (String subAgent : subTree.getSimpleNames()) {
			System.out.println(indent + "├── [Sub-Agent] " + subAgent);
		}
		for (String shard : subTree.getHierarchicalNames()) {
			System.out.println(indent + "├── [Shard] " + shard);
			MultiTreeMap deeperTree = subTree.getATree(shard);
			if (deeperTree != null) {
				printAgentTree(shard, deeperTree, level + 1);
			}
		}
	}

	private static ClientAppInt currentNodeBridge;
	private static int currentPort = -1;

	public static void main(String[] args) {
		try {
//            MultiTreeMap nodeConfiguration =  new MultiTreeMap();
//
//            StartServer startServer = new StartServer(nodeConfiguration);
//            Node node = startServer.startServer();

//                nodeConfiguration = node.getNodeConfiguration();
//                ClientApp clientApp = ClientApp.getInstance(node, nodeConfiguration);
			int initialPort = findNodeConexion();
			if (initialPort == -1) {
				System.out.println("Could not connect to any active node.");
			}
			currentPort = initialPort;
			System.out.println("Connected to NodeService on port: " + currentPort);

			// Registry registry = LocateRegistry.getRegistry("localhost", 1099);
//			// remoteNode = (Node) registry.lookup("Node");
//			System.out.println("Connected to RMI registry.");
			// ClientAppInt nodeBridge = (ClientAppInt) registry.lookup("NodeService");

			Scanner scanner = new Scanner(System.in);
			while (true) {
				System.out.print("Enter command: ");
				String command = scanner.nextLine().trim();

				if (command.equalsIgnoreCase("exit")) {
					break;
//				} else if (command.startsWith("-agent")) {
//					currentNodeBridge.addAgentTree(command);
				} else if (command.equalsIgnoreCase("view-agents")) {
					currentNodeBridge.printAllAgents();
				} else if (command.equalsIgnoreCase("show me the list of ports")) {
					showActivePorts();
				} else if (command.equalsIgnoreCase("just show")) {
					currentNodeBridge.printAllAgents2();
//				} else if (command.startsWith("-agent")) {
//					try {
//						String[] parts = command.split(" ");
//						if (parts.length == 4) {
//							String agentName = parts[1];
//							String agentType = parts[2];
//							String agentClass = parts[3];
//							// Apelul metodei addNewAgent din clasa Node
//							currentNodeBridge.addNewAgent(agentName, agentType, agentClass);
//						} else {
//							System.out.println("Comanda 'add-agent' necesita 3 parametri: nume, tip, clasa.");
//						}
//					} catch (Exception e) {
//						System.out.println("Eroare la adaugarea agentului: " + e.getMessage());
//					}
				} else if (command.startsWith("add-agent ")) {
	                try {
	                    String[] parts = command.split(" ");
	                    // Verifică dacă comanda are numărul corect de parametri
	                    if (parts.length == 5) {
	                        String nodeName = parts[1];
	                        String pylonName = parts[2];
	                        String agentName = parts[3];
	                        String agentClass = parts[4]; // Am simplificat pentru că AgentPingPong nu folosește "type"

	                        // Apelul metodei de pe obiectul de la distanță (RMI)
	                        // Atentie: Aici am presupus ca nu mai ai parametrul agentType
							currentNodeBridge.addNewAgent(nodeName, pylonName, agentName, "simple", agentClass);

	                        System.out.println("Agentul '" + agentName + "' a fost adaugat cu succes.");
	                    } else {
	                        System.out.println("Comanda 'add-agent' necesita 4 parametri: <node> <pylon> <nume> <clasa>.");
	                    }
	                } catch (Exception e) {
	                    System.out.println("Eroare la adaugarea agentului: " + e.getMessage());
	                }
	            } else if (command.startsWith("change port with ")) {
					try {
						String[] parts = command.split(" ");
						int newPort = Integer.parseInt(parts[parts.length - 1]);
						connectToNode(newPort);
					} catch (NumberFormatException e) {
						System.out.println("Invalid port number");
					}
				} else {
					System.out.println("Invalid command format. Example: -agent composite:AgentX -shard messaging");
				}
			}
			scanner.close();

		} catch (Exception e) {
			System.err.println("Client exception: " + e.toString());
			e.printStackTrace();
		}
	}

	private static int findNodeConexion() {
		int[] potentialPorts = { 1099, 1100, 1101, 1102, 1103, 1104, 1105, 1106 };
		for (int port : potentialPorts) {
			try {
				Registry registry = LocateRegistry.getRegistry("localhost", port);
				currentNodeBridge = (ClientAppInt) registry.lookup("NodeService-" + port);
				System.out.println("Successfully connected to port: + port");
				return port;
			} catch (Exception e) {
				System.err.println("Failed to connect to port " + port + "Continue to search");
			}
		}
		System.err.println("Could not find any active node to connect to.");
		return -1;
	}

	private static void connectToNode(int port) throws Exception {
		Registry registry = LocateRegistry.getRegistry("localhost", port);
		currentNodeBridge = (ClientAppInt) registry.lookup("NodeService-" + port);
		currentPort = port;
		System.out.println("Connected to NodeService on port: " + currentPort);

	}

	private static void showActivePorts() {
//		if (currentNodeBridge != null) {
//			try {
//				List<Integer> ports = currentNodeBridge.getActivePorts();
//				System.out.print("List of ports that are in use: ");
//				ports.forEach(p -> System.out.print(p + "; "));
//				System.out.println("\nYour current port: " + currentPort);
//			} catch (Exception e) {
//				System.err.println("Could not retrieve active ports" + e.getMessage());
//			}
//		}
		List<Integer> activePorts = new ArrayList<>();
		int[] potentialPorts = { 1099, 1100, 1101, 1102, 1103, 1104, 1105, 1106 };

		for (int port : potentialPorts) {
			try {
				Registry registry = LocateRegistry.getRegistry("localhost", port);
				registry.lookup("NodeService-" + port);
				activePorts.add(port);
			} catch (Exception e) {

			}
		}
		if (activePorts.isEmpty()) {
			System.out.println("No active ports found");
		} else {
			System.out.print("List of ports that are in use");
			activePorts.forEach(p -> System.out.print(p + "; "));
			System.out.println("\nYour current port: " + currentPort);
		}
	}
    
//    public static void main(String[] args) {
//		try {
////            MultiTreeMap nodeConfiguration =  new MultiTreeMap();
////
////            StartServer startServer = new StartServer(nodeConfiguration);
////            Node node = startServer.startServer();
//
////                nodeConfiguration = node.getNodeConfiguration();
////                ClientApp clientApp = ClientApp.getInstance(node, nodeConfiguration);
//			
//			
//			Registry registry = LocateRegistry.getRegistry("localhost", 1099);
//			// remoteNode = (Node) registry.lookup("Node");
//			System.out.println("Connected to RMI registry.");
//			ClientAppInt nodeBridge = (ClientAppInt) registry.lookup("NodeService");
//
//			Scanner scanner = new Scanner(System.in);
//			while (true) {
//				System.out.print("Enter command: ");
//				String command = scanner.nextLine().trim();
//
//				if (command.equalsIgnoreCase("exit")) {
//					break;
//				} else if (command.startsWith("-agent")) {
//					nodeBridge.addAgentTree(command);
//				} else if (command.equalsIgnoreCase("view-agents")) {
//					nodeBridge.printAllAgents();
//				} else if (command.equalsIgnoreCase("show me the list of ports")) {
//			//		showActivePorts();
//				} else if (command.startsWith("change the existing port with port ")) {
//					try {
//						String[] parts = command.splits(" ");
//						int newPort = Integer.parseInt(parts[parts.length -1 ]);
//						connectToNode(newPort);
//					} catch (NumberFormatException e) {
//						System.out.println("Invalid port number");
//					}
//				} else {
//					System.out.println("Invalid command format. Example: -agent composite:AgentX -shard messaging");
//				}
//			}
//			scanner.close();
//
//		} catch (Exception e) {
//			System.err.println("Client exception: " + e.toString());
//			e.printStackTrace();
//		}
//	}
	
	
}