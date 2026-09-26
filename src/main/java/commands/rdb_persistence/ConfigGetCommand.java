package commands.rdb_persistence;

import commands.Command;
import commands.aof_persistance.AofConfig;
import commands.transactions.TransactionState;
import storage.RedisData;
import utils.RespUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class ConfigGetCommand implements Command {

    private final RdbConfig rdbConfig;
    private final AofConfig aofConfig;

    public ConfigGetCommand(
            RdbConfig rdbConfig,
            AofConfig aofConfig
    ) {
        this.rdbConfig = rdbConfig;
        this.aofConfig = aofConfig;
    }

    @Override
    public void execute(
            List<String> args,
            RedisData redisData,
            OutputStream outputStream,
            TransactionState transactionState
    ) throws IOException {

        if (args.isEmpty()) {
            RespUtil.writeEmptyArray(outputStream);
            outputStream.flush();
            return;
        }

        String parameter =
                args.get(0).toLowerCase();

        String value =
                getConfigValue(parameter);

        /*
         * Unknown configuration option
         */
        if (value == null) {
            RespUtil.writeEmptyArray(outputStream);
            outputStream.flush();
            return;
        }

        /*
         * RESP:
         *
         * *2
         * $<length>
         * <parameter>
         * $<length>
         * <value>
         */
        RespUtil.writeArrayHeader(
                outputStream,
                2
        );

        RespUtil.writeBulkString(
                outputStream,
                parameter
        );

        RespUtil.writeBulkString(
                outputStream,
                value
        );

        outputStream.flush();
    }

    private String getConfigValue(
            String option
    ) {

        return switch (option) {

            /*
             * RDB configuration
             */
            case "dir" ->
                    rdbConfig.getDir();

            case "dbfilename" ->
                    rdbConfig.getDbfilename();

            /*
             * AOF configuration
             */
            case "appendonly" ->
                    aofConfig.getAppendonly();

            case "appenddirname" ->
                    aofConfig.getAppenddirname();

            case "appendfilename" ->
                    aofConfig.getAppendfilename();

            case "appendfsync" ->
                    aofConfig.getAppendfsync();

            default ->
                    null;
        };
    }
}