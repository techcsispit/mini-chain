package minichain;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.UUID;
import java.util.Base64;

// bump version only for incompatible changes, adding a field is safe
public record Transaction(String id, String from, String to, int amount, PublicKey senderKey, byte[] signature) implements Serializable {
    private static final long serialVersionUID = 2L;

    // Sender used for mining rewards.
    public static final String NETWORK = "network";

    // For mining rewards
    public Transaction(String from, String to, int amount) {
        this(UUID.randomUUID().toString(), from, to, amount, null, null);
    }

    public static Transaction create(String from, String to, int amount, PrivateKey priv, PublicKey pub) {
        try {
            String id = UUID.randomUUID().toString();
            Signature rsa = Signature.getInstance("SHA256withRSA");
            rsa.initSign(priv);
            rsa.update(signedBytes(id, from, to, amount));
            byte[] sig = rsa.sign();
            return new Transaction(id, from, to, amount, pub, sig);
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign transaction", e);
        }
    }

    public boolean hasValidSignature() {
        if (NETWORK.equals(from)) return true;
        if (senderKey == null || signature == null) return false;
        try {
            Signature rsa = Signature.getInstance("SHA256withRSA");
            rsa.initVerify(senderKey);
            rsa.update(signedBytes(id, from, to, amount));
            return rsa.verify(signature);
        } catch (Exception e) {
            return false;
        }
    }

    // Each text field is written with its length in front, so the bytes can't be
    // re-split into a different sender, payee or amount.
    private static byte[] signedBytes(String id, String from, String to, int amount) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeUTF(id);
        out.writeUTF(from);
        out.writeUTF(to);
        out.writeInt(amount);
        return bytes.toByteArray();
    }

    @Override
    public String toString() {
        return from + " -> " + to + ": " + amount;
    }
}
