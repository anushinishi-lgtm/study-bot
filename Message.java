import java.io.Serializable;

public class Message implements Serializable {
    String sender;
    String text;

    Message(String sender, String text) {
        this.sender = sender;
        this.text = text;
    }
}
