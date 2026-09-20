package commands.transactions;

import commands.Command;
import commands.CommandRegistry;
import storage.RedisData;
import utils.RespUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class ExecCommand implements Command {
    private final CommandRegistry commandRegistry;

    public ExecCommand(CommandRegistry commandRegistry){
        this.commandRegistry = commandRegistry;
    }

    @Override
    public void execute(
            List<String> args,
            RedisData redisData,
            OutputStream outputStream,
            TransactionState transactionState
    ) throws IOException {

        if (!transactionState.isInTransaction()) {

            RespUtil.writeSimpleError(
                    outputStream,
                    "EXEC without MULTI"
            );

            return;
        }

        List<List<String>> queuedCommands = transactionState.getQueuedCommands();

        RespUtil.writeArrayHeader(outputStream, queuedCommands.size());

        for(List<String> queuedCommand : queuedCommands){
            String commandName = queuedCommand.get(0).toUpperCase();

            Command command = commandRegistry.getCommand(commandName);

            if(command == null){
                RespUtil.writeSimpleError(
                        outputStream,
                        "unknown command"
                );
                continue;
            }

            List<String> commandArgs = queuedCommand.subList(1, queuedCommand.size());

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

            command.execute(commandArgs, redisData, byteArrayOutputStream, transactionState);

            outputStream.write(
                    byteArrayOutputStream.toByteArray()
            );
        }

        transactionState.endTransaction();
    }
}