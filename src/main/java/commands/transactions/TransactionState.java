package commands.transactions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TransactionState {

    private boolean inTransaction = false;

    private final List<List<String>> queuedCommands = new ArrayList<>();

    private final Map<String, Long> watchedKeys = new HashMap<>();

    public boolean isInTransaction() {
        return inTransaction;
    }

    public void startTransaction() {
        inTransaction = true;
        queuedCommands.clear();
    }

    public void endTransaction() {
        inTransaction = false;
        queuedCommands.clear();
        watchedKeys.clear();
    }

    public void queueCommand(List<String> command) {
        queuedCommands.add(new ArrayList<>(command));
    }

    public List<List<String>> getQueuedCommands() {
        return queuedCommands;
    }

    public void watchKey(String key, long version) {
        watchedKeys.put(key, version);
    }

    public Map<String, Long> getWatchedKeys() {
        return watchedKeys;
    }

    public void clearWatchedKeys() {
        watchedKeys.clear();
    }
}