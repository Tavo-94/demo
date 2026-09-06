package com.splunk.demo.service;

import com.splunk.demo.api.dto.TransactionReportResponse;
import lombok.RequiredArgsConstructor;
import net.logstash.logback.marker.Markers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TransactionReportService {

    private static final Logger log = LoggerFactory.getLogger(TransactionReportService.class);
    private final RestClient.Builder restClientBuilder;
    private final TransactionDatabaseService transactionDatabaseService;

    public TransactionReportResponse processTransaction(String transactionId) {
        String correlationId = MDC.get("correlation_id");
        if (correlationId == null) {
            correlationId = "unknown";
        }
        
        String databaseError = null;
        try {
            transactionDatabaseService.initiateTransaction(transactionId);
        } catch (Exception e) {
            databaseError = e.getMessage();
        }

        RestClient restClient = restClientBuilder.build();
        boolean hasError = false;
        String ledgerError = null;
        String riskError = null;
        
        TransactionReportResponse.ForexExposure forex = null;
        TransactionReportResponse.InterestRisk interest = null;

        // Real call to Ledger mock
        long ledgerStart = System.currentTimeMillis();
        try {
            Map<String, Object> ledgerResponse = restClient.get()
                    .uri("https://api.fiscaldata.treasury.gov/services/api/fiscal_service/v1/accounting/od/rates_of_exchange?page[size]=1")
                    .header("X-Correlation-ID", correlationId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            
            long ledgerLatency = System.currentTimeMillis() - ledgerStart;
            
            // Extract Metrics
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> dataList = (List<Map<String, Object>>) ledgerResponse.get("data");
            String currency = "Unknown";
            String exchangeRate = "Unknown";
            
            if (dataList != null && !dataList.isEmpty()) {
                Map<String, Object> firstItem = dataList.get(0);
                currency = (String) firstItem.get("currency");
                exchangeRate = (String) firstItem.get("exchange_rate");
                forex = new TransactionReportResponse.ForexExposure(currency, exchangeRate);
            }

            log.info(Markers.append("event_type", "downstream_call")
                    .and(Markers.append("target_service", "Ledger"))
                    .and(Markers.append("downstream_latency_ms", ledgerLatency))
                    .and(Markers.append("currency", currency))
                    .and(Markers.append("exchange_rate", exchangeRate)), 
                    "Successfully called Ledger service for transaction {}", transactionId);
                    
        } catch (Exception e) {
            hasError = true;
            log.error("Failed to call Ledger service for transaction {}", transactionId, e);
            ledgerError = e.getMessage();
        }

        // Real call to Risk mock
        long riskStart = System.currentTimeMillis();
        try {
            Map<String, Object> riskResponse = restClient.get()
                    .uri("https://api.fiscaldata.treasury.gov/services/api/fiscal_service/v2/accounting/od/avg_interest_rates?page[size]=1")
                    .header("X-Correlation-ID", correlationId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            
            long riskLatency = System.currentTimeMillis() - riskStart;
            
            // Extract Metrics
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> dataList = (List<Map<String, Object>>) riskResponse.get("data");
            String securityDesc = "Unknown";
            String interestRate = "Unknown";
            
            if (dataList != null && !dataList.isEmpty()) {
                Map<String, Object> firstItem = dataList.get(0);
                securityDesc = (String) firstItem.get("security_desc");
                interestRate = (String) firstItem.get("avg_interest_rate_amt");
                interest = new TransactionReportResponse.InterestRisk(securityDesc, interestRate);
            }

            log.info(Markers.append("event_type", "downstream_call")
                    .and(Markers.append("target_service", "Risk"))
                    .and(Markers.append("downstream_latency_ms", riskLatency))
                    .and(Markers.append("security_type", securityDesc))
                    .and(Markers.append("interest_rate", interestRate)), 
                    "Successfully called Risk service for transaction {}", transactionId);
                    
        } catch (Exception e) {
            hasError = true;
            log.error("Failed to call Risk service for transaction {}", transactionId, e);
            riskError = e.getMessage();
        }
        
        String finalStatus = hasError ? "FAILED" : "COMPLETE";
        
        transactionDatabaseService.completeTransaction(transactionId, finalStatus);
        
        return new TransactionReportResponse(
            transactionId, 
            finalStatus, 
            forex, 
            interest, 
            ledgerError, 
            riskError, 
            databaseError
        );
    }
}
