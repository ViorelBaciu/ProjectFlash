package FlashMasService;

import RMI.FlashMasInterface;

import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;

public class FlashMasServer {
    public static void main(String[] args) {
        try{
            //Initializarea serviciului Flash-Mas
            FlashMasInterface flashMas = new FlashMasService();

            // Pornirea unui registry RMI pe portul 1099
            LocateRegistry.createRegistry(1099);

            // Inregistrarea serviciului in registry
            Naming.rebind("rmi://localhost/FlashMas", flashMas);

            System.out.println("Serverul Flash-Mas is running and waiting command");
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
