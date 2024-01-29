package server;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class ProductImpl implements Product {

    public String name;
    public String description;
    public double price;

    public ProductImpl(String newName, String newDescritpion, double newPrice) throws RemoteException{
        this.name=newName;
        this.description = newDescritpion;
        this.price = newPrice;
    }
    public String getName() throws RemoteException{
        return this.name;
    }
    public String getDescription() throws RemoteException {
        return this.description;
    }
    public double getPrice() throws RemoteException {
        return this.price;
    }

}
