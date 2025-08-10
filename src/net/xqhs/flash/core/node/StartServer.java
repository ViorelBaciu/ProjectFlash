package net.xqhs.flash.core.node;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import net.xqhs.flash.core.util.MultiTreeMap;


public class StartServer {

    private MultiTreeMap nodeConfiguration;

    public StartServer(MultiTreeMap nodeConfiguration) {
        this.nodeConfiguration = nodeConfiguration;
    }

    public Node startServer() {
		int basePort = 1099;
		int maxPortAttempts = 50;

		for (int port = basePort; port < basePort + maxPortAttempts; port++) {

			try {

				Node node = new Node(nodeConfiguration, port);
				Registry registry = LocateRegistry.createRegistry(port);
				registry.rebind("NodeService-" + port, node);

				// Node.addActivePort(port);
				System.out.println("Node server started");

				return node;
			} catch (RemoteException e) {
//                e.printStackTrace();
				System.err.println("Port " + port + " in in use. Trying next ");
			}
		}

		System.err.println("Could not find an available port");
        return null;
    }
}

//public class StartServer {
//
//    private MultiTreeMap nodeConfiguration;
//
//    public StartServer(MultiTreeMap nodeConfiguration) {
//        this.nodeConfiguration = nodeConfiguration;
//    }
//
//    public Node startServer() {
//        try {
//            Node node = new Node(nodeConfiguration);
//
//			Registry registry = LocateRegistry.createRegistry(1099);
//			registry.rebind("NodeService", node);
//
//            System.out.println("Node server started");
//
//            return node;
//        } catch (RemoteException e) {
//            e.printStackTrace();
//        }
//        return null;
//    }
//}
