package minichain;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.security.KeyPair;
import java.security.KeyPairGenerator;

class BlockchainTest {

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
    void startsWithGenesisBlock() {
        Blockchain chain = new Blockchain(2);
        assertEquals(1, chain.getChain().size());
        assertEquals("0", chain.getChain().get(0).getPreviousHash());
    }

    @Test
    void minerGetsReward() {
        Blockchain chain = new Blockchain(2);
        chain.minePending("asha");
        assertEquals(Blockchain.MINING_REWARD, chain.balanceOf("asha"));
    }

    @Test
    void newPersonHasZeroBalance() {
        Blockchain chain = new Blockchain(2);

        assertEquals(0, chain.balanceOf("ravi"));
    }

    @Test
    void paymentsMoveCoins() {
        Blockchain chain = new Blockchain(2);
        chain.minePending("asha");
        KeyPair ashaWallet = getWallet();
        chain.addTransaction(Transaction.create("asha", "ravi", 20, ashaWallet.getPrivate(), ashaWallet.getPublic()));
        chain.minePending("meera");
        assertEquals(30, chain.balanceOf("asha"));
        assertEquals(20, chain.balanceOf("ravi"));
        assertEquals(50, chain.balanceOf("meera"));
    }

    @Test
    void cannotSpendMoreThanYouHave() {
        Blockchain chain = new Blockchain(2);
        chain.minePending("asha");
        KeyPair ashaWallet = getWallet();
        assertThrows(IllegalArgumentException.class, () -> chain.addTransaction(Transaction.create("asha", "ravi", 60, ashaWallet.getPrivate(), ashaWallet.getPublic())));
    }

    @Test
    void cannotMakeNegativePayment() {
        Blockchain chain = new Blockchain(2);
        chain.minePending("asha");
        KeyPair ashaWallet = getWallet();

        assertThrows(IllegalArgumentException.class, () ->
            chain.addTransaction(
                Transaction.create("asha", "ravi", -10,
                    ashaWallet.getPrivate(), ashaWallet.getPublic())
            )
        );
    }

    @Test
    void cannotPayYourself() {
        Blockchain chain = new Blockchain(2);
        chain.minePending("asha");
        KeyPair ashaWallet = getWallet();
        assertThrows(IllegalArgumentException.class, () -> chain.addTransaction(Transaction.create("asha", "asha", 5, ashaWallet.getPrivate(), ashaWallet.getPublic())));
    }

    @Test
    void cannotSpendQueuedCoins() {
        Blockchain chain = new Blockchain(2);
        chain.minePending("asha");
        KeyPair ashaWallet = getWallet();
        chain.addTransaction(Transaction.create("asha", "ravi", 40, ashaWallet.getPrivate(), ashaWallet.getPublic()));
        assertThrows(IllegalArgumentException.class, () -> chain.addTransaction(Transaction.create("asha", "meera", 40, ashaWallet.getPrivate(), ashaWallet.getPublic())));
        assertEquals(1, chain.getPending().size());
    }

    @Test
    void blocksAreMinedAndLinked() {
        Blockchain chain = new Blockchain(3);
        chain.minePending("asha");
        chain.minePending("ravi");
        for (int i = 1; i < chain.getChain().size(); i++) {
            Block block = chain.getChain().get(i);
            assertTrue(block.getHash().startsWith("000"));
            assertEquals(chain.getChain().get(i - 1).getHash(), block.getPreviousHash());
        }
        assertTrue(chain.isValid());
    }

    @Test
    void tamperedAmountInvalidatesChain() {
        Blockchain chain = new Blockchain(2);
        chain.minePending("asha");
        Block block = chain.getChain().get(1);
        Transaction original = block.getTransactions().get(0);
        
        // Hacker changes the amount to 1,000,000 and keeps the original signature
        block.getTransactions().set(0, new Transaction(original.id(), original.from(), original.to(), 1_000_000, original.senderKey(), original.signature()));
        
        // Hacker tries to be sneaky and re-mines the block so the hashes look correct!
        block.mine(2);
        
        // The chain STILL rejects it because the digital signature is broken
        assertFalse(chain.isValid());
    }

    @Test
    void forgedSenderInvalidatesChain() {
        Blockchain chain = new Blockchain(2);
        chain.minePending("asha");
        KeyPair ashaWallet = getWallet();
        chain.addTransaction(Transaction.create("asha", "ravi", 20, ashaWallet.getPrivate(), ashaWallet.getPublic()));
        chain.minePending("miner");
        
        Block block = chain.getChain().get(2);
        Transaction original = block.getTransactions().get(0);
        
        // Hacker tries to forge the sender to be "hacker" instead of "asha"
        block.getTransactions().set(0, new Transaction(original.id(), "hacker", original.to(), original.amount(), original.senderKey(), original.signature()));
        
        // Hacker re-mines the block to fix the hashes
        block.mine(2);
        
        // The chain STILL rejects it because the signature doesn't match the new sender name
        assertFalse(chain.isValid());
    }

    @Test
    void tamperingBlockOneIsDetected() {
        Blockchain chain = new Blockchain(2);
        chain.minePending("asha");
        chain.getChain().get(1).tamperPreviousHash("f".repeat(64));
        assertFalse(chain.isValid());
    }

    @Test
    void brokenLinkIsDetected() {
        Blockchain chain = new Blockchain(2);
        chain.minePending("asha");
        chain.minePending("ravi");
        chain.getChain().get(2).tamperPreviousHash("f".repeat(64));
        assertFalse(chain.isValid());
    }

    @Test
    void savedChainReloads(@TempDir Path dir) {
        Path file = dir.resolve("chain.dat");
        Blockchain chain = new Blockchain(2);
        chain.minePending("asha");
        Storage.save(chain, file);

        Blockchain loaded = Storage.load(file, 2);
        assertTrue(loaded.isValid());
        assertEquals(2, loaded.getDifficulty());
        assertEquals(chain.getChain().size(), loaded.getChain().size());
        assertEquals(chain.latestBlock().getHash(), loaded.latestBlock().getHash());
        assertEquals(50, loaded.balanceOf("asha"));
    }

    @Test
    void queuedPaymentsStayQueuedAfterReload(@TempDir Path dir) {
        Path file = dir.resolve("chain.dat");
        Blockchain chain = new Blockchain(2);
        chain.minePending("asha");
        KeyPair ashaWallet = getWallet();
        chain.addTransaction(Transaction.create("asha", "ravi", 20, ashaWallet.getPrivate(), ashaWallet.getPublic()));
        Storage.save(chain, file);

        Blockchain loaded = Storage.load(file, 2);
        assertEquals(1, loaded.getPending().size());
        assertEquals(2, loaded.getChain().size());
        assertEquals(30, loaded.balanceOf("asha"));
    }

    @Test
    void tamperedChainIsNotSaved(@TempDir Path dir) {
        Path file = dir.resolve("chain.dat");
        Blockchain good = new Blockchain(2);
        good.minePending("asha");
        Storage.save(good, file);

        Blockchain tampered = new Blockchain(2);
        tampered.minePending("ravi");
        tampered.getChain().get(1).tamperPreviousHash("f".repeat(64));
        Storage.save(tampered, file);

        Blockchain loaded = Storage.load(file, 2);
        assertEquals(2, loaded.getChain().size());
        assertEquals(50, loaded.balanceOf("asha"));
    }

    @Test
    void missingOrUnreadableFileStartsANewChain(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("chain.dat");
        Blockchain fresh = Storage.load(file, 2);
        assertEquals(2, fresh.getDifficulty());
        assertEquals(1, fresh.getChain().size());

        Files.writeString(file, "not a chain");
        Blockchain afterGarbage = Storage.load(file, 2);
        assertEquals(2, afterGarbage.getDifficulty());
        assertEquals(1, afterGarbage.getChain().size());
    }

    @Test
    void signingWithDifferentKeyIsRejected() {
        Blockchain chain = new Blockchain(2);
        chain.minePending("asha");
        
        KeyPair ashaWallet = getWallet();
        chain.addTransaction(Transaction.create("asha", "ravi", 10, ashaWallet.getPrivate(), ashaWallet.getPublic()));
        
        KeyPair hackerWallet = getWallet();
        assertThrows(IllegalArgumentException.class, () -> 
            chain.addTransaction(Transaction.create("asha", "ravi", 10, hackerWallet.getPrivate(), hackerWallet.getPublic()))
        );
    }

    @Test
    void signatureCannotBeReusedWithShiftedFields() {
        Blockchain chain = new Blockchain(2);
        chain.minePending("asha");

        KeyPair ashaWallet = getWallet();
        Transaction signed = Transaction.create("asha", "ravi", 120, ashaWallet.getPrivate(), ashaWallet.getPublic());

        // Same id, key and signature, but a digit moved from the amount into the payee name
        Transaction payeeShifted = new Transaction(signed.id(), "asha", "ravi1", 20, signed.senderKey(), signed.signature());
        assertFalse(payeeShifted.hasValidSignature());
        assertThrows(IllegalArgumentException.class, () -> chain.addTransaction(payeeShifted));

        // Same signature, but a letter moved from the payee name into the sender name
        Transaction senderShifted = new Transaction(signed.id(), "ashar", "avi", 120, signed.senderKey(), signed.signature());
        assertFalse(senderShifted.hasValidSignature());

        assertTrue(signed.hasValidSignature());
    }
}
