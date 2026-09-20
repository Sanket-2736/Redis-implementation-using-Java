package commands;

import commands.transactions.TransactionState;
import storage.RedisData;
import utils.RespUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

public class BLPopCommand implements Command{
    @Override
    public void execute(List<String> args, RedisData redisData, OutputStream outputStream,
                        TransactionState transactionState) throws IOException {
        if(args.size() < 2) return;

        String key = args.get(0);
        double timeout = Double.parseDouble(args.get(1));

        Map<String, List<String >> lists = redisData.getLists();

        synchronized (redisData){
            while (true){
                List<String> list = lists.get(key);

                if(list != null && !list.isEmpty()){
                    String value = list.remove(0);
                    RespUtil.writeArrayHeader(outputStream, 2);
                    RespUtil.writeBulkString(outputStream, key);
                    RespUtil.writeBulkString(outputStream, value);
                    return;
                }

                if (timeout == 0){
                    try{
                        redisData.wait();
                    } catch (InterruptedException e){
                        Thread.currentThread().interrupt();
                        return;
                    }
                } else {
                    long timeoutMillis = (long)(timeout*1000);

                    try {
                        redisData.wait(timeoutMillis);
                    } catch (InterruptedException e){
                        Thread.currentThread().interrupt();
                        return;
                    }

                    list = lists.get(key);

                    if(list == null || list.isEmpty()){
                        RespUtil.writeEmptyArray(outputStream);
                        return;
                    }
                }
            }
        }
    }
}
