package commands.transactions;

import commands.Command;
import storage.RedisData;
import utils.RespUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

public class IncrCommand implements Command {

    @Override
    public void execute(List<String> args, RedisData redisData, OutputStream outputStream,
                        TransactionState transactionState) throws IOException {
        String key = args.get(0);
        Map<String, String > data = redisData.getData();

        if(!data.containsKey(key)) {
            data.put(key, String.valueOf(1));
            redisData.markModified(key);
            RespUtil.writeInteger(outputStream, 1);
        } else {
            String val = data.get(key);

            try {
                int num = Integer.parseInt(val);
                num++;
                data.put(key, String.valueOf(num));
                redisData.markModified(key);
                RespUtil.writeInteger(outputStream, num);
            } catch (NumberFormatException e) {
                RespUtil.writeSimpleError(
                        outputStream,
                        "value is not an integer or out of range"
                );
            }
        }
    }
}
