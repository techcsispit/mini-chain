# mini-chain

A small blockchain in Java: blocks, SHA-256 hashes, proof-of-work mining, mining rewards and payments between people. It's for learning how a blockchain works. There's no networking and no real money.

## Running it

You need Java 17 or newer and Maven.

```
mvn compile exec:java    # interactive menu
mvn test
```

Try mining a block, tampering with it (option 7), then checking if the chain is valid (option 6).

Run it again and it picks up where you left off. The chain is saved to `chain.dat` and wallets to `wallets.dat` in the project folder; delete those files to start over.

## The idea

Each block stores some payments summarized via a binary Merkle tree root (`merkleRoot`), the hash of the previous block, and its own hash. The hash is computed from the block's header fields (`index|timestamp|previousHash|nonce|merkleRoot`). Mining means trying different nonces until the hash starts with enough zeros, which makes rewriting old blocks expensive.

## How it's supposed to work

Payments
- The amount has to be more than 0.
- You can't pay yourself.
- You can't spend more than you have, and that includes payments you've already made that aren't mined yet.
- Someone who has never received anything has a balance of 0.
- Note on Security: The system uses digital signatures to prevent forging transactions. However, for demo purposes, the interactive menu automatically fetches or generates the private key for any name you type as the sender. In a real application, users would hold their own private keys.

Mining
- A mined block holds all waiting payments plus a 50 coin reward for the miner.
- Every block's hash starts with as many zeros as the difficulty.

Validation
- `isValid()` returns false if anything in any mined block was changed, including the Merkle tree root, transactions, digital signatures, difficulty, or block links.

## Saving

The chain is kept in `chain.dat` and user wallets in `wallets.dat` in the project folder.

- The files are written every time you mine a block, and again when you quit (wallets are also saved when a new key is generated).
- Starting the program loads those files, including payments that are still waiting to be mined and registered user keys.
- A chain that fails the validity check is never written, so the file always holds the last chain that passed. Tamper with a block, quit, and the next run loads the untampered chain.
- The files are Java serialized objects, so they are binary and you can't read them in a text editor.
- It is tied to the `Block` and `Transaction` classes. Change either one and the file stops loading, and the program starts a new chain instead (e.g. `Block`'s `serialVersionUID` was bumped to `4L` for transaction hashing in `merkleRoot`, so chains saved with `3L` start fresh).
- Deleting `chain.dat` and `wallets.dat` starts you over.

## Code

All in `src/main/java/minichain/`:

- `Transaction.java`: a payment
- `Block.java`: hashing and mining
- `Blockchain.java`: payments, balances, validation
- `Storage.java`: saving and loading the chain and wallets
- `Main.java`: the menu

Tests are in `src/test/java/minichain/`.

## Contributing

Fork the repo, make your changes on a new branch, and open a pull request. Run `mvn test` first.

If you find a bug, open an issue with the steps to reproduce it, what you expected, and what happened instead.

Part of Source Start by CSI SPIT. MIT licensed.
