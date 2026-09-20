package commands;

import storage.RedisData;
import utils.RespUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

public class SetCommand implements Command {

    @Override
    public void execute(
            List<String> args,
            RedisData redisData,
            OutputStream outputStream
    ) throws IOException {

        if (args.size() < 2) {
            return;
        }

        String key = args.get(0);
        String value = args.get(1);

        Map<String, String> data =
                redisData.getData();

        Map<String, Long> expiry =
                redisData.getExpiry();

        data.put(key, value);

        // Remove old expiry
        expiry.remove(key);

        // SET key value EX/PX time
        if (args.size() >= 4) {

            String option = args.get(2);
            String time = args.get(3);

            long expiryTime =
                    System.currentTimeMillis();

            if (option.equalsIgnoreCase("EX")) {

                expiryTime +=
                        Long.parseLong(time) * 1000;

                expiry.put(key, expiryTime);

            } else if (option.equalsIgnoreCase("PX")) {

                expiryTime +=
                        Long.parseLong(time);

                expiry.put(key, expiryTime);
            }
        }

        RespUtil.writeSimpleString(
                outputStream,
                "OK"
        );
    }
}