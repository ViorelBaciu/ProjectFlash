package server;

import java.rmi.server.UnicastRemoteObject;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
public class Server {
    public static void main(String[] args) {
        try{

            // Set hostname for the server using javaProperty
            System.setProperty("java.rmi.server.hostname","127.0.0.1");
            System.out.println(" Server has been started ");
    ProductImpl p1 = new ProductImpl("Laptop", "lenovo laptop", 1240.5);
    ProductImpl p2 = new ProductImpl("Mobile", "Mi mobile", 240.5);
    ProductImpl p3 = new ProductImpl("Power Charger", "lenovo charger", 240.1);
    ProductImpl p4 = new ProductImpl("MotorBike", "Yamaha Biker", 38000.24);

            // Export p1,p2,p3,p4 object using UnicastRemoteObject class
            Product stub1 = (Product) UnicastRemoteObject.exportObject(p1,0);
            Product stub2 = (Product) UnicastRemoteObject.exportObject(p2,0);
            Product stub3 = (Product) UnicastRemoteObject.exportObject(p3,0);
            Product stub4 = (Product) UnicastRemoteObject.exportObject(p4,0);

            // Register the exported class in RMI registry with some name;
            // Client will use that name to get the reference of those exported object.

            // Get the registry to register the object
            Registry registry = LocateRegistry.getRegistry("127.0.0.1", 9100);

            registry.rebind("l", stub1);
            registry.rebind("m", stub2);
            registry.rebind("c", stub3);
            registry.rebind("b", stub4);
            System.out.println("Exported and Binding of objects has been completed");
        } catch (Exception e) {
            System.out.println("Some server error...." + e);
        }
    }
}
