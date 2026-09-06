package com.splunk.demo.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransactionRecord {
    @Id
    private String transactionId;
    
    @Version
    private Long version;
    
    private String status;
    private Long createdAt;
    
    public static TransactionRecord create(String transactionId) {
        TransactionRecord record = new TransactionRecord();
        record.transactionId = transactionId;
        record.status = "PENDING";
        record.createdAt = System.currentTimeMillis();
        return record;
    }
    
    public void markComplete() {
        this.status = "COMPLETE";
    }
    
    public void markFailed() {
        this.status = "FAILED";
    }
    
    // For test mocking compatibility if needed
    public void setStatus(String status) {
        this.status = status;
    }
}
