package commands;

import commands.transactions.TransactionState;
import storage.RedisData;
import utils.RespUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class EchoCommand implements Command {

    @Override
    public void execute(
            List<String> args,
            RedisData redisData,
            OutputStream outputStream,
            TransactionState transactionState
    ) throws IOException {

        if (args.size() == 1) {

            RespUtil.writeBulkString(
                    outputStream,
                    args.get(0)
            );
        }
    }
}