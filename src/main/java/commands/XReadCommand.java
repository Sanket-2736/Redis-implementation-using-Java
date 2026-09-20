package commands;

import storage.RedisData;
import storage.StreamEntry;
import utils.RespUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

public class XReadCommand implements Command{
    @Override
    public void execute(List<String> args, RedisData redisData, OutputStream outputStream) throws IOException {
        if (args.size() < 3) return;

        String key = args.get(1);
        String startId = args.get(2);

        Map<String, List<StreamEntry>> streams = redisData.getStreams();

        List<StreamEntry> stream = streams.get(key);

        if(stream == null || stream.isEmpty()){
            RespUtil.writeEmptyArray(outputStream);
            return;
        }

        String[] parts = startId.split("-");

        long startTime = Long.parseLong(parts[0]);
        long startSequence;

        if(parts.length == 2){
            startSequence = 0;
        } else {
            startSequence = Long.parseLong(parts[1]);
        }

        List<StreamEntry> result = stream.stream()
                .filter(entry -> {
                    String[] entryParts = entry.getId().split("-");
                    long entryTime = Long.parseLong(entryParts[0]);
                    long entrySequence = Long.parseLong(entryParts[1]);

                    return entryTime > startTime || (
                            entryTime == startTime && entrySequence > startSequence
                            );
                })
                .toList();

        if(result.isEmpty()){
            RespUtil.writeArrayHeader(outputStream, 0);
            return;
        }

        RespUtil.writeArrayHeader(outputStream, 1);
        RespUtil.writeArrayHeader(outputStream, 2);

        RespUtil.writeBulkString(outputStream, key);

        RespUtil.writeArrayHeader(outputStream, result.size());

        for(StreamEntry entry : result){
            RespUtil.writeArrayHeader(outputStream, 2);

            RespUtil.writeBulkString(outputStream, entry.getId());

            Map<String, String> fields = entry.getFields();

            RespUtil.writeArrayHeader(outputStream, fields.size() * 2);

            for (Map.Entry<String, String> field :
                    fields.entrySet()) {

                RespUtil.writeBulkString(
                        outputStream,
                        field.getKey()
                );

                RespUtil.writeBulkString(
                        outputStream,
                        field.getValue()
                );
            }
        }
    }
}
