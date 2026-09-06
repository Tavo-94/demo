package com.splunk.demo.service;

import com.splunk.demo.model.TransactionRecord;
import com.splunk.demo.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.marker.Markers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionDatabaseService {

    private final TransactionRepository transactionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void initiateTransaction(String transactionId) {
        long dbStart = System.currentTimeMillis();
        try {
            TransactionRecord record = TransactionRecord.create(transactionId);
            transactionRepository.save(record);
            
            long dbLatency = System.currentTimeMillis() - dbStart;
            log.info(Markers.append("event_type", "database_call")
                    .and(Markers.append("target_service", "PostgreSQL"))
                    .and(Markers.append("downstream_latency_ms", dbLatency))
                    .and(Markers.append("query_type", "INSERT")), 
                    "Successfully saved transaction {} to database", transactionId);
        } catch (Exception e) {
            log.error(Markers.append("event_type", "database_error"), 
                    "Failed to save transaction {} to database", transactionId, e);
            throw new RuntimeException("Database error during initiate", e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeTransaction(String transactionId, String finalStatus) {
        try {
            transactionRepository.findById(transactionId).ifPresent(record -> {
                if ("COMPLETE".equals(finalStatus)) {
                    record.markComplete();
                } else {
                    record.markFailed();
                }
                transactionRepository.save(record);
            });
            log.info(Markers.append("event_type", "database_call")
                    .and(Markers.append("target_service", "PostgreSQL"))
                    .and(Markers.append("query_type", "UPDATE"))
                    .and(Markers.append("final_status", finalStatus)), 
                    "Successfully updated transaction {} status to {}", transactionId, finalStatus);
        } catch (Exception e) {
            log.error(Markers.append("event_type", "database_error"), 
                    "Failed to update transaction {} status", transactionId, e);
        }
    }
}
