package commands.aof_persistance;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class AofWriter {

    private final AofConfig aofConfig;

    public AofWriter(AofConfig aofConfig) {
        this.aofConfig = aofConfig;
    }

    /**
     * Appends a command to the active incremental AOF file.
     *
     * The active file is determined from the manifest,
     * not from appendfilename.
     */
    public void appendCommand(
            List<String> elements
    ) throws IOException {

        if (!isAofEnabled()) {
            return;
        }

        Path aofDirectory =
                Path.of(
                        aofConfig.getDir(),
                        aofConfig.getAppenddirname()
                );

        Path manifest =
                aofDirectory.resolve(
                        aofConfig.getAppendfilename()
                                + ".manifest"
                );

        /*
         * Read the active incremental AOF filename
         * from the manifest.
         */
        String aofFileName =
                readActiveAofFile(manifest);

        if (aofFileName == null) {
            throw new IOException(
                    "No active incremental AOF file found in manifest"
            );
        }

        Path aofFile =
                aofDirectory.resolve(
                        aofFileName
                );

        /*
         * Build RESP representation of the command.
         *
         * Example:
         *
         * SET foo 100
         *
         * becomes:
         *
         * *3\r\n
         * $3\r\nSET\r\n
         * $3\r\nfoo\r\n
         * $3\r\n100\r\n
         */
        byte[] commandBytes =
                encodeResp(elements);

        /*
         * Open in append mode.
         */
        try (
                FileOutputStream outputStream =
                        new FileOutputStream(
                                aofFile.toFile(),
                                true
                        )
        ) {

            outputStream.write(commandBytes);

            /*
             * appendfsync always:
             *
             * Make sure the data reaches the OS/disk
             * before returning to the caller.
             */
            if (
                    aofConfig.getAppendfsync()
                            .equalsIgnoreCase("always")
            ) {

                outputStream.flush();

                FileDescriptor fileDescriptor =
                        outputStream.getFD();

                fileDescriptor.sync();
            }
        }
    }

    /**
     * Reads the manifest and returns the filename
     * of the incremental AOF entry.
     *
     * Expected line:
     *
     * file appendonly.aof.1.incr.aof seq 1 type i
     */
    private String readActiveAofFile(
            Path manifest
    ) throws IOException {

        if (!Files.exists(manifest)) {
            throw new IOException(
                    "AOF manifest does not exist: "
                            + manifest
            );
        }

        List<String> lines =
                Files.readAllLines(
                        manifest,
                        StandardCharsets.UTF_8
                );

        for (String line : lines) {

            String trimmed =
                    line.trim();

            if (trimmed.isEmpty()) {
                continue;
            }

            String[] parts =
                    trimmed.split(" ");

            /*
             * Expected:
             *
             * parts[0] = file
             * parts[1] = filename
             * parts[2] = seq
             * parts[3] = 1
             * parts[4] = type
             * parts[5] = i
             */
            if (
                    parts.length >= 6
                            && parts[0].equals("file")
                            && parts[2].equals("seq")
                            && parts[4].equals("type")
                            && parts[5].equals("i")
            ) {

                return parts[1];
            }
        }

        return null;
    }

    /**
     * Encodes a command as a RESP array.
     */
    private byte[] encodeResp(
            List<String> elements
    ) {

        StringBuilder response =
                new StringBuilder();

        response.append("*")
                .append(elements.size())
                .append("\r\n");

        for (String element : elements) {

            byte[] bytes =
                    element.getBytes(
                            StandardCharsets.UTF_8
                    );

            response.append("$")
                    .append(bytes.length)
                    .append("\r\n");

            response.append(element)
                    .append("\r\n");
        }

        return response
                .toString()
                .getBytes(StandardCharsets.UTF_8);
    }

    private boolean isAofEnabled() {

        return aofConfig.getAppendonly()
                .equalsIgnoreCase("yes");
    }
}