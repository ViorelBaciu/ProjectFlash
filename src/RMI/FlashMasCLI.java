package RMI;

import java.rmi.Naming;

public class FlashMasCLI {
    public static void main(String[] args) {
        try{
            // Get reference to Flash-Mas via RMI
            FlashMasInterface flashMas = (FlashMasInterface) Naming.lookup("rmi://localhost/FlashMas");

            // Trimiterea comenzii si afisarea raspunsului
            String command = "test";
            String response = flashMas.executeCommand(command);
            System.out.println("Response from Flash-Mas: " + response);
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
