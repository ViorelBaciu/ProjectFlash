package FlashMasService;

import RMI.FlashMasInterface;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class FlashMasService extends UnicastRemoteObject implements FlashMasInterface {
    protected FlashMasService() throws RemoteException{
        super();
    }

    @Override
    public String executeCommand(String command) throws RemoteException {
        // Aici ar trebui să interpretam și să executam comanda în cadrul Flash-MAS
        // Exemplu simplu: returnăm doar comanda primită
        return "Command " + command;
    }
}
