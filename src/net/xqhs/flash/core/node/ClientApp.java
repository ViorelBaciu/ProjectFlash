package net.xqhs.flash.core.node;

// Required imports
import net.xqhs.flash.core.DeploymentConfiguration;
import net.xqhs.flash.core.DeploymentUtils;
import net.xqhs.flash.core.node.notification.ClientCallbackInterface;
import net.xqhs.flash.core.util.MultiTreeMap;
import net.xqhs.util.logging.UnitComponentExt;

import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

import static net.xqhs.flash.core.DeploymentConfiguration.readCLIArgs;

public class ClientApp extends UnicastRemoteObject implements ClientCallbackInterface, Serializable, Remote {
    private static ClientApp instance;

    protected ClientApp() throws RemoteException {
        super();
    }

    public static synchronized ClientApp getInstance() throws RemoteException {
        if (instance == null) {
            instance = new ClientApp();
        }
        return instance;
    }

    @Override
    public void notifyAgentAdded(String agentName) throws RemoteException {
        System.out.println("Notification: Agent " + agentName + " has been added.");
    }

    public static void main(String[] args) {
        try {


            // Locate the RMI registry
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);

            // Look up the remote object
            NodeInterface stub = (NodeInterface) registry.lookup("Node");

            // Creates and exports the callback object for the client
            ClientApp clientApp = ClientApp.getInstance();
            ClientCallbackInterface callbackStub = (ClientCallbackInterface) clientApp;

            // Server callback registration
            stub.registerCallback(callbackStub);

            // Initialize the base context, root tree, and other necessary structures for ReadCLI
            DeploymentUtils.PublicCtxtTriple baseContext = new DeploymentUtils.PublicCtxtTriple("root", null, null);
            MultiTreeMap rootTree = new MultiTreeMap();
            List<String> autoCreated = new ArrayList<>();
            Map<String, String> name_ids = new HashMap<>();
            UnitComponentExt log = new UnitComponentExt();

            // Process initial command-line arguments using readCLIArgs
            readCLIArgs(Arrays.asList(args).iterator(), baseContext, rootTree, autoCreated, name_ids, log);

            Scanner scanner = new Scanner(System.in);

            while (true) {
                System.out.print("Enter command: ");
                String command = scanner.nextLine().trim();
                System.out.println("Command entered: " + command);

                if (command.equalsIgnoreCase("exit")) {
                    break;
                } else if (command.startsWith("add -agent")) {
                    // Check and parse the command
                    String[] parts = command.split(" ", 5); // split to maximum 5 parts
                    for (int i = 0; i < parts.length; i++) {
                        System.out.println("Part " + i + ": '" + parts[i] + "'");
                    }

                    if (parts.length == 5 && parts[3].equalsIgnoreCase("-shard")) {
                        String agentName = parts[2];
                        String shardName = parts[4];
                        // Use RMI calls to add an agent
                        stub.addAgent(agentName, shardName);
                        System.out.println("Agent added successfully: " + agentName + " to shard: " + shardName);
                    } else {
                        System.out.println("Invalid command format. Usage: add -agent AgentName -shard ShardName");
                    }
                } else if (command.equalsIgnoreCase("list agents")) {
                    // Command to list added agents
                    Map<String, String> agents = stub.getAgentMap();
                    if (agents.isEmpty()) {
                        System.out.println("No agents added.");
                    } else {
                        agents.forEach((agent, shard) ->
                                System.out.println("Agent: " + agent + ", Shard: " + shard));
                    }
                } else {
                    // If the command is not recognized, try processing it with readCLIArgs
                    String[] additionalArgs = command.split(" ");
                    readCLIArgs(Arrays.asList(additionalArgs).iterator(), baseContext, rootTree, autoCreated, name_ids, log);

                    // Additional logic could be added here to handle any specific commands
                    // parsed by readCLIArgs that may have been added during runtime
                    System.out.println("Command processed via ReadCLI. Valid commands: add -agent AgentName -shard ShardName, list agents, exit");
                }
            }

            scanner.close();
        } catch (Exception e) {
            System.err.println("Client exception: " + e.toString());
            e.printStackTrace();
        }

    }
}