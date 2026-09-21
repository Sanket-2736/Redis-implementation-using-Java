package storage;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
public class RedisData {

    private final Map<String, String> data;
    private final Map<String, Long> expiry;
    private final Map<String, List<String>> lists;
    private final Map<String, List<StreamEntry>> streams;
    private final Map<String, Long> keyVersions;
    private final String role;
    private final String replicationId;
    private final long replicationOffset;

    public synchronized void markModified(String key){
        keyVersions.put(
                key,
                1L + keyVersions.getOrDefault(key, 0L)
        );
    }

    public synchronized long getKeyVersion(String key){
        return keyVersions.getOrDefault(key, 0L);
    }
}