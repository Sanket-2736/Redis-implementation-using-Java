package commands;

import storage.RedisData;
import utils.RespUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RPushCommand implements Command {

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

        Map<String, List<String>> lists = redisData.getLists();

        synchronized (redisData) {

            List<String> list =
                    lists.computeIfAbsent(key, k -> new ArrayList<>());

            for (int i = 1; i < args.size(); i++) {
                list.add(args.get(i));
            }

            redisData.notifyAll();

            RespUtil.writeInteger(outputStream, list.size());
        }
    }
}