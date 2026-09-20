package commands;

import commands.transactions.TransactionState;
import storage.RedisData;
import utils.RespUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LLenCommand implements Command{

    @Override
    public void execute(List<String> args, RedisData redisData, OutputStream outputStream,
                        TransactionState transactionState) throws IOException {
        if(args.size() != 1) return;

        String key = args.get(0);

        Map<String, List<String >> lists = redisData.getLists();

        List<String> list = lists.computeIfAbsent(key, k -> new ArrayList<>());

        if(list.isEmpty()){
            RespUtil.writeInteger(outputStream, 0);
            return;
        }
        RespUtil.writeInteger(outputStream, list.size());
    }
}
