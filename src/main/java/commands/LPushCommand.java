package commands;

import commands.transactions.TransactionState;
import storage.RedisData;
import utils.RespUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LPushCommand implements Command{
    @Override
    public void execute(List<String> args, RedisData redisData, OutputStream outputStream,
                        TransactionState transactionState) throws IOException {
        if(args.size() < 2) return;

        String key = args.get(0);
        Map<String, List<String>> lists = redisData.getLists();

        List<String > list = lists.computeIfAbsent(key, k -> new ArrayList<>());
        for(int i = 1; i < args.size(); i++){
            list.add(args.get(i));
        }
        redisData.notifyAll();

        RespUtil.writeArrayHeader(outputStream, list.size());
    }
}
