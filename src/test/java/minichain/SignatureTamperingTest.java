package minichain;

import static org.junit.jupiter.api.Assertions.*;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests that digital signatures, and nothing else, reject an edited payment.
 *
 * The edited block is rebuilt and mined again from scratch, so its hash, its link to the
 * previous block and its proof of work are all correct. The only check left that can
 * reject the chain is the signature check.
 */
class SignatureTamperingTest {

    private static final int DIFFICULTY = 2;

    private static KeyPair getWallet() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(1024);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // asha earns 50, pays ravi 20 with a signed payment, then "miner" mines it into block 2.
    private static Blockchain chainWithSignedPayment(KeyPair ashaWallet) {
        Blockchain chain = new Blockchain(DIFFICULTY);
        chain.minePending("asha");
        chain.addTransaction(Transaction.create("asha", "ravi", 20, ashaWallet.getPrivate(), ashaWallet.getPublic()));
        chain.minePending("miner");
        return chain;
    }

    // Replaces the latest block with a new one holding `transactions` and really mines it,
    // so the stored hash matches the contents (unlike calling mine() on an already mined block).
    private static void replaceLatestBlock(Blockchain chain, List<Transaction> transactions) {
        int last = chain.getChain().size() - 1;
        Block old = chain.getChain().get(last);
        Block rebuilt = new Block(old.getIndex(), old.getTimestamp(), transactions, old.getPreviousHash());
        rebuilt.mine(chain.getDifficulty());
        chain.getChain().set(last, rebuilt);
    }

    // Every check isValid() makes except the signature check passes for this chain.
    private static void assertHashesAndWorkAreValid(Blockchain chain) {
        String target = "0".repeat(chain.getDifficulty());
        for (int i = 1; i < chain.getChain().size(); i++) {
            Block block = chain.getChain().get(i);
            assertEquals(block.computeHash(), block.getHash(), "block " + i + " hash should match its contents");
            assertEquals(chain.getChain().get(i - 1).getHash(), block.getPreviousHash(), "block " + i + " should be linked");
            assertTrue(block.getHash().startsWith(target), "block " + i + " should meet the difficulty");
        }
    }

    @Test
    void rebuiltBlockWithUntouchedPaymentIsValid() {
        Blockchain chain = chainWithSignedPayment(getWallet());
        Block latest = chain.latestBlock();

        replaceLatestBlock(chain, new ArrayList<>(latest.getTransactions()));

        assertHashesAndWorkAreValid(chain);
        assertTrue(chain.isValid());
    }

    @Test
    void alteredAmountOnSignedPaymentIsRejectedBySignature() {
        Blockchain chain = chainWithSignedPayment(getWallet());
        List<Transaction> transactions = new ArrayList<>(chain.latestBlock().getTransactions());
        Transaction signed = transactions.get(0);
        assertEquals(20, signed.amount());

        transactions.set(0, new Transaction(signed.id(), signed.from(), signed.to(), 1_000_000, signed.senderKey(), signed.signature()));
        replaceLatestBlock(chain, transactions);

        assertHashesAndWorkAreValid(chain);
        assertFalse(chain.isValid(), "an amount edited after signing must not pass the signature check");
    }

    @Test
    void forgedSenderOnSignedPaymentIsRejectedBySignature() {
        Blockchain chain = chainWithSignedPayment(getWallet());
        List<Transaction> transactions = new ArrayList<>(chain.latestBlock().getTransactions());
        Transaction signed = transactions.get(0);
        assertEquals("asha", signed.from());

        transactions.set(0, new Transaction(signed.id(), "hacker", signed.to(), signed.amount(), signed.senderKey(), signed.signature()));
        replaceLatestBlock(chain, transactions);

        assertHashesAndWorkAreValid(chain);
        assertFalse(chain.isValid(), "a sender edited after signing must not pass the signature check");
    }

    @Test
    void unsignedPaymentInMinedBlockIsRejectedBySignature() {
        Blockchain chain = chainWithSignedPayment(getWallet());
        List<Transaction> transactions = new ArrayList<>(chain.latestBlock().getTransactions());
        Transaction signed = transactions.get(0);

        transactions.set(0, new Transaction(signed.id(), signed.from(), signed.to(), signed.amount(), null, null));
        replaceLatestBlock(chain, transactions);

        assertHashesAndWorkAreValid(chain);
        assertFalse(chain.isValid(), "a payment with no signature must not pass the signature check");
    }

    @Test
    void addTransactionRejectsAlteredAmount() {
        KeyPair asha = getWallet();
        Blockchain chain = new Blockchain(DIFFICULTY);
        chain.minePending("asha");
        Transaction signed = Transaction.create("asha", "ravi", 10, asha.getPrivate(), asha.getPublic());
        // 40 is within asha's balance of 50, so only the signature can reject this
        Transaction altered = new Transaction(signed.id(), signed.from(), signed.to(), 40, signed.senderKey(), signed.signature());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> chain.addTransaction(altered));

        assertTrue(error.getMessage().toLowerCase().contains("signature"), error.getMessage());
        assertTrue(chain.getPending().isEmpty());
    }

    @Test
    void addTransactionRejectsUnsignedPayment() {
        Blockchain chain = new Blockchain(DIFFICULTY);
        chain.minePending("asha");
        Transaction unsigned = new Transaction("some-id", "asha", "ravi", 10, null, null);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> chain.addTransaction(unsigned));

        assertTrue(error.getMessage().toLowerCase().contains("signature"), error.getMessage());
        assertTrue(chain.getPending().isEmpty());
    }
}
