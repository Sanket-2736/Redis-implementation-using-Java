package commands;

import storage.RedisData;
import storage.StreamEntry;
import utils.RespUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class XReadCommand implements Command {

    @Override
    public void execute(
            List<String> args,
            RedisData redisData,
            OutputStream outputStream
    ) throws IOException {

        boolean blocking = false;
        long timeoutMillis = 0;

        int index = 0;

        // XREAD BLOCK <milliseconds> STREAMS ...
        if (index < args.size()
                && args.get(index).equalsIgnoreCase("BLOCK")) {

            blocking = true;

            if (index + 1 >= args.size()) {
                return;
            }

            timeoutMillis =
                    Long.parseLong(args.get(index + 1));

            index += 2;
        }

        // STREAMS must be present
        if (index >= args.size()
                || !args.get(index).equalsIgnoreCase("STREAMS")) {
            return;
        }

        index++;

        int remaining = args.size() - index;

        if (remaining < 2 || remaining % 2 != 0) {
            return;
        }

        int streamCount = remaining / 2;

        List<String> keys = new ArrayList<>();
        List<String> ids = new ArrayList<>();

        // Read stream keys
        for (int i = 0; i < streamCount; i++) {
            keys.add(args.get(index + i));
        }

        // Read IDs
        for (int i = 0; i < streamCount; i++) {
            ids.add(args.get(index + streamCount + i));
        }

        synchronized (redisData) {

            /*
             * IMPORTANT:
             *
             * Resolve "$" before blocking.
             *
             * "$" means:
             * "start from the current end of the stream"
             */
            for (int i = 0; i < streamCount; i++) {

                if (!ids.get(i).equals("$")) {
                    continue;
                }

                String key = keys.get(i);

                List<StreamEntry> stream =
                        redisData.getStreams().get(key);

                if (stream == null || stream.isEmpty()) {

                    /*
                     * No entries currently exist.
                     *
                     * Use -1-0 so that the first real entry
                     * will be greater than this ID.
                     */
                    ids.set(i, "-1-0");

                } else {

                    /*
                     * Capture the current last ID.
                     *
                     * This is the meaning of "$".
                     */
                    StreamEntry lastEntry =
                            stream.get(stream.size() - 1);

                    ids.set(i, lastEntry.getId());
                }
            }

            long startTime = System.currentTimeMillis();

            while (true) {

                Map<String, List<StreamEntry>> streams =
                        redisData.getStreams();

                List<List<StreamEntry>> results =
                        new ArrayList<>();

                /*
                 * Find entries greater than the
                 * starting IDs.
                 */
                for (int i = 0; i < streamCount; i++) {

                    String key = keys.get(i);
                    String startId = ids.get(i);

                    List<StreamEntry> stream =
                            streams.get(key);

                    List<StreamEntry> result =
                            new ArrayList<>();

                    if (stream != null) {

                        String[] parts =
                                startId.split("-");

                        long startStreamTime =
                                Long.parseLong(parts[0]);

                        long startSequence =
                                parts.length == 1
                                        ? 0
                                        : Long.parseLong(parts[1]);

                        for (StreamEntry entry : stream) {

                            String[] entryParts =
                                    entry.getId().split("-");

                            long entryTime =
                                    Long.parseLong(entryParts[0]);

                            long entrySequence =
                                    Long.parseLong(entryParts[1]);

                            boolean greaterThanStart =
                                    entryTime > startStreamTime
                                            ||
                                            (entryTime == startStreamTime
                                                    && entrySequence
                                                    > startSequence);

                            if (greaterThanStart) {
                                result.add(entry);
                            }
                        }
                    }

                    results.add(result);
                }

                /*
                 * Check whether any stream has
                 * new entries.
                 */
                boolean hasResults = false;

                for (List<StreamEntry> result : results) {

                    if (!result.isEmpty()) {
                        hasResults = true;
                        break;
                    }
                }

                /*
                 * New data available.
                 */
                if (hasResults) {

                    writeResponse(
                            outputStream,
                            keys,
                            results
                    );

                    return;
                }

                /*
                 * Non-blocking XREAD.
                 */
                if (!blocking) {

                    RespUtil.writeArrayHeader(
                            outputStream,
                            0
                    );

                    return;
                }

                /*
                 * BLOCK 0:
                 * wait forever.
                 */
                if (timeoutMillis == 0) {

                    try {
                        redisData.wait();

                    } catch (InterruptedException e) {

                        Thread.currentThread().interrupt();
                        return;
                    }

                } else {

                    /*
                     * Calculate remaining timeout.
                     */
                    long elapsed =
                            System.currentTimeMillis()
                                    - startTime;

                    long remainingMillis =
                            timeoutMillis - elapsed;

                    /*
                     * Timeout expired.
                     */
                    if (remainingMillis <= 0) {

                        RespUtil.writeNullArray(
                                outputStream
                        );

                        return;
                    }

                    try {

                        redisData.wait(
                                remainingMillis
                        );

                    } catch (InterruptedException e) {

                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    private void writeResponse(
            OutputStream outputStream,
            List<String> keys,
            List<List<StreamEntry>> results
    ) throws IOException {

        int responseStreamCount = 0;

        for (List<StreamEntry> result : results) {

            if (!result.isEmpty()) {
                responseStreamCount++;
            }
        }

        RespUtil.writeArrayHeader(
                outputStream,
                responseStreamCount
        );

        for (int i = 0; i < results.size(); i++) {

            List<StreamEntry> result =
                    results.get(i);

            if (result.isEmpty()) {
                continue;
            }

            String key = keys.get(i);

            // [key, entries]
            RespUtil.writeArrayHeader(
                    outputStream,
                    2
            );

            RespUtil.writeBulkString(
                    outputStream,
                    key
            );

            // entries
            RespUtil.writeArrayHeader(
                    outputStream,
                    result.size()
            );

            for (StreamEntry entry : result) {

                // [id, fields]
                RespUtil.writeArrayHeader(
                        outputStream,
                        2
                );

                RespUtil.writeBulkString(
                        outputStream,
                        entry.getId()
                );

                Map<String, String> fields =
                        entry.getFields();

                RespUtil.writeArrayHeader(
                        outputStream,
                        fields.size() * 2
                );

                for (Map.Entry<String, String> field
                        : fields.entrySet()) {

                    RespUtil.writeBulkString(
                            outputStream,
                            field.getKey()
                    );

                    RespUtil.writeBulkString(
                            outputStream,
                            field.getValue()
                    );
                }
            }
        }
    }
}