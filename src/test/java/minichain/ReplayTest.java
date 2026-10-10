package minichain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.List;

class ReplayTest {

    private static KeyPair getWallet() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(1024);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void pendingTransactionCannotBeAddedTwice() {
        Blockchain chain = new Blockchain(2);
        chain.minePending("asha");
        chain.minePending("asha");
        KeyPair ashaWallet = getWallet();
        Transaction payment = Transaction.create("asha", "ravi", 20, ashaWallet.getPrivate(), ashaWallet.getPublic());
        chain.addTransaction(payment);

        assertThrows(IllegalArgumentException.class, () -> chain.addTransaction(payment));
        assertEquals(1, chain.getPending().size());
    }

    @Test
    void minedTransactionCannotBeReplayed() {
        Blockchain chain = new Blockchain(2);
        chain.minePending("asha");
        chain.minePending("asha");
        KeyPair ashaWallet = getWallet();
        chain.addTransaction(Transaction.create("asha", "ravi", 20, ashaWallet.getPrivate(), ashaWallet.getPublic()));
        chain.minePending("meera");

        // Anyone can copy a mined transaction out of the chain and submit it again.
        Transaction copy = chain.getChain().get(3).getTransactions().get(0);
        assertThrows(IllegalArgumentException.class, () -> chain.addTransaction(copy));
        chain.minePending("meera");

        assertEquals(20, chain.balanceOf("ravi"));
        assertEquals(80, chain.balanceOf("asha"));
        assertTrue(chain.isValid());
    }

    @Test
    void repeatedTransactionIdInvalidatesChain() {
        Blockchain chain = new Blockchain(2);
        chain.minePending("asha");
        chain.minePending("asha");
        KeyPair ashaWallet = getWallet();
        chain.addTransaction(Transaction.create("asha", "ravi", 20, ashaWallet.getPrivate(), ashaWallet.getPublic()));
        chain.minePending("meera");
        assertTrue(chain.isValid());

        // A properly mined block that carries the same transaction a second time.
        Transaction copy = chain.getChain().get(3).getTransactions().get(0);
        List<Transaction> replayed = new ArrayList<>();
        replayed.add(copy);
        replayed.add(new Transaction(Transaction.NETWORK, "meera", Blockchain.MINING_REWARD));
        Block block = new Block(chain.getChain().size(), System.currentTimeMillis(), replayed, chain.latestBlock().getHash());
        block.mine(2);
        chain.getChain().add(block);

        assertFalse(chain.isValid());
    }
}
