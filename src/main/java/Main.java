import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {

    private static Map<String, String> data = new HashMap<>();
    private static Map<String, Long> expiry = new HashMap<>();
    private static Map<String, List<String>> lists = new HashMap<>();

    private static String readLine(InputStream inputStream) throws IOException {

        StringBuilder line = new StringBuilder();

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

    // Read a RESP bulk string
    private static String readBulkString(InputStream inputStream) throws IOException {

        String line = readLine(inputStream);

        int length = Integer.parseInt(line.substring(1));

        byte[] data = inputStream.readNBytes(length);

        // Consume "\r\n"
        inputStream.read();
        inputStream.read();

        return new String(data, StandardCharsets.UTF_8);
    }

    private static void handleClient(Socket clientSocket) {

        try {
            OutputStream outputStream = clientSocket.getOutputStream();
            InputStream inputStream = clientSocket.getInputStream();

            while (true) {

                String arrayLine = readLine(inputStream);

                if (arrayLine == null) {
                    break;
                }

                // Remove '*' and get number of elements
                int numberOfElements =
                        Integer.parseInt(arrayLine.substring(1));

                // First element is always the command
                String command = readBulkString(inputStream);

                // ---------------- PING ----------------

                if (command.equalsIgnoreCase("PING")) {

                    if (numberOfElements == 1) {

                        outputStream.write(
                                "+PONG\r\n".getBytes(StandardCharsets.UTF_8)
                        );

                    } else if (numberOfElements == 2) {

                        String argument =
                                readBulkString(inputStream);

                        byte[] data =
                                argument.getBytes(StandardCharsets.UTF_8);

                        outputStream.write(
                                ("$" + data.length + "\r\n")
                                        .getBytes(StandardCharsets.UTF_8)
                        );

                        outputStream.write(data);

                        outputStream.write(
                                "\r\n".getBytes(StandardCharsets.UTF_8)
                        );
                    }
                }

                // ---------------- ECHO ----------------

                else if (command.equalsIgnoreCase("ECHO")) {

                    if (numberOfElements == 2) {

                        String argument =
                                readBulkString(inputStream);

                        byte[] data =
                                argument.getBytes(StandardCharsets.UTF_8);

                        outputStream.write(
                                ("$" + data.length + "\r\n")
                                        .getBytes(StandardCharsets.UTF_8)
                        );

                        outputStream.write(data);

                        outputStream.write(
                                "\r\n".getBytes(StandardCharsets.UTF_8)
                        );
                    }
                }

                // ---------------- SET ----------------

                else if (command.equalsIgnoreCase("SET")) {

                    if (numberOfElements >= 3) {

                        String key =
                                readBulkString(inputStream);

                        String value =
                                readBulkString(inputStream);

                        data.put(key, value);

                        // Remove old expiry if key is overwritten
                        expiry.remove(key);

                        // SET key value EX/PX time
                        if (numberOfElements >= 5) {

                            String option =
                                    readBulkString(inputStream);

                            String time =
                                    readBulkString(inputStream);

                            long expiryTime =
                                    System.currentTimeMillis();

                            if (option.equalsIgnoreCase("EX")) {

                                // Time in seconds
                                expiryTime +=
                                        Long.parseLong(time) * 1000;

                                expiry.put(key, expiryTime);

                            } else if (option.equalsIgnoreCase("PX")) {

                                // Time in milliseconds
                                expiryTime +=
                                        Long.parseLong(time);

                                expiry.put(key, expiryTime);
                            }
                        }

                        outputStream.write(
                                "+OK\r\n".getBytes(StandardCharsets.UTF_8)
                        );
                    }
                }

                // ---------------- GET ----------------

                else if (command.equalsIgnoreCase("GET")) {

                    if (numberOfElements == 2) {

                        String key =
                                readBulkString(inputStream);

                        // Check expiry first
                        if (expiry.containsKey(key)) {

                            long expiryTime =
                                    expiry.get(key);

                            if (expiryTime <= System.currentTimeMillis()) {

                                data.remove(key);
                                expiry.remove(key);
                            }
                        }

                        String val = data.get(key);

                        // Key doesn't exist OR key has expired
                        if (val == null) {

                            outputStream.write(
                                    "$-1\r\n"
                                            .getBytes(StandardCharsets.UTF_8)
                            );

                        } else {

                            byte[] value =
                                    val.getBytes(StandardCharsets.UTF_8);

                            outputStream.write(
                                    ("$" + value.length + "\r\n")
                                            .getBytes(StandardCharsets.UTF_8)
                            );

                            outputStream.write(value);

                            outputStream.write(
                                    "\r\n"
                                            .getBytes(StandardCharsets.UTF_8)
                            );
                        }
                    }
                }

                // ---------------- RPUSH ----------------

                else if (command.equalsIgnoreCase("RPUSH")) {

                    if (numberOfElements >= 3) {

                        String key =
                                readBulkString(inputStream);

                        List<String> list =
                                lists.computeIfAbsent(
                                        key,
                                        k -> new ArrayList<>()
                                );

                        // Read and append all values
                        for (int i = 2; i < numberOfElements; i++) {

                            String value =
                                    readBulkString(inputStream);

                            list.add(value);
                        }

                        // Return new length of the list
                        outputStream.write(
                                (":" + list.size() + "\r\n")
                                        .getBytes(StandardCharsets.UTF_8)
                        );
                    }
                }

                // ---------------- LRANGE ----------------

                else if (command.equalsIgnoreCase("LRANGE")) {

                    if (numberOfElements == 4) {

                        String key =
                                readBulkString(inputStream);

                        int start =
                                Integer.parseInt(
                                        readBulkString(inputStream)
                                );

                        int stop =
                                Integer.parseInt(
                                        readBulkString(inputStream)
                                );

                        List<String> list =
                                lists.get(key);

                        // List doesn't exist
                        if (list == null) {

                            outputStream.write(
                                    "*0\r\n"
                                            .getBytes(StandardCharsets.UTF_8)
                            );

                        } else {

                            int listSize = list.size();

                            /*
                             * Convert negative indexes.
                             *
                             * -1 -> last element
                             * -2 -> second last
                             * etc.
                             */
                            if (start < 0) {
                                start = listSize + start;
                            }

                            if (stop < 0) {
                                stop = listSize + stop;
                            }

                            // Negative index beyond the beginning
                            if (start < 0) {
                                start = 0;
                            }

                            if (stop < 0) {
                                stop = 0;
                            }

                            /*
                             * If start is outside the list
                             * or start > stop, return empty array.
                             */
                            if (start >= listSize ||
                                    start > stop) {

                                outputStream.write(
                                        "*0\r\n"
                                                .getBytes(StandardCharsets.UTF_8)
                                );

                            } else {

                                /*
                                 * If stop is greater than or equal
                                 * to list size, use the last element.
                                 */
                                stop =
                                        Math.min(
                                                stop,
                                                listSize - 1
                                        );

                                int resultSize =
                                        stop - start + 1;

                                // RESP array header
                                outputStream.write(
                                        ("*" + resultSize + "\r\n")
                                                .getBytes(
                                                        StandardCharsets.UTF_8
                                                )
                                );

                                // Write each element
                                for (int i = start;
                                     i <= stop;
                                     i++) {

                                    String value =
                                            list.get(i);

                                    byte[] bytes =
                                            value.getBytes(
                                                    StandardCharsets.UTF_8
                                            );

                                    // RESP bulk string header
                                    outputStream.write(
                                            ("$" + bytes.length + "\r\n")
                                                    .getBytes(
                                                            StandardCharsets.UTF_8
                                                    )
                                    );

                                    outputStream.write(bytes);

                                    outputStream.write(
                                            "\r\n"
                                                    .getBytes(
                                                            StandardCharsets.UTF_8
                                                    )
                                    );
                                }
                            }
                        }
                    }
                }

                outputStream.flush();
            }

            clientSocket.close();

        } catch (IOException e) {

            System.out.println(
                    "Client IOException: " + e.getMessage()
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
                                () -> handleClient(clientSocket)
                        );

                clientThread.start();
            }

        } catch (IOException e) {

            System.out.println(
                    "IOException: " + e.getMessage()
            );
        }
    }
}