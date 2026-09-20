import commands.Command;
import commands.CommandRegistry;
import storage.RedisData;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Main {

    private static final RedisData redisData =
            new RedisData(
                    new HashMap<>(),
                    new HashMap<>(),
                    new HashMap<>(),
                    new HashMap<>()
            );

    private static final CommandRegistry commandRegistry =
            new CommandRegistry();

    // Read a line ending with \r\n
    private static String readLine(
            InputStream inputStream
    ) throws IOException {

        StringBuilder line =
                new StringBuilder();

        int ch;

        while ((ch = inputStream.read()) != -1) {

            if (ch == '\r') {

                inputStream.read(); // consume '\n'

                return line.toString();
            }

            line.append((char) ch);
        }

        return null;
    }

    // Read RESP bulk string
    private static String readBulkString(
            InputStream inputStream
    ) throws IOException {

        String line =
                readLine(inputStream);

        if (line == null) {
            return null;
        }

        int length =
                Integer.parseInt(
                        line.substring(1)
                );

        byte[] data =
                inputStream.readNBytes(length);

        // Consume \r\n
        inputStream.read();
        inputStream.read();

        return new String(
                data,
                StandardCharsets.UTF_8
        );
    }

    private static void handleClient(
            Socket clientSocket
    ) {

        try {

            InputStream inputStream =
                    clientSocket.getInputStream();

            OutputStream outputStream =
                    clientSocket.getOutputStream();

            while (true) {
                String arrayLine =
                        readLine(inputStream);

                if (arrayLine == null) {
                    break;
                }

                int numberOfElements =
                        Integer.parseInt(
                                arrayLine.substring(1)
                        );

                List<String> elements =
                        new ArrayList<>();

                for (int i = 0;
                     i < numberOfElements;
                     i++) {

                    elements.add(
                            readBulkString(inputStream)
                    );
                }

                if (elements.isEmpty()) {
                    continue;
                }

                String commandName =
                        elements.get(0)
                                .toUpperCase();

                List<String> args =
                        elements.subList(
                                1,
                                elements.size()
                        );

                Command command =
                        commandRegistry.getCommand(
                                commandName
                        );

                if (command != null) {

                    command.execute(
                            args,
                            redisData,
                            outputStream
                    );
                }

                outputStream.flush();
            }

            clientSocket.close();

        } catch (IOException e) {

            System.out.println(
                    "Client IOException: "
                            + e.getMessage()
            );
        }
    }

    public static void main(String[] args) {

        System.out.println(
                "Logs from your program will appear here!"
        );

        int port = 6379;

        try {

            ServerSocket serverSocket =
                    new ServerSocket(port);

            serverSocket.setReuseAddress(true);

            while (true) {

                Socket clientSocket =
                        serverSocket.accept();

                Thread clientThread =
                        new Thread(
                                () ->
                                        handleClient(
                                                clientSocket
                                        )
                        );

                clientThread.start();
            }

        } catch (IOException e) {

            System.out.println(
                    "IOException: "
                            + e.getMessage()
            );
        }
    }
}