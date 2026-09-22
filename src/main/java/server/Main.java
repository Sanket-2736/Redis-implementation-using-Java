package server;

import commands.Command;
import commands.CommandRegistry;
import commands.transactions.TransactionState;
import lombok.Getter;
import replications.ReplicaConnection;
import storage.RedisData;
import utils.RespUtil;

import java.io.ByteArrayOutputStream;
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

    private static RedisData redisData;
    public static final List<OutputStream> replicaOutputStreams =
            new ArrayList<>();

    private final static List<ReplicaConnection> replicas = new ArrayList<>();

    private static final CommandRegistry commandRegistry =
            new CommandRegistry();

    private static int getCommandByteLength(List<String> elements) {

        int length = 0;

        length += ("*" + elements.size() + "\r\n")
                .getBytes(StandardCharsets.UTF_8)
                .length;

        for (String element : elements) {

            byte[] bytes =
                    element.getBytes(StandardCharsets.UTF_8);

            length += ("$" + bytes.length + "\r\n")
                    .getBytes(StandardCharsets.UTF_8)
                    .length;

            length += bytes.length;

            length += 2;
        }

        return length;
    }

    private static void sendGetAck(
            ReplicaConnection replica
    ) throws IOException {

        String command =
                "*3\r\n" +
                        "$8\r\n" +
                        "REPLCONF\r\n" +
                        "$6\r\n" +
                        "GETACK\r\n" +
                        "$1\r\n" +
                        "*\r\n";

        replica.getOutputStream()
                .write(command.getBytes(StandardCharsets.UTF_8));

        replica.getOutputStream().flush();
    }

    private static boolean readAck(
            ReplicaConnection replica
    ) throws IOException {

        String arrayLine =
                readLine(replica.getInputStream());

        if (arrayLine == null) {
            return false;
        }

        int numberOfElements =
                Integer.parseInt(arrayLine.substring(1));

        List<String> elements =
                new ArrayList<>();

        for (int i = 0; i < numberOfElements; i++) {
            elements.add(
                    readBulkString(
                            replica.getInputStream()
                    )
            );
        }

        if (elements.size() >= 3
                && elements.get(0).equalsIgnoreCase("REPLCONF")
                && elements.get(1).equalsIgnoreCase("ACK")) {

            long offset =
                    Long.parseLong(elements.get(2));

            replica.setAcknowledgedOffset(offset);

            return true;
        }

        return false;
    }

    private static void connectToMaster(
            String masterHost,
            int masterPort,
            int replicaPort
    ) throws IOException {

        Socket masterSocket =
                new Socket(masterHost, masterPort);

        InputStream inputStream =
                masterSocket.getInputStream();

        OutputStream outputStream =
                masterSocket.getOutputStream();

        String pingCommand =
                "*1\r\n" +
                        "$4\r\n" +
                        "PING\r\n";

        outputStream.write(
                pingCommand.getBytes(StandardCharsets.UTF_8)
        );

        outputStream.flush();

        // Wait for +PONG
        readResponse(inputStream);


        // --------------------------------------------------------
        // 2. REPLCONF listening-port <PORT>
        // --------------------------------------------------------

        String port =
                String.valueOf(replicaPort);

        String listeningPortCommand =
                "*3\r\n" +
                        "$8\r\n" +
                        "REPLCONF\r\n" +
                        "$14\r\n" +
                        "listening-port\r\n" +
                        "$" + port.length() + "\r\n" +
                        port + "\r\n";

        outputStream.write(
                listeningPortCommand.getBytes(StandardCharsets.UTF_8)
        );

        outputStream.flush();

        // Wait for +OK
        readResponse(inputStream);


        // --------------------------------------------------------
        // 3. REPLCONF capa psync2
        // --------------------------------------------------------

        String capaCommand =
                "*3\r\n" +
                        "$8\r\n" +
                        "REPLCONF\r\n" +
                        "$4\r\n" +
                        "capa\r\n" +
                        "$6\r\n" +
                        "psync2\r\n";

        outputStream.write(
                capaCommand.getBytes(StandardCharsets.UTF_8)
        );

        outputStream.flush();

        // Wait for +OK
        readResponse(inputStream);


        // --------------------------------------------------------
        // 4. PSYNC ? -1
        // --------------------------------------------------------

        String psyncCommand =
                "*3\r\n" +
                        "$5\r\n" +
                        "PSYNC\r\n" +
                        "$1\r\n" +
                        "?\r\n" +
                        "$2\r\n" +
                        "-1\r\n";

        outputStream.write(
                psyncCommand.getBytes(StandardCharsets.UTF_8)
        );

        outputStream.flush();


        // --------------------------------------------------------
        // Read FULLRESYNC response
        // --------------------------------------------------------

        readResponse(inputStream);


        // --------------------------------------------------------
        // Read RDB bulk string
        // --------------------------------------------------------

        String rdbHeader =
                readLine(inputStream);

        if (rdbHeader == null) {
            return;
        }

        int rdbLength =
                Integer.parseInt(
                        rdbHeader.substring(1)
                );

        inputStream.readNBytes(rdbLength);

        // Consume \r\n after RDB
        inputStream.read();
        inputStream.read();


        // --------------------------------------------------------
        // Receive replicated commands
        // --------------------------------------------------------

        while (true) {

            String arrayLine =
                    readLine(inputStream);

            if (arrayLine == null) {
                break;
            }

            if (arrayLine.isEmpty()) {
                continue;
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

                String element =
                        readBulkString(inputStream);

                if (element == null) {
                    return;
                }

                elements.add(element);
            }

            if (elements.isEmpty()) {
                continue;
            }


            // ----------------------------------------------------
            // Calculate command length BEFORE processing
            // ----------------------------------------------------

            int commandLength =
                    getCommandByteLength(elements);


            String commandName =
                    elements.get(0).toUpperCase();

            List<String> args =
                    elements.subList(
                            1,
                            elements.size()
                    );


            // ----------------------------------------------------
            // REPLCONF GETACK *
            // ----------------------------------------------------

            if (commandName.equals("REPLCONF")
                    && args.size() >= 2
                    && args.get(0).equalsIgnoreCase("GETACK")) {

                /*
                 * Send the current offset first.
                 *
                 * The current GETACK command itself must NOT
                 * be included in this ACK.
                 */
                sendAck(
                        outputStream,
                        replicationOffset
                );

                /*
                 * After sending the ACK, count the GETACK
                 * command so that the next ACK includes it.
                 */
                replicationOffset += commandLength;

                continue;
            }




            // ----------------------------------------------------
            // Execute replicated command silently
            // ----------------------------------------------------

            Command command =
                    commandRegistry.getCommand(commandName);

            if (command != null) {

                command.execute(
                        args,
                        redisData,
                        new ByteArrayOutputStream(),
                        new TransactionState()
                );
            }


            // ----------------------------------------------------
            // Count every received command
            // ----------------------------------------------------

            replicationOffset += commandLength;
        }
    }


    // ============================================================
    // Read RESP simple line
    // ============================================================

    private static String readResponse(
            InputStream inputStream
    ) throws IOException {

        StringBuilder res =
                new StringBuilder();

        int prev = -1;
        int curr;

        while ((curr = inputStream.read()) != -1) {

            res.append((char) curr);

            if (prev == '\r'
                    && curr == '\n') {

                break;
            }

            prev = curr;
        }

        return res.toString();
    }


    // ============================================================
    // Read line ending with \r\n
    // ============================================================

    private static String readLine(
            InputStream inputStream
    ) throws IOException {

        StringBuilder line =
                new StringBuilder();

        int ch;

        while ((ch = inputStream.read()) != -1) {

            if (ch == '\r') {

                inputStream.read();

                return line.toString();
            }

            line.append((char) ch);
        }

        return null;
    }


    // ============================================================
    // Read RESP bulk string
    // ============================================================

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


    // ============================================================
    // Send REPLCONF ACK
    // ============================================================

    private static void sendAck(
            OutputStream outputStream,
            long offset
    ) throws IOException {

        String offsetString =
                String.valueOf(offset);

        String response =
                "*3\r\n" +
                        "$8\r\n" +
                        "REPLCONF\r\n" +
                        "$3\r\n" +
                        "ACK\r\n" +
                        "$" + offsetString.length() + "\r\n" +
                        offsetString + "\r\n";

        outputStream.write(
                response.getBytes(StandardCharsets.UTF_8)
        );

        outputStream.flush();
    }


    // ============================================================
    // Propagate command to all replicas
    // ============================================================

    private static synchronized void propagateCommand(
            List<String> elements
    ) throws IOException {

        int commandLength =
                getCommandByteLength(elements);

        StringBuilder command =
                new StringBuilder();

        command.append("*")
                .append(elements.size())
                .append("\r\n");

        for (String element : elements) {

            byte[] bytes =
                    element.getBytes(StandardCharsets.UTF_8);

            command.append("$")
                    .append(bytes.length)
                    .append("\r\n")
                    .append(element)
                    .append("\r\n");
        }

        byte[] commandBytes =
                command.toString()
                        .getBytes(StandardCharsets.UTF_8);

        synchronized (replicas) {

            var iterator = replicas.iterator();

            while (iterator.hasNext()) {

                ReplicaConnection replica =
                        iterator.next();

                try {

                    replica.getOutputStream()
                            .write(commandBytes);

                    replica.getOutputStream()
                            .flush();

                } catch (IOException e) {

                    iterator.remove();

                    try {
                        replica.getSocket().close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }

        replicationOffset += commandLength;
    }


    // ============================================================
    // Handle client connection
    // ============================================================

    private static void handleClient(
            Socket clientSocket
    ) {

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

                if (arrayLine.isEmpty()) {
                    continue;
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

                    String element =
                            readBulkString(inputStream);

                    if (element == null) {
                        return;
                    }

                    elements.add(element);
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

                System.out.println("Received command: " + commandName + " args=" + args);


                Command command =
                        commandRegistry.getCommand(
                                commandName
                        );
                System.out.println("Command found: " + (command != null));

                // ------------------------------------------------
                // Register replica connection on REPLCONF
                // ------------------------------------------------

                if (commandName.equals("REPLCONF")) {

                    synchronized (replicaOutputStreams) {

                        if (!replicaOutputStreams.contains(
                                outputStream
                        )) {

                            replicaOutputStreams.add(
                                    outputStream
                            );
                        }
                    }
                }


                // ------------------------------------------------
                // Execute command
                // ------------------------------------------------

                if (command != null) {

                    if (transactionState.isInTransaction()
                            && !commandName.equals("MULTI")
                            && !commandName.equals("EXEC")
                            && !commandName.equals("WATCH")
                            && !commandName.equals("UNWATCH")
                            && !commandName.equals("DISCARD")) {

                        transactionState.queueCommand(
                                elements
                        );

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


                        // ----------------------------------------
                        // Propagate SET to replicas
                        // ----------------------------------------

                        if (commandName.equals("SET")) {

                            propagateCommand(elements);
                        }
                    }
                } else {
                    String response = "-ERR unknown command '" + commandName + "'\r\n";
                    outputStream.write(response.getBytes(StandardCharsets.UTF_8));
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


    // ============================================================
    // Main
    // ============================================================

    public static void main(String[] args) {

        System.out.println(
                "Logs from your program will appear here!"
        );


        String masterHost = null;
        int masterPort = -1;

        int port = 6379;


        // --------------------------------------------------------
        // Parse command-line arguments
        // --------------------------------------------------------

        for (int i = 0;
             i < args.length;
             i++) {

            if (args[i].equals("--port")
                    && (i + 1) < args.length) {

                port =
                        Integer.parseInt(
                                args[i + 1]
                        );

                i++;
            }


            if (args[i].equals("--replicaof")
                    && i + 1 < args.length) {

                role = "slave";

                String[] replicaOf =
                        args[i + 1].split(" ");

                masterHost =
                        replicaOf[0];

                masterPort =
                        Integer.parseInt(
                                replicaOf[1]
                        );

                i++;
            }
        }


        // --------------------------------------------------------
        // Initialize RedisData AFTER role is known
        // --------------------------------------------------------

        redisData =
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


        try {

            // ----------------------------------------------------
            // Start replication in background for replica
            // ----------------------------------------------------

            if (role.equals("slave")) {

                String finalMasterHost =
                        masterHost;

                int finalMasterPort =
                        masterPort;

                int finalReplicaPort =
                        port;


                Thread replicationThread =
                        new Thread(() -> {

                            try {

                                connectToMaster(
                                        finalMasterHost,
                                        finalMasterPort,
                                        finalReplicaPort
                                );

                            } catch (IOException e) {

                                System.out.println(
                                        "Replication IOException: "
                                                + e.getMessage()
                                );
                            }
                        });


                replicationThread.start();
            }


            // ----------------------------------------------------
            // Start Redis server
            // ----------------------------------------------------

            ServerSocket serverSocket =
                    new ServerSocket(port);

            System.out.println(
                    "Redis server started on port: "
                            + port
            );


            serverSocket.setReuseAddress(true);


            // ----------------------------------------------------
            // Accept clients
            // ----------------------------------------------------

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

//~$ ip route | grep default
//default via your_ip dev eth0 proto kernel
//~$ redis-cli -2 -h your_ip -p 6379