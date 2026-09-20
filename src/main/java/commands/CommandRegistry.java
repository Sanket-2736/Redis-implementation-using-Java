package commands;

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

        commands.put("XADD", new XAddCommand());
        commands.put("XRANGE", new XRangeCommand());
        commands.put("XREAD", new XReadCommand());
    }

    public Command getCommand(String command) {
        return commands.get(command.toUpperCase());
    }
}