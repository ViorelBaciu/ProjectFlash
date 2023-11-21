package RMI;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface FlashMasInterface extends Remote {
    String executeCommand(String command) throws RemoteException;
}
