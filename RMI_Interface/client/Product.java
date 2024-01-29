package client;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Product extends Remote {
    // Let's us define API.

    public String getName() throws RemoteException;
    public String getDescription() throws RemoteException;
    public double getPrice() throws RemoteException;
}