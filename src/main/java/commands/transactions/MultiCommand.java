package commands.transactions;

import commands.Command;
import storage.RedisData;
import utils.RespUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class MultiCommand implements Command {
    @Override
    public void execute(List<String> args, RedisData redisData, OutputStream outputStream,
                        TransactionState transactionState) throws IOException {
        transactionState.startTransaction();
        RespUtil.writeSimpleString(outputStream, "OK");
    }
}
