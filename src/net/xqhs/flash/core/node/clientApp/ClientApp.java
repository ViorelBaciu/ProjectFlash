package net.xqhs.flash.core.node.clientApp;

import net.xqhs.flash.core.*;
import net.xqhs.flash.core.node.EntityLoader;
import net.xqhs.flash.core.node.Node;
import net.xqhs.flash.core.util.MultiTreeMap;
import net.xqhs.flash.core.node.StartServer;
import net.xqhs.util.logging.UnitComponent;
import net.xqhs.flash.core.DeploymentConfiguration.CtxtTriple;


import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ClientApp extends UnicastRemoteObject implements ClientCallbackInterface, Serializable, Remote {
    private static ClientApp instance;
    private EntityLoader entityLoader;
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
    public MultiTreeMap getRootTree(){
        lock.lock();
        try{
            return nodeConfiguration.getATree("root");
        } finally {
            lock.unlock();
        }
    }

    public void setRootTree(MultiTreeMap rootTree){
        lock.lock();
        try{
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

            deploymentConfig.readCLIArgs(
                    Arrays.asList(args).iterator(),
                    new CtxtTriple("deployment", null, rootTree),
                    rootTree,
                    new LinkedList<>(),
                    new HashMap<>(),
                    new UnitComponent("ClientApp")
            );

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

    public static void main(String[] args) {
        try {
            MultiTreeMap nodeConfiguration =  new MultiTreeMap();

            StartServer startServer = new StartServer(nodeConfiguration);
            Node node = startServer.startServer();

            if (node != null) {

                nodeConfiguration = node.getNodeConfiguration();
                ClientApp clientApp = ClientApp.getInstance(node, nodeConfiguration);
                Registry registry = LocateRegistry.getRegistry("localhost", 1099);
                System.out.println("Connected to RMI registry.");

                Scanner scanner = new Scanner(System.in);
                while (true) {
                    System.out.print("Enter command: ");
                    String command = scanner.nextLine().trim();

                    if (command.equalsIgnoreCase("exit")) {
                        break;
                    } else if (command.startsWith("-agent")) {
                        clientApp.addAgents(command);
                    } else if (command.equalsIgnoreCase("view-agents")) {
                        clientApp.printAllAgents();
                    } else {
                        System.out.println("Invalid command format. Example: -agent composite:AgentX -shard messaging");
                    }
                }
                scanner.close();
            } else {
                System.err.println("Failed to start the server. Node is null.");
            }
        } catch (Exception e) {
            System.err.println("Client exception: " + e.toString());
            e.printStackTrace();
        }
    }
}