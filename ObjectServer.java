import java.io.*;
import java.net.*;

public class ObjectServer {
    public static void main(String[] args) throws Exception {
        ServerSocket server = new ServerSocket(6000);
        System.out.println("Waiting for client...");

        Socket socket = server.accept();
        System.out.println("Client connected.");

        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());

        Message msg = (Message) in.readObject();
        System.out.println(msg.sender + ": " + msg.text);

        Message reply = new Message("Server", "Message received!");
        out.writeObject(reply);

        socket.close();
        server.close();
    }
}
