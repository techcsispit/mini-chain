package minichain;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class BlockchainTest {

    private static KeyPair getWallet() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
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
        assertEquals(Block.computeMerkleRoot(List.of()), chain.getChain().get(0).getMerkleRoot());
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
        KeyPair ashaWallet = getWallet();
        String asha = Blockchain.deriveAddress(ashaWallet.getPublic());
        chain.minePending(asha);
        chain.addTransaction(Transaction.create(asha, "ravi", 20, ashaWallet.getPrivate(), ashaWallet.getPublic()));
        chain.minePending("meera");
        assertEquals(30, chain.balanceOf(asha));
        assertEquals(20, chain.balanceOf("ravi"));
        assertEquals(50, chain.balanceOf("meera"));
    }

    @Test
    void cannotSpendMoreThanYouHave() {
        Blockchain chain = new Blockchain(2);
        KeyPair ashaWallet = getWallet();
        String asha = Blockchain.deriveAddress(ashaWallet.getPublic());
        chain.minePending(asha);
        assertThrows(IllegalArgumentException.class, () -> chain.addTransaction(Transaction.create(asha, "ravi", 60, ashaWallet.getPrivate(), ashaWallet.getPublic())));
    }

    @Test
    void cannotMakeNegativePayment() {
        Blockchain chain = new Blockchain(2);
        KeyPair ashaWallet = getWallet();
        String asha = Blockchain.deriveAddress(ashaWallet.getPublic());
        chain.minePending(asha);

        assertThrows(IllegalArgumentException.class, () ->
            chain.addTransaction(
                Transaction.create(asha, "ravi", -10,
                    ashaWallet.getPrivate(), ashaWallet.getPublic())
            )
        );
    }

    @Test
    void cannotPayYourself() {
        Blockchain chain = new Blockchain(2);
        KeyPair ashaWallet = getWallet();
        String asha = Blockchain.deriveAddress(ashaWallet.getPublic());
        chain.minePending(asha);
        assertThrows(IllegalArgumentException.class, () -> chain.addTransaction(Transaction.create(asha, asha, 5, ashaWallet.getPrivate(), ashaWallet.getPublic())));
    }

    @Test
    void cannotSpendQueuedCoins() {
        Blockchain chain = new Blockchain(2);
        KeyPair ashaWallet = getWallet();
        String asha = Blockchain.deriveAddress(ashaWallet.getPublic());
        chain.minePending(asha);
        chain.addTransaction(Transaction.create(asha, "ravi", 40, ashaWallet.getPrivate(), ashaWallet.getPublic()));
        assertThrows(IllegalArgumentException.class, () -> chain.addTransaction(Transaction.create(asha, "meera", 40, ashaWallet.getPrivate(), ashaWallet.getPublic())));
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
        KeyPair ashaWallet = getWallet();
        String asha = Blockchain.deriveAddress(ashaWallet.getPublic());
        chain.minePending(asha);
        chain.addTransaction(Transaction.create(asha, "ravi", 20, ashaWallet.getPrivate(), ashaWallet.getPublic()));
        chain.minePending("miner");
        Block block = chain.getChain().get(2);
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
        KeyPair ashaWallet = getWallet();
        String asha = Blockchain.deriveAddress(ashaWallet.getPublic());
        chain.minePending(asha);
        chain.addTransaction(Transaction.create(asha, "ravi", 20, ashaWallet.getPrivate(), ashaWallet.getPublic()));
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
        KeyPair ashaWallet = getWallet();
        String asha = Blockchain.deriveAddress(ashaWallet.getPublic());
        chain.minePending(asha);
        chain.addTransaction(Transaction.create(asha, "ravi", 20, ashaWallet.getPrivate(), ashaWallet.getPublic()));
        Storage.save(chain, file);

        Blockchain loaded = Storage.load(file, 2);
        assertEquals(1, loaded.getPending().size());
        assertEquals(2, loaded.getChain().size());
        assertEquals(30, loaded.balanceOf(asha));
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
        KeyPair ashaWallet = getWallet();
        String asha = Blockchain.deriveAddress(ashaWallet.getPublic());
        chain.minePending(asha);
        
        chain.addTransaction(Transaction.create(asha, "ravi", 10, ashaWallet.getPrivate(), ashaWallet.getPublic()));
        
        KeyPair hackerWallet = getWallet();
        assertThrows(IllegalArgumentException.class, () -> 
            chain.addTransaction(Transaction.create(asha, "ravi", 10, hackerWallet.getPrivate(), hackerWallet.getPublic()))
        );
    }

    @Test
    void savedWalletsReload(@TempDir Path dir) {
        Path file = dir.resolve("wallets.dat");
        Map<String, KeyPair> wallets = new HashMap<>();
        KeyPair ashaWallet = getWallet();
        wallets.put("asha", ashaWallet);
        Storage.saveWallets(wallets, file);

        Map<String, KeyPair> loaded = Storage.loadWallets(file);
        assertEquals(1, loaded.size());
        assertTrue(loaded.containsKey("asha"));
        assertEquals(ashaWallet.getPublic(), loaded.get("asha").getPublic());
        assertEquals(ashaWallet.getPrivate(), loaded.get("asha").getPrivate());
    }

    @Test
    void missingOrUnreadableWalletsFileStartsEmpty(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("wallets.dat");
        Map<String, KeyPair> fresh = Storage.loadWallets(file);
        assertTrue(fresh.isEmpty());

        Files.writeString(file, "corrupted data");
        Map<String, KeyPair> afterGarbage = Storage.loadWallets(file);
        assertTrue(afterGarbage.isEmpty());
    }

    @Test
    void oldRsaWalletsAreDiscardedCleanly(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("wallets.dat");
        KeyPairGenerator rsaGen = KeyPairGenerator.getInstance("RSA");
        rsaGen.initialize(1024);
        KeyPair rsaWallet = rsaGen.generateKeyPair();

        Map<String, KeyPair> oldWallets = new HashMap<>();
        oldWallets.put("asha", rsaWallet);
        Storage.saveWallets(oldWallets, file);

        // When loaded, old RSA wallets from #25 are discarded cleanly
        Map<String, KeyPair> loaded = Storage.loadWallets(file);
        assertFalse(loaded.containsKey("asha"));
        assertTrue(loaded.isEmpty());
    }

    @Test
    void mixedWalletsDiscardRsaKeysAndKeepEd25519Keys(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("wallets.dat");
        KeyPairGenerator rsaGen = KeyPairGenerator.getInstance("RSA");
        rsaGen.initialize(1024);
        KeyPair rsaWallet = rsaGen.generateKeyPair();

        KeyPair edWallet = getWallet();

        Map<String, KeyPair> mixed = new HashMap<>();
        mixed.put("asha", rsaWallet);
        mixed.put("ravi", edWallet);
        Storage.saveWallets(mixed, file);

        Map<String, KeyPair> loaded = Storage.loadWallets(file);
        assertEquals(1, loaded.size());
        assertFalse(loaded.containsKey("asha"));
        assertTrue(loaded.containsKey("ravi"));
        assertEquals(edWallet.getPublic(), loaded.get("ravi").getPublic());

        // Signing with the retained Ed25519 wallet works and does NOT throw InvalidKeyException
        String raviAddr = Blockchain.deriveAddress(loaded.get("ravi").getPublic());
        assertDoesNotThrow(() ->
            Transaction.create(raviAddr, "meera", 10, loaded.get("ravi").getPrivate(), loaded.get("ravi").getPublic())
        );
    }

    @Test
    void reloadedWalletCanSpendAcrossSimulatedRestart(@TempDir Path dir) {
        Path chainFile = dir.resolve("chain.dat");
        Path walletsFile = dir.resolve("wallets.dat");

        Blockchain chain = new Blockchain(2);
        KeyPair ashaWallet = getWallet();
        String asha = Blockchain.deriveAddress(ashaWallet.getPublic());
        chain.minePending(asha);

        Map<String, KeyPair> wallets = new HashMap<>();
        wallets.put("asha", ashaWallet);
        Storage.saveWallets(wallets, walletsFile);

        chain.addTransaction(Transaction.create(asha, "ravi", 20, ashaWallet.getPrivate(), ashaWallet.getPublic()));
        chain.minePending("miner");
        Storage.save(chain, chainFile);

        // --- Simulated App Restart ---
        Blockchain loadedChain = Storage.load(chainFile, 2);
        Map<String, KeyPair> loadedWallets = Storage.loadWallets(walletsFile);

        assertEquals(30, loadedChain.balanceOf(asha));
        KeyPair loadedAshaWallet = loadedWallets.get("asha");
        assertNotNull(loadedAshaWallet);

        assertDoesNotThrow(() -> {
            loadedChain.addTransaction(Transaction.create(asha, "ravi", 10,
                loadedAshaWallet.getPrivate(), loadedAshaWallet.getPublic()));
        });

        loadedChain.minePending("miner");
        assertEquals(20, loadedChain.balanceOf(asha));
        assertEquals(30, loadedChain.balanceOf("ravi"));
        assertTrue(loadedChain.isValid());
    }

    @Test
    void unpersistedWalletMismatchFailsAcrossRestart(@TempDir Path dir) {
        Path chainFile = dir.resolve("chain.dat");

        Blockchain chain = new Blockchain(2);
        KeyPair ashaWallet = getWallet();
        String asha = Blockchain.deriveAddress(ashaWallet.getPublic());
        chain.minePending(asha);

        chain.addTransaction(Transaction.create(asha, "ravi", 20, ashaWallet.getPrivate(), ashaWallet.getPublic()));
        chain.minePending("miner");
        Storage.save(chain, chainFile);

        // --- Simulated App Restart without persisting wallet ---
        Blockchain loadedChain = Storage.load(chainFile, 2);
        KeyPair freshKeyForAsha = getWallet();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
            loadedChain.addTransaction(Transaction.create(asha, "ravi", 10,
                freshKeyForAsha.getPrivate(), freshKeyForAsha.getPublic()))
        );
        assertTrue(thrown.getMessage().contains("does not match sender public key: " + asha));
    }

    // ==========================================
    // Tests for Merkle Root
    // ==========================================

    @Test
    void merkleTreeComputesCorrectlyForDifferentTransactionCounts() {
        // 0 transactions (empty)
        String root0 = Block.computeMerkleRoot(List.of());
        assertEquals(Block.sha256(""), root0);

        // 1 transaction: root is tx1.hash()
        KeyPair w1 = getWallet();
        String addr1 = Blockchain.deriveAddress(w1.getPublic());
        Transaction tx1 = Transaction.create(addr1, "ravi", 10, w1.getPrivate(), w1.getPublic());
        String root1 = Block.computeMerkleRoot(List.of(tx1));
        assertEquals(tx1.hash(), root1);

        // 2 transactions: sha256(h1 + h2)
        KeyPair w2 = getWallet();
        String addr2 = Blockchain.deriveAddress(w2.getPublic());
        Transaction tx2 = Transaction.create(addr2, "meera", 20, w2.getPrivate(), w2.getPublic());
        String root2 = Block.computeMerkleRoot(List.of(tx1, tx2));
        assertEquals(Block.sha256(tx1.hash() + tx2.hash()), root2);

        // 3 transactions: odd duplication for tx3 -> sha256(sha256(h1 + h2) + sha256(h3 + h3))
        KeyPair w3 = getWallet();
        String addr3 = Blockchain.deriveAddress(w3.getPublic());
        Transaction tx3 = Transaction.create(addr3, addr1, 30, w3.getPrivate(), w3.getPublic());
        String root3 = Block.computeMerkleRoot(List.of(tx1, tx2, tx3));
        String expected3 = Block.sha256(
            Block.sha256(tx1.hash() + tx2.hash()) +
            Block.sha256(tx3.hash() + tx3.hash())
        );
        assertEquals(expected3, root3);
    }

    @Test
    void tamperingTransactionInvalidatesMerkleRootAndChain() {
        Blockchain chain = new Blockchain(2);
        KeyPair ashaWallet = getWallet();
        String asha = Blockchain.deriveAddress(ashaWallet.getPublic());
        chain.minePending(asha);

        chain.addTransaction(Transaction.create(asha, "ravi", 20, ashaWallet.getPrivate(), ashaWallet.getPublic()));
        chain.minePending("miner");

        Block block = chain.getChain().get(2);
        // Tamper transaction without updating block Merkle root
        Transaction original = block.getTransactions().get(0);
        Transaction modified = new Transaction(original.id(), original.from(), original.to(), original.amount() + 1, original.senderKey(), original.signature());
        block.getTransactions().set(0, modified);

        // isValid detects that block's stored Merkle root no longer matches transactions
        assertFalse(chain.isValid());
    }

    @Test
    void tamperingMerkleRootDirectlyInvalidatesChain() {
        Blockchain chain = new Blockchain(2);
        chain.minePending("miner");

        Block block = chain.getChain().get(1);
        block.tamperMerkleRoot("0".repeat(64));

        assertFalse(chain.isValid());
    }

    // ==========================================
    // Tests for Transaction Hashing (Issue #29)
    // ==========================================

    @Test
    void transactionHashIsDeterministicAndHex64() {
        KeyPair wallet = getWallet();
        Transaction tx = Transaction.create("asha", "ravi", 25, wallet.getPrivate(), wallet.getPublic());

        String hash1 = tx.hash();
        String hash2 = tx.hash();

        assertNotNull(hash1);
        assertEquals(64, hash1.length());
        assertTrue(hash1.matches("^[0-9a-f]{64}$"), "Hash must be a 64-character lowercase hexadecimal string");
        assertEquals(hash1, hash2, "Repeated calls to hash() must be deterministic");
    }

    @Test
    void modifyingAnyTransactionFieldChangesHash() {
        KeyPair wallet = getWallet();
        KeyPair otherWallet = getWallet();
        Transaction base = Transaction.create("asha", "ravi", 25, wallet.getPrivate(), wallet.getPublic());
        String baseHash = base.hash();

        // Modifying ID
        Transaction diffId = new Transaction("custom-id-" + System.nanoTime(), base.from(), base.to(), base.amount(), base.senderKey(), base.signature());
        assertNotEquals(baseHash, diffId.hash(), "Altering ID must change hash");

        // Modifying from
        Transaction diffFrom = new Transaction(base.id(), "asha-modified", base.to(), base.amount(), base.senderKey(), base.signature());
        assertNotEquals(baseHash, diffFrom.hash(), "Altering from must change hash");

        // Modifying to
        Transaction diffTo = new Transaction(base.id(), base.from(), "ravi-modified", base.amount(), base.senderKey(), base.signature());
        assertNotEquals(baseHash, diffTo.hash(), "Altering to must change hash");

        // Modifying amount
        Transaction diffAmount = new Transaction(base.id(), base.from(), base.to(), base.amount() + 10, base.senderKey(), base.signature());
        assertNotEquals(baseHash, diffAmount.hash(), "Altering amount must change hash");

        // Modifying senderKey
        Transaction diffSenderKey = new Transaction(base.id(), base.from(), base.to(), base.amount(), otherWallet.getPublic(), base.signature());
        assertNotEquals(baseHash, diffSenderKey.hash(), "Altering senderKey must change hash");

        Transaction nullSenderKey = new Transaction(base.id(), base.from(), base.to(), base.amount(), null, base.signature());
        assertNotEquals(baseHash, nullSenderKey.hash(), "Null senderKey must produce different hash than present key");

        // Modifying signature
        byte[] alteredSig = base.signature().clone();
        alteredSig[0] ^= 0xFF;
        Transaction diffSig = new Transaction(base.id(), base.from(), base.to(), base.amount(), base.senderKey(), alteredSig);
        assertNotEquals(baseHash, diffSig.hash(), "Altering signature bytes must change hash");

        Transaction nullSig = new Transaction(base.id(), base.from(), base.to(), base.amount(), base.senderKey(), null);
        assertNotEquals(baseHash, nullSig.hash(), "Null signature must produce different hash than present signature");
    }

    @Test
    void unsignedRewardTransactionHashesDeterministically() {
        Transaction rewardTx = new Transaction(Transaction.NETWORK, "asha", 50);
        assertNull(rewardTx.senderKey());
        assertNull(rewardTx.signature());

        String hash1 = rewardTx.hash();
        String hash2 = rewardTx.hash();

        assertEquals(64, hash1.length());
        assertTrue(hash1.matches("^[0-9a-f]{64}$"));
        assertEquals(hash1, hash2);
    }

    @Test
    void modifyingTransactionSecurityFieldsInvalidatesMerkleRoot() {
        KeyPair wallet = getWallet();
        KeyPair otherWallet = getWallet();
        Transaction original = Transaction.create("asha", "ravi", 25, wallet.getPrivate(), wallet.getPublic());
        String originalRoot = Block.computeMerkleRoot(List.of(original));

        // Altering id changes Merkle root
        Transaction tamperedId = new Transaction("alt-id", original.from(), original.to(), original.amount(), original.senderKey(), original.signature());
        assertNotEquals(originalRoot, Block.computeMerkleRoot(List.of(tamperedId)));

        // Altering senderKey changes Merkle root
        Transaction tamperedKey = new Transaction(original.id(), original.from(), original.to(), original.amount(), otherWallet.getPublic(), original.signature());
        assertNotEquals(originalRoot, Block.computeMerkleRoot(List.of(tamperedKey)));

        // Altering signature changes Merkle root
        byte[] tamperedSig = original.signature().clone();
        tamperedSig[0] ^= 0xFF;
        Transaction tamperedSignature = new Transaction(original.id(), original.from(), original.to(), original.amount(), original.senderKey(), tamperedSig);
        assertNotEquals(originalRoot, Block.computeMerkleRoot(List.of(tamperedSignature)));
    }

    // =============================================================
    // Tests for Native Ed25519 & Address Derivation (Issue #31)
    // =============================================================

    @Test
    void validEd25519SigningAndVerification() {
        KeyPair wallet = getWallet();
        assertTrue("EdDSA".equalsIgnoreCase(wallet.getPublic().getAlgorithm()) || "Ed25519".equalsIgnoreCase(wallet.getPublic().getAlgorithm()));

        String address = Blockchain.deriveAddress(wallet.getPublic());
        assertNotNull(address);
        assertEquals(64, address.length());
        assertTrue(address.matches("^[0-9a-f]{64}$"));

        Transaction tx = Transaction.create(address, "ravi", 25, wallet.getPrivate(), wallet.getPublic());
        assertTrue(tx.hasValidSignature());
        assertEquals(64, tx.signature().length); // Ed25519 signatures are 64 bytes

        // Tampering with the signature invalidates it
        byte[] tamperedSig = tx.signature().clone();
        tamperedSig[0] ^= 0xFF;
        Transaction invalidTx = new Transaction(tx.id(), tx.from(), tx.to(), tx.amount(), tx.senderKey(), tamperedSig);
        assertFalse(invalidTx.hasValidSignature());
    }

    @Test
    void rejectionOfTransactionsWhereFromDoesNotMatchDerivedAddress() {
        Blockchain chain = new Blockchain(2);
        KeyPair wallet = getWallet();
        String trueAddress = Blockchain.deriveAddress(wallet.getPublic());
        chain.minePending(trueAddress);

        // Arbitrary username alias instead of derived address
        Transaction namedTx = Transaction.create("asha", "ravi", 10, wallet.getPrivate(), wallet.getPublic());
        IllegalArgumentException err1 = assertThrows(IllegalArgumentException.class, () -> chain.addTransaction(namedTx));
        assertTrue(err1.getMessage().contains("does not match sender public key"));

        // Another valid hex address belonging to someone else
        KeyPair otherWallet = getWallet();
        String otherAddress = Blockchain.deriveAddress(otherWallet.getPublic());
        Transaction stolenFromTx = Transaction.create(otherAddress, "ravi", 10, wallet.getPrivate(), wallet.getPublic());
        IllegalArgumentException err2 = assertThrows(IllegalArgumentException.class, () -> chain.addTransaction(stolenFromTx));
        assertTrue(err2.getMessage().contains("does not match sender public key"));
    }

    @Test
    void frontRunningProtectionAcrossAccounts() {
        Blockchain chain = new Blockchain(2);
        KeyPair aliceWallet = getWallet();
        String aliceAddress = Blockchain.deriveAddress(aliceWallet.getPublic());
        chain.minePending(aliceAddress);
        assertEquals(50, chain.balanceOf(aliceAddress));

        KeyPair attackerWallet = getWallet();
        String attackerAddress = Blockchain.deriveAddress(attackerWallet.getPublic());

        // Attacker attempts to spend Alice's coins by using aliceAddress as 'from' signed with attacker's key
        Transaction attackTx = Transaction.create(aliceAddress, attackerAddress, 25, attackerWallet.getPrivate(), attackerWallet.getPublic());
        IllegalArgumentException err = assertThrows(IllegalArgumentException.class, () -> chain.addTransaction(attackTx));
        assertTrue(err.getMessage().contains("does not match sender public key"));

        // Attacker cannot register aliceAddress in knownKeys using attacker's public key
        assertTrue(chain.getPending().isEmpty());
        assertEquals(50, chain.balanceOf(aliceAddress));

        // Alice successfully spends her own coins
        KeyPair bobWallet = getWallet();
        String bobAddress = Blockchain.deriveAddress(bobWallet.getPublic());
        Transaction legitTx = Transaction.create(aliceAddress, bobAddress, 25, aliceWallet.getPrivate(), aliceWallet.getPublic());
        assertDoesNotThrow(() -> chain.addTransaction(legitTx));

        chain.minePending("miner");
        assertEquals(25, chain.balanceOf(aliceAddress));
        assertEquals(25, chain.balanceOf(bobAddress));
        assertTrue(chain.isValid());
    }

    @Test
    void tamperedMiningRewardAmountFailsValidation() {
        Blockchain chain = new Blockchain(2);
        chain.minePending("miner");
        assertTrue(chain.isValid());

        Block block = chain.getChain().get(1);
        Transaction legitReward = block.getTransactions().get(0);

        // Replace 50 coin reward with 1,000,000 coins
        Transaction tamperedReward = new Transaction(legitReward.id(), Transaction.NETWORK, legitReward.to(), 1_000_000, null, null);
        block.getTransactions().set(0, tamperedReward);

        // Recompute Merkle root and re-mine block hash to satisfy proof-of-work
        block.tamperMerkleRoot(Block.computeMerkleRoot(block.getTransactions()));
        block.mine(chain.getDifficulty());

        // isValid must catch that the reward amount is not 50
        assertFalse(chain.isValid());
    }

    @Test
    void duplicateMiningRewardInSingleBlockFailsValidation() {
        Blockchain chain = new Blockchain(2);
        chain.minePending("miner");
        assertTrue(chain.isValid());

        Block block = chain.getChain().get(1);

        // Inject a second 50 coin mining reward in the same block
        block.getTransactions().add(new Transaction(Transaction.NETWORK, "miner", Blockchain.MINING_REWARD));

        // Recompute Merkle root and re-mine block hash
        block.tamperMerkleRoot(Block.computeMerkleRoot(block.getTransactions()));
        block.mine(chain.getDifficulty());

        // isValid must reject multiple network rewards in a single block
        assertFalse(chain.isValid());
    }

    @Test
    void transactionWithInsufficientBalanceInMinedBlockFailsValidation() {
        Blockchain chain = new Blockchain(2);
        KeyPair aliceWallet = getWallet();
        String aliceAddress = Blockchain.deriveAddress(aliceWallet.getPublic());
        KeyPair bobWallet = getWallet();
        String bobAddress = Blockchain.deriveAddress(bobWallet.getPublic());

        // Alice has 0 balance, but signs a transaction to transfer 50 coins to Bob
        Transaction overdraftTx = Transaction.create(aliceAddress, bobAddress, 50, aliceWallet.getPrivate(), aliceWallet.getPublic());

        // A malicious or external block includes this overdraft transaction directly
        List<Transaction> txs = new ArrayList<>();
        txs.add(overdraftTx);
        txs.add(new Transaction(Transaction.NETWORK, "miner", Blockchain.MINING_REWARD));

        Block block = new Block(chain.getChain().size(), System.currentTimeMillis(), txs, chain.latestBlock().getHash());
        block.mine(chain.getDifficulty());
        chain.getChain().add(block);

        // Transaction signature and proof-of-work are valid, but Alice had insufficient balance
        assertFalse(chain.isValid());
    }

    @Test
    void consecutiveOverdraftInSameBlockFailsValidation() {
        Blockchain chain = new Blockchain(2);
        KeyPair aliceWallet = getWallet();
        String aliceAddress = Blockchain.deriveAddress(aliceWallet.getPublic());
        KeyPair bobWallet = getWallet();
        String bobAddress = Blockchain.deriveAddress(bobWallet.getPublic());

        // Alice receives 50 coins via mining reward
        chain.minePending(aliceAddress);
        assertEquals(50, chain.balanceOf(aliceAddress));
        assertTrue(chain.isValid());

        // Alice creates two legitimate 30-coin transactions (total 60 coins > 50 balance)
        Transaction tx1 = Transaction.create(aliceAddress, bobAddress, 30, aliceWallet.getPrivate(), aliceWallet.getPublic());
        Transaction tx2 = Transaction.create(aliceAddress, bobAddress, 30, aliceWallet.getPrivate(), aliceWallet.getPublic());

        List<Transaction> txs = new ArrayList<>();
        txs.add(tx1);
        txs.add(tx2);
        txs.add(new Transaction(Transaction.NETWORK, "miner", Blockchain.MINING_REWARD));

        Block block = new Block(chain.getChain().size(), System.currentTimeMillis(), txs, chain.latestBlock().getHash());
        block.mine(chain.getDifficulty());
        chain.getChain().add(block);

        // isValid must reject the block because the second transaction overspends Alice's balance
        assertFalse(chain.isValid());
    }
}
