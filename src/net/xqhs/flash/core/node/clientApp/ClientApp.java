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
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import net.xqhs.flash.FlashBoot;
import net.xqhs.flash.core.agent.AgentWave;
import net.xqhs.flash.core.node.Node;
import net.xqhs.flash.core.support.AbstractMessagingShard;
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
			int initialPort = findNodeConexion();
			if (initialPort == -1) {
				System.out.println("Could not connect to any active node.");
			}
			currentPort = initialPort;
			System.out.println("Connected to NodeService on port: " + currentPort);

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
					// currentNodeBridge.printAllAgents2();
//				
				} else if (command.equalsIgnoreCase("list-agents")) {
					System.out.println("Entities registered on the node:");
					Map<String, String> entities = currentNodeBridge.listEntities();
					if (entities.isEmpty()) {
						System.out.println("No entities found on this node.");
					} else {
						System.out.println("Entities and their status on the node:");

						// Iterate through the Map to print each entity and its status
						for (Map.Entry<String, String> entry : entities.entrySet()) {
							System.out.println("- " + entry.getKey() + " | Status: " + entry.getValue());
						}
					}
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
				} else if (command.startsWith("list of commands")) {
					ClientApp currentApp = new ClientApp(null,null);
					currentApp.printCommand(command);
					
				} else if (command.startsWith("run-boot ")) {
					String dynamicConfg = command.substring("run-boot".length());
					ClientApp tempClientApp = new ClientApp(null, null);
					tempClientApp.deployDynamicConfiguration(dynamicConfg);
					try {
						System.out.println("Waits 10 seconds before continuing with the next command ");
						Thread.sleep(10000);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						System.err.println("The wait has been interrupted");
					}

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
		int[] potentialPorts = { 1099, 1100, 1101, 1102, 1103, 1104, 1105, 1106, 1107, 1108, 1109, 1110 };
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
		List<Integer> activePorts = new ArrayList<>();
		int[] potentialPorts = { 1099, 1100, 1101, 1102, 1103, 1104, 1105, 1106, 1107, 1108, 1109, 1110 };

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
			System.out.print("List of ports that are in use: ");
			activePorts.forEach(p -> System.out.print(p + "; "));
			System.out.println("\nYour current port: " + currentPort);
		}
	}

	public void deployDynamicConfiguration(String dynamicConfigLine) {
		String[] args = dynamicConfigLine.split("\\s+");

		FlashBoot.main(args);
	}

	public void runFlashBootCons(String dynamicConfigLine) {
		try {
			String javaHome = System.getProperty("java.home");
			String javaBin = javaHome + File.separator + "bin" + File.separator + "java";

			String classpath = System.getProperty("java.class.path");
			String mainClass = "net.xqhs.flash.FlashBoot";

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

			System.out.println("A new instance of FlashBoot has been launched in a separate process.");

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

	public static void printCommand(String commands) {

		System.out.println(" ");
		System.out.println(" -agent ");
		System.out.println(" -run-boot ");
		System.out.println(" -run-boot2 ");
		System.out.println(" list of ports ");
		System.out.println(" change port with ");
		System.out.println(" list-agents -> show the entities ");
		System.out.println(" view-agents -> show the configNode ");
		System.out.println(" run-clientApp ");
		System.out.println(" stop-node ");
		System.out.println(" exit ");
		System.out.println(" ");
	}

	public void receiveMessage(String source, String destination, String content) throws RemoteException {
		System.out.println("Message received via RMI from " + source + " for " + destination);

		// Aici trebuie să implementezi logica de direcționare a mesajului către agentul
		// țintă.
		// Poți folosi un mecanism similar cu cel din `WebSocketMessagingShard` sau o
		// metodă
		// internă a nodului. Un exemplu ar fi să apelezi o metodă din clasa `Node`
		// care gestionează livrarea mesajelor interne.

		// Exemplu ipotetic de logica de distribuție:
		AgentWave wave = new AgentWave(content, source, destination);

		// În loc să-l livrezi direct, ar trebui să-l dai mai departe shard-ului de
		// mesagerie
		// sau unei metode din `Node` care se ocupă de asta.

		// Caută shard-ul de mesagerie al nodului tău și livrează-i mesajul.
		// Poate ai un câmp `messagingShard` în clasa `Node` sau `ClientApp`.
		AbstractMessagingShard messagingShard = null; // Obține shard-ul corect

		if (messagingShard != null) {
			messagingShard.signalAgentEvent(wave);
			System.out.println("Message forwarded to local agents.");
		} else {
			System.err.println("Could not find a messaging shard to deliver the message.");
		}
	}

}