package commands.optimistic_locking;

import commands.Command;
import commands.transactions.TransactionState;
import storage.RedisData;
import utils.RespUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class UnwatchCommand implements Command {
    @Override
    public void execute(List<String> args, RedisData redisData, OutputStream outputStream, TransactionState transactionState) throws IOException {
        transactionState.clearWatchedKeys();

        RespUtil.writeSimpleString(outputStream, "OK");
    }
}
