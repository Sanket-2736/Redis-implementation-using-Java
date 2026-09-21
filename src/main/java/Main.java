import commands.Command;
import commands.CommandRegistry;
import commands.transactions.TransactionState;
import storage.RedisData;
import utils.RespUtil;

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
    private static String role = "master";
    private static String replicationId =
            "8371b4fb1155b71f4a04d3e1bc3e18c4a990aeeb";

    private static long replicationOffset = 0;
    private static final RedisData redisData =
            new RedisData(
                    new HashMap<>(),
                    new HashMap<>(),
                    new HashMap<>(),
                    new HashMap<>(),
                    new HashMap<>(),
                    role,
                    replicationId,
                    replicationOffset
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

        /*
         * IMPORTANT:
         * Each client gets its own TransactionState.
         *
         * This means multiple clients can have independent
         * transactions at the same time.
         */
        TransactionState transactionState =
                new TransactionState();

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
                        elements.get(0).toUpperCase();

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

                    /*
                     * If we're inside MULTI, queue commands
                     * instead of executing them.
                     *
                     * MULTI, EXEC and DISCARD must execute
                     * immediately because they control the
                     * transaction itself.
                     */
                    if (transactionState.isInTransaction()
                            && !commandName.equals("MULTI")
                            && !commandName.equals("EXEC")
                            && !commandName.equals("WATCH")
                            && !commandName.equals("UNWATCH")
                            && !commandName.equals("DISCARD")) {

                        transactionState.queueCommand(elements);

                        RespUtil.writeSimpleString(
                                outputStream,
                                "QUEUED"
                        );

                    } else {

                        command.execute(
                                args,
                                redisData,
                                outputStream,
                                transactionState
                        );
                    }
                }

                outputStream.flush();
            }

        } catch (IOException e) {

            System.out.println(
                    "Client IOException: "
                            + e.getMessage()
            );

        } finally {

            try {
                clientSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

    public static void main(String[] args) {

        System.out.println(
                "Logs from your program will appear here!"
        );

        int port = 6379;

        for(int i = 0; i < args.length; i++){
            if(args[i].equals("--port") && (i + 1) < args.length){
                port = Integer.parseInt(args[i + 1]);
                i++;
            }

            if (args[i].equals("--replicaof") && i + 1 < args.length) {
                role = "slave";
                i++;
            }
        }

        try {

            ServerSocket serverSocket =
                    new ServerSocket(port);
            System.out.println("Redis server started on port: " + port);

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