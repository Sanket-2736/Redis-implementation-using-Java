package commands;

import java.util.HashMap;
import java.util.Map;

public class CommandRegistry {

    private final Map<String, Command> commands =
            new HashMap<>();

    public CommandRegistry() {

        commands.put("PING", new PingCommand());
        commands.put("ECHO", new EchoCommand());
        commands.put("LPOP", new LPopCommand());
        commands.put("SET", new SetCommand());
        commands.put("GET", new GetCommand());
        commands.put("BLPOP", new BLPopCommand());
        commands.put("RPUSH", new RPushCommand());
        commands.put("LRANGE", new LRangeCommand());
        commands.put("LPUSH", new LPushCommand());
        commands.put("LLEN", new LLenCommand());
    }

    public Command getCommand(String command) {

        return commands.get(
                command.toUpperCase()
        );
    }
}