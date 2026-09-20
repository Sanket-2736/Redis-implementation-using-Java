package commands;

import storage.RedisData;
import utils.RespUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class PingCommand implements Command {

    @Override
    public void execute(
            List<String> args,
            RedisData redisData,
            OutputStream outputStream
    ) throws IOException {

        if (args.size() == 0) {

            RespUtil.writeSimpleString(
                    outputStream,
                    "PONG"
            );

        } else {

            RespUtil.writeBulkString(
                    outputStream,
                    args.get(0)
            );
        }
    }
}