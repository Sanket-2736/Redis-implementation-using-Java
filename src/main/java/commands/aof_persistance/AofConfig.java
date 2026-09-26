package commands.aof_persistance;

import lombok.Getter;

@Getter
public class AofConfig {
    private final String dir;
    private final String appendonly;
    private final String appendfilename;
    private final String appenddirname;
    private final String appendfsync;

    public AofConfig() {

        this.dir =
                System.getProperty("user.dir");

        this.appendonly =
                "no";

        this.appenddirname =
                "appendonlydir";

        this.appendfilename =
                "appendonly.aof";

        this.appendfsync =
                "everysec";
    }

    public AofConfig(
            String dir,
            String appendonly,
            String appenddirname,
            String appendfilename,
            String appendfsync
    ) {

        this.dir = dir;
        this.appendonly = appendonly;
        this.appenddirname = appenddirname;
        this.appendfilename = appendfilename;
        this.appendfsync = appendfsync;
    }
}
