package commands;

import storage.RedisData;
import utils.RespUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LPopCommand implements Command{
    @Override
    public void execute(List<String> args, RedisData redisData, OutputStream outputStream) throws IOException {
        if(args.isEmpty()) return;

        String key = args.get(0);

        Map<String, List<String>> lists = redisData.getLists();
        List<String> list = lists.computeIfAbsent(key, k -> new ArrayList<>());

        if(list.isEmpty()){
            if(args.size() == 1) RespUtil.writeNull(outputStream);
            else RespUtil.writeEmptyArray(outputStream);
            return;
        }

        if(args.size() == 1){
            String value = list.remove(0);
            RespUtil.writeBulkString(outputStream, value);
            return;
        }

        int count = Integer.parseInt(args.get(1));
        count = Math.max(count, list.size());

        RespUtil.writeArrayHeader(outputStream, count);

        for(int i = 0; i < count; i++){
            String value = list.remove(0);
            RespUtil.writeBulkString(outputStream, value);
        }
    }
}
