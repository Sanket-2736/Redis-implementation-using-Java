package commands;

import commands.optimistic_locking.InfoCommand;
import commands.optimistic_locking.UnwatchCommand;
import commands.optimistic_locking.WatchCommand;
import commands.transactions.DiscardCommand;
import commands.transactions.ExecCommand;
import commands.transactions.IncrCommand;
import commands.transactions.MultiCommand;

import java.util.HashMap;
import java.util.Map;

public class CommandRegistry {

    private final Map<String, Command> commands = new HashMap<>();

    public CommandRegistry() {

        commands.put("PING", new PingCommand());
        commands.put("ECHO", new EchoCommand());
        commands.put("SET", new SetCommand());
        commands.put("GET", new GetCommand());

        commands.put("RPUSH", new RPushCommand());
        commands.put("LPUSH", new LPushCommand());
        commands.put("LRANGE", new LRangeCommand());
        commands.put("LLEN", new LLenCommand());
        commands.put("LPOP", new LPopCommand());
        commands.put("BLPOP", new BLPopCommand());

        commands.put("TYPE", new TypeCommand());

//        Stream commands
        commands.put("XADD", new XAddCommand());
        commands.put("XRANGE", new XRangeCommand());
        commands.put("XREAD", new XReadCommand());

//        transaction commands
        commands.put("INCR", new IncrCommand());
        commands.put("MULTI", new MultiCommand());
        commands.put("EXEC", new ExecCommand(this));
        commands.put("DISCARD", new DiscardCommand());

//        optimistic locking
        commands.put("WATCH", new WatchCommand());
        commands.put("UNWATCH", new UnwatchCommand());
        commands.put("INFO", new InfoCommand());

    }

    public Command getCommand(String command) {
        return commands.get(command.toUpperCase());
    }
}