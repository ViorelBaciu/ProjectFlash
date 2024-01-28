package client;

import server.Product;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class Client {
    public static void main(String[] args) {
        try {

            // Locate the registry
            Registry registry = LocateRegistry.getRegistry("127.0.0.1", 9100);

            // Get the reference of exported object from RMI Registry
            Product laptop = (Product) registry.lookup("l");
            Product mobile = (Product) registry.lookup("m");
            Product charger = (Product) registry.lookup("c");
            Product bike = (Product) registry.lookup("b");

            // Now we can invoke the method of the referenced objects
            System.out.println(" The name of the laptop is: " + laptop.getName());
            System.out.println(" The description is: " + laptop.getDescription());
            System.out.println(" The price is: " + laptop.getPrice());

            System.out.println(mobile.getName());
            System.out.println(charger.getName());
            System.out.println(bike.getName());

        } catch (Exception e) {
            System.out.println("Client side error.." + e);
        }
    }
}
