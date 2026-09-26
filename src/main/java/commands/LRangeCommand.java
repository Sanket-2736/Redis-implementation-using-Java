package commands;

import commands.transactions.TransactionState;
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
            OutputStream outputStream,
            TransactionState transactionState
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
        if (list == null) {
            RespUtil.writeEmptyArray(
                    outputStream
            );
            return;
        }
        int listSize = list.size();
        if (start < 0) {
            start = listSize + start;
        }
        if (stop < 0) {
            stop = listSize + stop;
        }
        if (start < 0) {
            start = 0;
        }
        if (stop < 0) {
            stop = 0;
        }
        if (start >= listSize ||
                start > stop) {
            RespUtil.writeEmptyArray(
                    outputStream
            );
            return;
        }
        stop =
                Math.min(
                        stop,
                        listSize - 1
                );
        int resultSize =
                stop - start + 1;
        RespUtil.writeArrayHeader(
                outputStream,
                resultSize
        );
        for (int i = start; i <= stop; i++) {
            RespUtil.writeBulkString(
                    outputStream,
                    list.get(i)
            );
        }
    }
}