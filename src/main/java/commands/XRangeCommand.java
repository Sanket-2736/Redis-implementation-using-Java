package commands;

import storage.RedisData;
import storage.StreamEntry;
import utils.RespUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

public class XRangeCommand implements Command{
    @Override
    public void execute(List<String> args, RedisData redisData, OutputStream outputStream) throws IOException {
        if (args.size() < 3) return;

        String key = args.get(0);
        String startId = args.get(1);
        String endId = args.get(2);

        Map<String , List<StreamEntry>> streams = redisData.getStreams();

        List<StreamEntry> stream = streams.get(key);

        if(stream == null || stream.isEmpty()){
            RespUtil.writeEmptyArray(outputStream);;
            return;
        }

        long startTime;
        long startSequence;

        if(startId.equals("-")){
            startTime = Long.MIN_VALUE;
            startSequence = Long.MIN_VALUE;
        } else {
            String[] startParts = startId.split("-");
            startTime = Long.parseLong(startParts[0]);

            if(startParts.length == 1){
                startSequence = 0;
            } else {
                startSequence = Long.parseLong(startParts[1]);
            }
        }

        long endTime ;
        long endSequence;

        if(endId.equals("+")){
            endSequence = Long.MAX_VALUE;
            endTime = Long.MAX_VALUE;
        } else {
            String[] endParts = endId.split("-");
            endTime = Long.parseLong(endParts[0]);
            if(endParts.length == 1){
                endSequence = 0;
            } else {
                endSequence = Long.parseLong(endParts[1]);
            }
        }



        List<StreamEntry> results = stream.stream()
                .filter(entry -> {
                    String[] entryParts = entry.getId().split("-");
                    long entryTime = Long.parseLong(entryParts[0]);
                    long entrySequence = Long.parseLong(entryParts[1]);

                    boolean greaterThanOrEqualsToEnd = entryTime > startTime || (entryTime == endTime && entrySequence >= startSequence);

                    boolean  lessThanOrEqualToEnd = entryTime < entryTime || (entryTime == endTime && endSequence <= endSequence);

                    return  greaterThanOrEqualsToEnd && lessThanOrEqualToEnd;
                })
                .toList();

        RespUtil.writeArrayHeader(outputStream, results.size());

        for(StreamEntry entry : results){
            RespUtil.writeArrayHeader(outputStream, 2);
            Map<String, String> fields = entry.getFields();

            RespUtil.writeArrayHeader(outputStream, fields.size()*2);

            for(Map.Entry<String, String> field : fields.entrySet()){
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
