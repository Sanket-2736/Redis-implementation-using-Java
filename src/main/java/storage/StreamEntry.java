package storage;

import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

public class StreamEntry {
    @Getter
    private final String id;
    @Getter
    private final Map<String, String> fields;

    public StreamEntry(String id){
        this.id = id;
        this.fields = new LinkedHashMap<>();
    }

    public void addField(String key, String val){
        fields.put(key, val);
    }
}
