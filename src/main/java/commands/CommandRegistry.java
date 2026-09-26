package commands;

import commands.aof_persistance.AofConfig;
import commands.optimistic_locking.*;
import commands.rdb_persistence.ConfigGetCommand;
import commands.rdb_persistence.KeysCommand;
import commands.rdb_persistence.RdbConfig;
import commands.transactions.DiscardCommand;
import commands.transactions.ExecCommand;
import commands.transactions.IncrCommand;
import commands.transactions.MultiCommand;
import lombok.Getter;
import replications.ReplicationManager;

import java.util.HashMap;
import java.util.Map;

public class CommandRegistry {

    private final Map<String, Command> commands =
            new HashMap<>();

    @Getter
    private final ReplicationManager replicationManager;


    public CommandRegistry(
            RdbConfig rdbConfig,
            AofConfig aofConfig
    ) {

        replicationManager = new ReplicationManager();

        commands.put(
                "PING",
                new PingCommand()
        );

        commands.put(
                "ECHO",
                new EchoCommand()
        );

        commands.put(
                "SET",
                new SetCommand()
        );

        commands.put(
                "GET",
                new GetCommand()
        );

        commands.put(
                "RPUSH",
                new RPushCommand()
        );

        commands.put(
                "LPUSH",
                new LPushCommand()
        );

        commands.put(
                "LRANGE",
                new LRangeCommand()
        );

        commands.put(
                "LLEN",
                new LLenCommand()
        );

        commands.put(
                "LPOP",
                new LPopCommand()
        );

        commands.put(
                "BLPOP",
                new BLPopCommand()
        );

        commands.put(
                "TYPE",
                new TypeCommand()
        );

        commands.put(
                "XADD",
                new XAddCommand()
        );

        commands.put(
                "XRANGE",
                new XRangeCommand()
        );

        commands.put(
                "XREAD",
                new XReadCommand()
        );

        commands.put(
                "INCR",
                new IncrCommand()
        );

        commands.put(
                "MULTI",
                new MultiCommand()
        );

        commands.put(
                "EXEC",
                new ExecCommand(this)
        );

        commands.put(
                "DISCARD",
                new DiscardCommand()
        );

        commands.put(
                "WATCH",
                new WatchCommand()
        );

        commands.put(
                "UNWATCH",
                new UnwatchCommand()
        );

        commands.put(
                "INFO",
                new InfoCommand()
        );

        commands.put(
                "REPLCONF",
                new ReplConfCommand()
        );

        commands.put(
                "PSYNC",
                new PsyncCommand()
        );

        commands.put(
                "WAIT",
                new WaitCommand(
                        replicationManager
                )
        );

        commands.put(
                "KEYS",
                new KeysCommand()
        );


        commands.put(
                "CONFIG",
                new ConfigGetCommand(
                        rdbConfig,
                        aofConfig
                )
        );
    }


    public Command getCommand(
            String command
    ) {

        return commands.get(
                command.toUpperCase()
        );
    }
}