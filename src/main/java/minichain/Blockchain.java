package minichain;

import java.io.Serializable;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// bump version only for incompatible changes, adding a field is safe
public class Blockchain implements Serializable {
    private static final long serialVersionUID = 2L;

    public static final int MINING_REWARD = 50;

    private final int difficulty;
    private final List<Block> chain = new ArrayList<>();
    private final List<Transaction> pending = new ArrayList<>();
    private final Map<String, PublicKey> knownKeys = new HashMap<>();

    public Blockchain(int difficulty) {
        this.difficulty = difficulty;
        Block genesis = new Block(0, System.currentTimeMillis(), new ArrayList<>(), "0");
        genesis.mine(difficulty);
        chain.add(genesis);
    }

    public List<Block> getChain() { return chain; }
    public List<Transaction> getPending() { return pending; }
    public int getDifficulty() { return difficulty; }
    public Block latestBlock() { return chain.get(chain.size() - 1); }

    public void addTransaction(Transaction tx) {
        if (!tx.hasValidSignature()) {
            throw new IllegalArgumentException("Transaction signature is invalid.");
        }
        if (!Transaction.NETWORK.equals(tx.from())) {
            PublicKey known = knownKeys.get(tx.from());
            if (known == null) {
                knownKeys.put(tx.from(), tx.senderKey());
            } else if (!known.equals(tx.senderKey())) {
                throw new IllegalArgumentException("Transaction uses a different key than the registered key for sender: " + tx.from());
            }
        }
        if (tx.from() == null || tx.from().isBlank() || tx.to() == null || tx.to().isBlank()) {
            throw new IllegalArgumentException("A payment needs a sender and a receiver.");
        }
        if (tx.from().equals(tx.to())) {
            throw new IllegalArgumentException("You can't pay yourself.");
        }
        if (tx.amount() <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero.");
        }
        if (balanceOf(tx.from()) < tx.amount()) {
            throw new IllegalArgumentException(tx.from() + " doesn't have enough coins.");
        }
        pending.add(tx);
    }

    public Block minePending(String miner) {
        List<Transaction> transactions = new ArrayList<>(pending);
        transactions.add(new Transaction(Transaction.NETWORK, miner, MINING_REWARD));
        Block block = new Block(chain.size(), System.currentTimeMillis(), transactions, latestBlock().getHash());
        block.mine(difficulty);
        chain.add(block);
        pending.clear();
        return block;
    }

    public Map<String, Integer> balances() {
        Map<String, Integer> balances = new HashMap<>();
        for (Block block : chain) {
            for (Transaction tx : block.getTransactions()) {
                if (!tx.from().equals(Transaction.NETWORK)) {
                    balances.merge(tx.from(), -tx.amount(), Integer::sum);
                }
                balances.merge(tx.to(), tx.amount(), Integer::sum);
            }
        }

        // adding pending transactions to the balances
        for (Transaction tx : pending) {
            balances.merge(tx.from(), -tx.amount(), Integer::sum);
            balances.merge(tx.to(), tx.amount(), Integer::sum);
        }
        return balances;
    }

    public int balanceOf(String person) {
        return balances().get(person);
    }

    public boolean isValid() {
        String target = "0".repeat(difficulty);
        Map<String, PublicKey> keys = new HashMap<>();
        for (int i = 1; i < chain.size(); i++) {
            Block block = chain.get(i);
            Block previous = chain.get(i - 1);
            if (!block.getHash().equals(block.computeHash())) return false;
            if (!block.getPreviousHash().equals(previous.getHash())) return false;
            if (!block.getHash().startsWith(target)) return false;
            for (Transaction tx : block.getTransactions()) {
                if (!tx.hasValidSignature()) return false;
                if (!Transaction.NETWORK.equals(tx.from())) {
                    PublicKey known = keys.get(tx.from());
                    if (known == null) {
                        keys.put(tx.from(), tx.senderKey());
                    } else if (!known.equals(tx.senderKey())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
