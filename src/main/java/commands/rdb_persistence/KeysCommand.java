package commands.rdb_persistence;

import commands.Command;
import commands.transactions.TransactionState;
import storage.RedisData;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class KeysCommand implements Command {

    @Override
    public void execute(
            List<String> args,
            RedisData redisData,
            OutputStream outputStream,
            TransactionState transactionState
    ) throws IOException {

        if (args.isEmpty()) {
            outputStream.write(
                    "-ERR wrong number of arguments for 'keys' command\r\n"
                            .getBytes(StandardCharsets.UTF_8)
            );
            outputStream.flush();
            return;
        }

        String pattern = args.get(0);

        List<String> keys = new ArrayList<>();

        // This stage only requires KEYS *
        if (pattern.equals("*")) {
            keys.addAll(redisData.getData().keySet());
        }

        StringBuilder response = new StringBuilder();

        // RESP array
        response
                .append("*")
                .append(keys.size())
                .append("\r\n");

        // Each key is a RESP bulk string
        for (String key : keys) {

            byte[] keyBytes =
                    key.getBytes(StandardCharsets.UTF_8);

            response
                    .append("$")
                    .append(keyBytes.length)
                    .append("\r\n")
                    .append(key)
                    .append("\r\n");
        }

        outputStream.write(
                response
                        .toString()
                        .getBytes(StandardCharsets.UTF_8)
        );

        outputStream.flush();
    }
}