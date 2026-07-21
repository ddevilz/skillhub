package com.skillswap.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

@Entity
@Table(name = "credit_transaction")
public class CreditTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType transactionType;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP") // mirrors V5__credit_transaction.sql DEFAULT CURRENT_TIMESTAMP; only affects
    // Hibernate's ddl-auto=create-drop test schema (Flyway owns the real schema, ddl-auto=none there).
    // Needed so raw-JDBC test inserts (bypassing @PrePersist) satisfy the NOT NULL column.
    private LocalDateTime transactionDate;

    @PrePersist
    void onCreate() { if (transactionDate == null) transactionDate = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long v) { this.sessionId = v; }
    public TransactionType getTransactionType() { return transactionType; }
    public void setTransactionType(TransactionType v) { this.transactionType = v; }
    public int getAmount() { return amount; }
    public void setAmount(int v) { this.amount = v; }
    public LocalDateTime getTransactionDate() { return transactionDate; }
}
