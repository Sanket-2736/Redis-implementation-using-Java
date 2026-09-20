package commands.transactions;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class TransactionState {

    private boolean inTransaction = false;
    @Getter
    private final List<List<String>> queuedCommands = new ArrayList<>();

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
    }

    public void queueCommand(List<String> command) {
        queuedCommands.add(new ArrayList<>(command));
    }
}