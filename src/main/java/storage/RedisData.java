package storage;

import java.util.List;
import java.util.Map;

public class RedisData {

    private final Map<String, String> data;
    private final Map<String, Long> expiry;
    private final Map<String, List<String>> lists;

    public RedisData(
            Map<String, String> data,
            Map<String, Long> expiry,
            Map<String, List<String>> lists
    ) {
        this.data = data;
        this.expiry = expiry;
        this.lists = lists;
    }

    public Map<String, String> getData() {
        return data;
    }

    public Map<String, Long> getExpiry() {
        return expiry;
    }

    public Map<String, List<String>> getLists() {
        return lists;
    }
}