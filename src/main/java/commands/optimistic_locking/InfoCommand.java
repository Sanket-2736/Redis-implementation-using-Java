package commands.optimistic_locking;

import commands.Command;
import commands.transactions.TransactionState;
import storage.RedisData;
import utils.RespUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class InfoCommand implements Command {

    @Override
    public void execute(
            List<String> args,
            RedisData redisData,
            OutputStream outputStream,
            TransactionState transactionState
    ) throws IOException {

        if (!args.isEmpty()
                && args.get(0).equalsIgnoreCase("replication")) {

            String response =
                    "# Replication\r\n" +
                            "role:" + redisData.getRole() + "\r\n" +
                            "master_replid:" + redisData.getReplicationId() + "\r\n" +
                            "master_repl_offset:" + redisData.getReplicationOffset() + "\r\n";

            RespUtil.writeBulkString(outputStream, response);
            return;
        }

        String response =
                "role:" + redisData.getRole() + "\r\n";

        RespUtil.writeBulkString(outputStream, response);
    }
}