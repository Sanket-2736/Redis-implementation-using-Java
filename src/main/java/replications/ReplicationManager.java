package replications;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ReplicationManager {
    private final List<ReplicaConnection> replicas =
            new ArrayList<>();

    private long replicationOffset = 0;

    public synchronized void addReplica(
            ReplicaConnection replica
    ) {
        if (!replicas.contains(replica)) {
            replicas.add(replica);
        }
    }

    public synchronized void removeReplica(
            ReplicaConnection replica
    ) {
        replicas.remove(replica);
    }

    public synchronized int getReplicaCount() {
        return replicas.size();
    }

    public synchronized long getReplicationOffset() {
        return replicationOffset;
    }

    public synchronized void propagate(
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

        Iterator<ReplicaConnection> iterator =
                replicas.iterator();

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

        replicationOffset += commandLength;
    }

    public synchronized int waitForReplicas(
            int requiredReplicas,
            long timeout
    ) {

        if (replicas.isEmpty()) {
            return 0;
        }

        long targetOffset =
                replicationOffset;

        long deadline =
                System.currentTimeMillis() + timeout;


        // Ask every replica for its current offset

        for (ReplicaConnection replica : replicas) {

            try {

                String getAck =
                        "*3\r\n" +
                                "$8\r\n" +
                                "REPLCONF\r\n" +
                                "$6\r\n" +
                                "GETACK\r\n" +
                                "$1\r\n" +
                                "*\r\n";

                replica.getOutputStream()
                        .write(
                                getAck.getBytes(
                                        StandardCharsets.UTF_8
                                )
                        );

                replica.getOutputStream()
                        .flush();

            } catch (IOException e) {
                // Ignore disconnected replica
            }
        }


        while (System.currentTimeMillis() < deadline) {

            int acknowledged = 0;

            for (ReplicaConnection replica : replicas) {

                try {

                    if (replica.getInputStream().available() > 0) {
                        readAck(replica);
                    }

                } catch (IOException ignored) {
                }

                if (replica.getAcknowledgedOffset()
                        >= targetOffset) {

                    acknowledged++;
                }
            }


            if (acknowledged >= requiredReplicas) {
                return acknowledged;
            }


            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }


        int acknowledged = 0;

        for (ReplicaConnection replica : replicas) {

            if (replica.getAcknowledgedOffset()
                    >= targetOffset) {

                acknowledged++;
            }
        }

        return acknowledged;
    }


    private void readAck(
            ReplicaConnection replica
    ) throws IOException {

        String arrayLine =
                readLine(
                        replica.getInputStream()
                );

        if (arrayLine == null) {
            return;
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
                    readBulkString(
                            replica.getInputStream()
                    )
            );
        }

        if (elements.size() >= 3
                && elements.get(0).equalsIgnoreCase("REPLCONF")
                && elements.get(1).equalsIgnoreCase("ACK")) {

            replica.setAcknowledgedOffset(
                    Long.parseLong(elements.get(2))
            );
        }
    }


    private String readLine(
            java.io.InputStream inputStream
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


    private String readBulkString(
            java.io.InputStream inputStream
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

        inputStream.read();
        inputStream.read();

        return new String(
                data,
                StandardCharsets.UTF_8
        );
    }


    private int getCommandByteLength(
            List<String> elements
    ) {

        int length = 0;

        length += (
                "*" +
                        elements.size() +
                        "\r\n"
        ).getBytes(StandardCharsets.UTF_8).length;

        for (String element : elements) {

            byte[] bytes =
                    element.getBytes(StandardCharsets.UTF_8);

            length += (
                    "$" +
                            bytes.length +
                            "\r\n"
            ).getBytes(StandardCharsets.UTF_8).length;

            length += bytes.length;
            length += 2;
        }

        return length;
    }

}
