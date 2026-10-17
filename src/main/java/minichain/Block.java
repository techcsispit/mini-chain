package minichain;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;


// bump version only for incompatible changes, adding a field is safe
public class Block implements Serializable {
    private static final long serialVersionUID = 4L;

    private final int index;
    private final long timestamp;
    private final List<Transaction> transactions;
    private String merkleRoot;
    private String previousHash;
    private int nonce = 0;
    private String hash;

    public Block(int index, long timestamp, List<Transaction> transactions, String previousHash) {
        this.index = index;
        this.timestamp = timestamp;
        this.transactions = transactions;
        this.merkleRoot = computeMerkleRoot(transactions);
        this.previousHash = previousHash;
        this.hash = computeHash();
    }

    public static String computeMerkleRoot(List<Transaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return sha256("");
        }
        List<String> tree = new ArrayList<>();
        for (Transaction tx : transactions) {
            tree.add(tx.hash());
        }
        while (tree.size() > 1) {
            List<String> nextLevel = new ArrayList<>();
            for (int i = 0; i < tree.size(); i += 2) {
                String left = tree.get(i);
                String right = (i + 1 < tree.size()) ? tree.get(i + 1) : left;
                nextLevel.add(sha256(left + right));
            }
            tree = nextLevel;
        }
        return tree.get(0);
    }

    public String computeHash() {
        String content = index + "|" + timestamp + "|" + previousHash + "|" + nonce + "|" + merkleRoot;
        return sha256(content);
    }

    // Proof of work: keep bumping the nonce until the hash starts with `difficulty` zeros.
    public void mine(int difficulty) {
        String target = "0".repeat(difficulty);
        while (!hash.startsWith(target)) {
            nonce++;
            hash = computeHash();
        }
    }

    static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public int getIndex() { return index; }
    public long getTimestamp() { return timestamp; }
    public List<Transaction> getTransactions() { return transactions; }
    public String getMerkleRoot() { return merkleRoot; }
    public String getPreviousHash() { return previousHash; }
    public int getNonce() { return nonce; }
    public String getHash() { return hash; }

    // Used by tests to simulate someone editing the chain.
    void tamperPreviousHash(String previousHash) {
        this.previousHash = previousHash;
        this.hash = computeHash();
    }

    void tamperMerkleRoot(String merkleRoot) {
        this.merkleRoot = merkleRoot;
        this.hash = computeHash();
    }
}
