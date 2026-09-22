package commands.optimistic_locking;
import commands.Command;
import commands.transactions.TransactionState;
import replications.ReplicationManager;
import storage.RedisData;
import replications.ReplicationManager;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class WaitCommand implements Command {

    private final ReplicationManager replicationManager;

    public WaitCommand(
            ReplicationManager replicationManager
    ) {
        this.replicationManager =
                replicationManager;
    }

    @Override
    public void execute(
            List<String> args,
            RedisData redisData,
            OutputStream outputStream,
            TransactionState transactionState
    ) throws IOException {

        int requiredReplicas =
                Integer.parseInt(args.get(0));

        long timeout =
                Long.parseLong(args.get(1));

        int acknowledged =
                replicationManager.waitForReplicas(
                        requiredReplicas,
                        timeout
                );

        outputStream.write(
                (":" + acknowledged + "\r\n")
                        .getBytes()
        );

        outputStream.flush();
    }
}