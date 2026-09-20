package commands;

import storage.RedisData;
import utils.RespUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

public class GetCommand implements Command {

    @Override
    public void execute(
            java.util.List<String> args,
            RedisData redisData,
            OutputStream outputStream
    ) throws IOException {

        if (args.size() != 1) {
            return;
        }

        String key = args.get(0);

        Map<String, String> data =
                redisData.getData();

        Map<String, Long> expiry =
                redisData.getExpiry();

        // Check expiry
        if (expiry.containsKey(key)) {

            long expiryTime =
                    expiry.get(key);

            if (expiryTime <= System.currentTimeMillis()) {

                data.remove(key);
                expiry.remove(key);
            }
        }

        String value = data.get(key);

        if (value == null) {

            RespUtil.writeNull(
                    outputStream
            );

        } else {

            RespUtil.writeBulkString(
                    outputStream,
                    value
            );
        }
    }
}