package RMI;

import java.rmi.Naming;

// Client care trimite argumentele catre server prin RMI
public class RMIClient {
    public static void main(String[] args) {
        try{
            // Localizare serviciu RMI pe server
            RemoteService remoteService = (RemoteService) Naming.lookup("rmi://localhost/RemoteService");

            // Trimitem argumentele catre server
            remoteService.displayArguments(args);

            System.out.println("Argumentele au fost trimise cu success catre server.");
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
