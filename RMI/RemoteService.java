package RMI;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public interface RemoteService extends java.rmi.Remote{
    void displayArguments(String[] args) throws RemoteException;
}
// Implementare a serviciului RMI
class RemoteServiceImpl extends UnicastRemoteObject implements RemoteService {
    protected RemoteServiceImpl() throws  RemoteException {
        super();
    }
    @Override
    public void displayArguments(String[] args) throws  RemoteException
    {
        System.out.println("The message was get: ");
        for (String arg: args){
            System.out.println(arg);
        }
    }
}
// Serverul care sta intr-un ciclu infinit si asteapta argumente prin RMI
  class RMIServer {
    public static void main(String[] args){
        try{
            // Creare obiect de implementare a serviciului RMI
            RemoteServiceImpl remoteService = new RemoteServiceImpl();

            // Publicare serviciu RMI pe un anumit port (in acest exemplu, portul 1099)
            Naming.rebind("rmi://localhost/RemoteService", remoteService);
            System.out.println("Serverul RMI este activ si asteapta argumente...");

            // Stam intr-un ciclu infinit
            while(true) {
                // Doar pentru a mentine serverul activ
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
