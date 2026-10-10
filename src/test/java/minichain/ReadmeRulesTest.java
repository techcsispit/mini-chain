package minichain;

import static org.junit.jupiter.api.Assertions.*;

import java.security.KeyPair;
import java.security.KeyPairGenerator;

import org.junit.jupiter.api.Test;

/** Edge cases from the README's "How it's supposed to work" section that the other tests don't reach. */
class ReadmeRulesTest {

    private static KeyPair getWallet() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(1024);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // "The amount has to be more than 0." means 0 itself is rejected, not just negative amounts.
    @Test
    void cannotMakeZeroPayment() {
        Blockchain chain = new Blockchain(2);
        chain.minePending("asha");
        KeyPair ashaWallet = getWallet();

        assertThrows(IllegalArgumentException.class, () ->
            chain.addTransaction(
                Transaction.create("asha", "ravi", 0,
                    ashaWallet.getPrivate(), ashaWallet.getPublic())
            )
        );
        assertTrue(chain.getPending().isEmpty());
        assertEquals(Blockchain.MINING_REWARD, chain.balanceOf("asha"));
        assertEquals(0, chain.balanceOf("ravi"));
    }

    // "Every block's hash starts with as many zeros as the difficulty." includes the genesis block.
    @Test
    void genesisBlockMeetsTheDifficulty() {
        Blockchain chain = new Blockchain(3);
        Block genesis = chain.getChain().get(0);

        assertTrue(genesis.getHash().startsWith("000"));
        assertEquals(genesis.computeHash(), genesis.getHash());
    }
}
