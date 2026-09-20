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
}