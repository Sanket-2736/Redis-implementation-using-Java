package commands;

import storage.RedisData;
import utils.RespUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

public class LRangeCommand implements Command {

    @Override
    public void execute(
            List<String> args,
            RedisData redisData,
            OutputStream outputStream
    ) throws IOException {

        if (args.size() != 3) {
            return;
        }

        String key = args.get(0);

        int start =
                Integer.parseInt(args.get(1));

        int stop =
                Integer.parseInt(args.get(2));

        Map<String, List<String>> lists =
                redisData.getLists();

        List<String> list =
                lists.get(key);

        // List doesn't exist
        if (list == null) {

            RespUtil.writeEmptyArray(
                    outputStream
            );

            return;
        }

        int listSize = list.size();

        /*
         * Convert negative indexes.
         *
         * -1 -> last element
         * -2 -> second last
         * etc.
         */

        if (start < 0) {

            start =
                    listSize + start;
        }

        if (stop < 0) {

            stop =
                    listSize + stop;
        }

        /*
         * If negative index is outside
         * the beginning of the list,
         * treat it as 0.
         */

        if (start < 0) {

            start = 0;
        }

        if (stop < 0) {

            stop = 0;
        }

        /*
         * If start is outside the list
         * or start > stop,
         * return empty array.
         */

        if (start >= listSize ||
                start > stop) {

            RespUtil.writeEmptyArray(
                    outputStream
            );

            return;
        }

        /*
         * stop cannot be greater than
         * the last index.
         */

        stop =
                Math.min(
                        stop,
                        listSize - 1
                );

        int resultSize =
                stop - start + 1;

        // RESP array header
        RespUtil.writeArrayHeader(
                outputStream,
                resultSize
        );

        // Write each element
        for (int i = start; i <= stop; i++) {

            RespUtil.writeBulkString(
                    outputStream,
                    list.get(i)
            );
        }
    }
}