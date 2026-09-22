package commands.optimistic_locking;

import commands.Command;
import commands.transactions.TransactionState;
import storage.RedisData;
import utils.RespUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class PsyncCommand implements Command {
    private static final byte[] EMPTY_RDB = new byte[] {
            (byte) 0x52, (byte) 0x45, (byte) 0x44, (byte) 0x49,
            (byte) 0x53, (byte) 0x30, (byte) 0x30, (byte) 0x31,
            (byte) 0xFA, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0xFF, (byte) 0xF0,
            (byte) 0x6E, (byte) 0xD1, (byte) 0xA3, (byte) 0x7B,
            (byte) 0xE8, (byte) 0xA0, (byte) 0x00, (byte) 0x00
    };

    @Override
    public void execute(List<String> args, RedisData redisData, OutputStream outputStream, TransactionState transactionState) throws IOException {
        String response = "FULLRESYNC" + redisData.getReplicationId() + " " + redisData.getReplicationOffset();
        RespUtil.writeBulkString(outputStream, response);

        String header = "$" + EMPTY_RDB.length + "\r\n";
        outputStream.write(header.getBytes());
        outputStream.write(EMPTY_RDB);
        outputStream.flush();
    }
}
