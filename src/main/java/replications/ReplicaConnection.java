package replications;

import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

@Getter
public class ReplicaConnection {
    private final Socket socket;
    private final InputStream inputStream;
    private final OutputStream outputStream;

    @Setter
    private volatile long acknowledgedOffset = 0;

    public ReplicaConnection(Socket socket) throws IOException{
        this.socket = socket;
        this.inputStream = socket.getInputStream();
        this.outputStream = socket.getOutputStream();
    }

}
