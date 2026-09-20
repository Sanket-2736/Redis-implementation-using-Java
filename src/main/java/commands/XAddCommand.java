package commands;

import storage.RedisData;
import storage.StreamEntry;
import utils.RespUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class XAddCommand implements Command {

    @Override
    public void execute(
            List<String> args,
            RedisData redisData,
            OutputStream outputStream
    ) throws IOException {

        if (args.size() < 4) {
            return;
        }

        String key = args.get(0);
        String id = args.get(1);

        if ((args.size() - 2) % 2 != 0) {
            return;
        }

        synchronized (redisData) {

            Map<String, List<StreamEntry>> streams =
                    redisData.getStreams();

            List<StreamEntry> stream =
                    streams.computeIfAbsent(
                            key,
                            k -> new ArrayList<>()
                    );

            /*
             * XADD key * field value
             */
            if (id.equals("*")) {

                long currentTime =
                        System.currentTimeMillis();

                long sequenceNumber = 0;

                if (!stream.isEmpty()) {

                    StreamEntry lastEntry =
                            stream.get(stream.size() - 1);

                    String[] parts =
                            lastEntry.getId().split("-");

                    long lastTime =
                            Long.parseLong(parts[0]);

                    long lastSequence =
                            Long.parseLong(parts[1]);

                    /*
                     * Make generated IDs strictly increasing.
                     */
                    if (currentTime < lastTime) {
                        currentTime = lastTime;
                        sequenceNumber = lastSequence + 1;

                    } else if (currentTime == lastTime) {
                        sequenceNumber = lastSequence + 1;
                    }
                }

                id = currentTime + "-" + sequenceNumber;
            }

            /*
             * XADD key <milliseconds>-* field value
             */
            else if (id.endsWith("-*")) {

                String timePart =
                        id.substring(
                                0,
                                id.length() - 2
                        );

                long millisecondsTime =
                        Long.parseLong(timePart);

                long sequenceNumber = 0;

                if (!stream.isEmpty()) {

                    StreamEntry lastEntry =
                            stream.get(stream.size() - 1);

                    String[] parts =
                            lastEntry.getId().split("-");

                    long lastTime =
                            Long.parseLong(parts[0]);

                    long lastSequence =
                            Long.parseLong(parts[1]);

                    if (millisecondsTime == lastTime) {
                        sequenceNumber =
                                lastSequence + 1;
                    }
                }

                if (millisecondsTime == 0
                        && sequenceNumber == 0) {

                    sequenceNumber = 1;
                }

                id = millisecondsTime
                        + "-"
                        + sequenceNumber;
            }

            else {

                String[] parts = id.split("-");

                long millisecondsTime =
                        Long.parseLong(parts[0]);

                long sequenceNumber =
                        Long.parseLong(parts[1]);

                if (millisecondsTime == 0
                        && sequenceNumber == 0) {

                    RespUtil.writeSimpleError(
                            outputStream,
                            "The ID specified in XADD must be greater than 0-0"
                    );

                    return;
                }

                if (!stream.isEmpty()) {

                    StreamEntry lastEntry =
                            stream.get(stream.size() - 1);

                    String[] lastParts =
                            lastEntry.getId().split("-");

                    long lastTime =
                            Long.parseLong(lastParts[0]);

                    long lastSequence =
                            Long.parseLong(lastParts[1]);

                    if (millisecondsTime < lastTime
                            ||
                            (millisecondsTime == lastTime
                                    && sequenceNumber
                                    <= lastSequence)) {

                        RespUtil.writeSimpleError(
                                outputStream,
                                "The ID specified in XADD is equal or smaller than the target stream top item"
                        );

                        return;
                    }
                }
            }

            StreamEntry entry =
                    new StreamEntry(id);

            for (int i = 2;
                 i < args.size();
                 i += 2) {

                String field = args.get(i);
                String value = args.get(i + 1);

                entry.addField(field, value);
            }

            stream.add(entry);

            redisData.notifyAll();
            RespUtil.writeBulkString(
                    outputStream,
                    id
            );
        }
    }
}