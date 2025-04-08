package com.example.fraud.model;

public class Transaction {
    public String transactionId;
    public String userId;
    public double amount;
    public long timestamp;
    public String location;

    public Transaction() {}

    @Override
    public String toString() {
        return String.format("Transaction[id=%s, user=%s, amount=%.2f, time=%d, location=%s]",
                transactionId, userId, amount, timestamp, location);
    }
}
