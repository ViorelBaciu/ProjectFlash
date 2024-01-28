package peerTopeer;



import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import javax.json.JsonObject;
import javax.json.Json;

public class PeerThread extends Thread{
    private BufferedReader bufferedReader;
    public PeerThread(Socket socket) throws IOException{
        bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }
    public void run(){
        boolean flag = true;
        while(flag){
            try{
                JsonObject jsonObject = Json.createReader(bufferedReader).readObject();
                if(jsonObject.containsKey("username"))
                    System.out.println("[" + jsonObject.getString("username")+"]: " +jsonObject.getString("messaging"));
            } catch (Exception e){
                flag = false;
                interrupt();
            }
        }
    }
}
