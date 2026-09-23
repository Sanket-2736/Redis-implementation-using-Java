package commands.rdb_persistence;

import lombok.Getter;

@Getter
public class RdbConfig {
    private final String dir;
    private final String dbfilename;

    public RdbConfig(String dir, String dbfilename) {
        this.dir = dir;
        this.dbfilename = dbfilename;
    }
}
