package commands.optimistic_locking;

import commands.Command;
import commands.transactions.TransactionState;
import storage.RedisData;
import utils.RespUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class WatchCommand implements Command {
    @Override
    public void execute(List<String> args, RedisData redisData, OutputStream outputStream, TransactionState transactionState) throws IOException {
        if(transactionState.isInTransaction()){
            RespUtil.writeSimpleError(outputStream, "WATCH inside MULTI is not allowed");
        }

        for(String key : args){
            long version = redisData.getKeyVersion(key);
            transactionState.watchKey(key, version);
        }

        RespUtil.writeSimpleString(outputStream, "OK");
    }
}
