package commands.rdb_persistence;

import commands.Command;
import commands.transactions.TransactionState;
import storage.RedisData;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ConfigGetCommand implements Command {
    private final RdbConfig rdbConfig;

    public ConfigGetCommand(RdbConfig rdbConfig){
        this.rdbConfig = rdbConfig;
    }

    @Override
    public void execute(List<String> args, RedisData redisData, OutputStream outputStream, TransactionState transactionState) throws IOException {
        if(args.isEmpty()){
            outputStream.write(
                    "*0\r\n".getBytes(StandardCharsets.UTF_8)
            );
            return;
        }

        String parameter = args.get(0).toLowerCase();
        String value;

        switch (parameter){
            case "dir":
                value = rdbConfig.getDir();
                break;

            case "dbfilename" :
                value = rdbConfig.getDbfilename();
                break;

            default:
                outputStream.write(
                        "*0\r\n".getBytes(StandardCharsets.UTF_8)
                );
                return;
        }

        byte[] parameterBytes = parameter.getBytes(StandardCharsets.UTF_8);
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);



        String response = "*2\r\n" + "$" + parameterBytes.length + "\r\n" + parameter + "\r\n" + "$" + valueBytes.length + "\r\n" + value + "\r\n";

        outputStream.write(response.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }
}
