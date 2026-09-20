package commands;

import commands.transactions.TransactionState;
import storage.RedisData;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public interface Command {

    void execute(
            List<String> args,
            RedisData redisData,
            OutputStream outputStream,
            TransactionState transactionState
    ) throws IOException;
}