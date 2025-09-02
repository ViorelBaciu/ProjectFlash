package net.xqhs.flash.core.node.clientApp;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import net.xqhs.flash.FlashBoot;
import net.xqhs.flash.core.node.Node;
import net.xqhs.flash.core.util.MultiTreeMap;

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

//	public void addAgents(String command) {
//		try {
//			String[] args = command.split("\\s+");
//			DeploymentConfiguration deploymentConfig = new DeploymentConfiguration();
//
//			MultiTreeMap rootTree = nodeConfiguration.getATree("root");
//			if (rootTree == null) {
//				rootTree = new MultiTreeMap();
//				setRootTree(rootTree);
//			}
//
//			deploymentConfig.readCLIArgs(Arrays.asList(args).iterator(), new CtxtTriple("deployment", null, rootTree),
//					rootTree, new LinkedList<>(), new HashMap<>(), new UnitComponent("ClientApp"));
//
//			System.out.println("Agents successfully added in rootTree: " + rootTree);
//
//			System.out.println("Agents successfully added: " + nodeConfiguration);
//		} catch (Exception e) {
//			System.err.println("Error adding agents: " + e.getMessage());
//            e.printStackTrace();
//		}
//	}

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
				} else if (command.startsWith("-")) {
					currentNodeBridge.addAgentTree(command);
				} else if (command.equalsIgnoreCase("view-agents")) {
					currentNodeBridge.printAllAgents();
				} else if (command.equalsIgnoreCase("list of ports")) {
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
//				} else if (command.startsWith("-agent ")) {
//					try {
//						String[] parts = command.split(" ");
//						if (parts.length >= 5) {
//							String nodeName = parts[1];
//							String pylonName = parts[2];
//							String agentName = parts[3];
////							String agentClass = parts[4];
//							StringBuilder agentArgs = new StringBuilder();
//							for (int i = 4; i < parts.length; i++) {
//								agentArgs.append(parts[i]).append(" ");
//							}
//							String agentClass = agentArgs.toString().trim();
//
//							// Apelul metodei de pe obiectul de la distanță (RMI)
//							// Atentie: Aici am presupus ca nu mai ai parametrul agentType
//
//							// String fullAgentClassPath = "test.simplePingPong." + agentClass;
//
//							currentNodeBridge.addNewAgent(nodeName, pylonName, agentName, "simple", agentClass);
//
//							System.out.println("The agent '" + agentName + "' has been added");
//						} else {
//							System.out.println(
//									"The comand 'add-agent' require 4 parameters: <node> <pylon> <nume> <clasa>");
//
//							// Command
//							// add-agent nodeA local: AgentNou AgentPingPong
//						}
//					} catch (Exception e) {
//						System.out.println("Error at the add of agents: " + e.getMessage());
//					}
	            } else if (command.startsWith("change port with ")) {
					try {
						String[] parts = command.split(" ");
						int newPort = Integer.parseInt(parts[parts.length - 1]);
						connectToNode(newPort);
					} catch (NumberFormatException e) {
						System.out.println("Invalid port number");
					}
				} else if (command.startsWith("activate-pingpong")) {

					// Command
					// activate-pingpong nodeA local: Agent2 Agent1

					// add-agent nodeA local: Agent1 AgentPingPong
					// add-agent nodeA local: Agent2 AgentPingPong

					try {
						String[] parts = command.split(" ");
						if (parts.length == 5) {
							String nodeName = parts[1];
							String pylonName = parts[2];
							String pingerAgentName = parts[3];
							String pongerAgentName = parts[4];

//							currentNodeBridge.activatePingPong(nodeName, pylonName, pingerAgentName, pongerAgentName);

							System.out.println("Ping-pong activation command sent");
						}
					} catch (Exception e) {
						System.err.println("Error processing command: " + e.getMessage());
					}
				} else if (command.startsWith("run-boot ")) {

					String dynamicConfg = command.substring("run-boot".length());
					ClientApp tempClientApp = new ClientApp(null, null);
					tempClientApp.deployDynamicConfiguration(dynamicConfg);

				} else if (command.startsWith("run-boot2")) {
					String dynamicConfig2 = command.substring("run-boot2".length());
					try {
						ClientApp currentAppInstance = new ClientApp(null, null);
						currentAppInstance.runFlashBootCons(dynamicConfig2);
					} catch (RemoteException e) {
						System.err.println("Error at creation of instance of client: " + e.getMessage());
					}

				} else if (command.startsWith("run-clientApp")) {
					String clientArgs = command.substring("new-client".length());
					runClientApp(clientArgs);
				} else if (command.startsWith("stop-node")) {
					try {
						currentNodeBridge.stop();
					} catch (RemoteException e) {
						System.err.println("Error to stop the node");
					}
				} else if (command.startsWith("exit")) {
					break;
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
	public void deployDynamicConfiguration(String dynamicConfigLine) {
		String[] args = dynamicConfigLine.split("\\s+");

		FlashBoot.main(args);
	}

	public void runFlashBootCons(String dynamicConfigLine) {
		try {
			String javaHome = System.getProperty("java.home");
			String javaBin = javaHome + File.separator + "bin" + File.separator + "java";

			// Get the full classpath string from the current JVM.
			String classpath = System.getProperty("java.class.path");

			// The fully qualified class name of the main class.
			String mainClass = "net.xqhs.flash.FlashBoot";

			// Create a list to hold the command and its arguments.
			List<String> command = new ArrayList<>();
			command.add("gnome-terminal");
			command.add("--");
			command.add(javaBin);
			command.add("-cp");
			command.add(classpath);
			command.add(mainClass);

			// Add the dynamic arguments from the user's input.
			String[] dynamicArgs = dynamicConfigLine.split("\\s+");
			command.addAll(Arrays.asList(dynamicArgs));

			// Create the ProcessBuilder with the command list.
			ProcessBuilder processBuilder = new ProcessBuilder(command);

			// Redirect the new process's I/O to the current console.
			// processBuilder.inheritIO();

			// Optional but recommended: set the working directory to the project's root.
			File projectRoot = new File("/home/viorel/eclipse-workspace/FlashMultiAgentSystem");
			processBuilder.directory(projectRoot);

			// Start the new process.
			Process process = processBuilder.start();

			System.out.println("A new instance of FlashBoot has been launched in a separate process.");
			System.out.println("The process is running with classpath: " + classpath);
		} catch (IOException e) {
			System.err.println("Error of the new process: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	public static void runClientApp(String dynamicConfigLine) {
		try {
			String javaHome = System.getProperty("java.home");
			String javaBin = javaHome + File.separator + "bin" + File.separator + "java";

			String classpath = System.getProperty("java.class.path");

			String mainClass = "net.xqhs.flash.core.node.clientApp.ClientApp";

			List<String> command = new ArrayList<>();
			command.add("gnome-terminal");
			command.add("--");
			command.add(javaBin);
			command.add("-cp");
			command.add(classpath);
			command.add(mainClass);

			String[] dynamicArgs = dynamicConfigLine.split("\\s+");
			command.addAll(Arrays.asList(dynamicArgs));

			ProcessBuilder processBuilder = new ProcessBuilder(command);

			File projectRoot = new File("/home/viorel/eclipse-workspace/FlashMultiAgentSystem");
			processBuilder.directory(projectRoot);

			Process process = processBuilder.start();
			System.out.println("A new instance of ClientApp has been launched in a separate terminal.");

		} catch (Exception e) {
			System.err.println("" + e.getMessage());
			e.printStackTrace();
		}
	}

}