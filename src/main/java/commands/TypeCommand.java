package commands;

import storage.RedisData;
import utils.RespUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class TypeCommand implements Command {

    @Override
    public void execute(
            List<String> args,
            RedisData redisData,
            OutputStream outputStream
    ) throws IOException {

        if (args.size() != 1) {
            return;
        }

        String key = args.get(0);

        // Check string values
        if (redisData.getData().containsKey(key)) {
            RespUtil.writeSimpleString(outputStream, "string");
            return;
        }

        if(redisData.getStreams().containsKey(key)){
            RespUtil.writeSimpleString(outputStream, "stream");
            return;
        }

        if(redisData.getLists().containsKey(key)){
            RespUtil.writeSimpleString(outputStream, "list");
            return;
        }

        // Key doesn't exist
        RespUtil.writeSimpleString(outputStream, "none");
    }
}