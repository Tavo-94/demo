package com.splunk.demo.service;

import com.splunk.demo.api.dto.TransactionReportResponse;
import com.splunk.demo.model.TransactionRecord;
import com.splunk.demo.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SpringBootTest
class TransactionReportServiceTest {

    static {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
    }

    @Autowired
    private TransactionReportService transactionReportService;

    private MockRestServiceServer server;

    @MockitoBean
    private TransactionRepository transactionRepository;

    @Autowired
    private RestClient.Builder restClientBuilder;

    @BeforeEach
    void setUp() {
        this.server = MockRestServiceServer.bindTo(restClientBuilder).build();
    }

    @Test
    void testProcessTransaction_Success() {
        String transactionId = "test-txn-123";
        TransactionRecord mockRecord = TransactionRecord.create(transactionId);
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(mockRecord));

        String ledgerUrl = "https://api.fiscaldata.treasury.gov/services/api/fiscal_service/v1/accounting/od/rates_of_exchange?page%5Bsize%5D=1";
        String ledgerJson = "{\"data\":[{\"currency\":\"Euro\",\"exchange_rate\":\"0.85\"}]}";
        server.expect(requestTo(ledgerUrl))
                .andRespond(withSuccess(ledgerJson, MediaType.APPLICATION_JSON));

        String riskUrl = "https://api.fiscaldata.treasury.gov/services/api/fiscal_service/v2/accounting/od/avg_interest_rates?page%5Bsize%5D=1";
        String riskJson = "{\"data\":[{\"security_desc\":\"Bonds\",\"avg_interest_rate_amt\":\"2.5\"}]}";
        server.expect(requestTo(riskUrl))
                .andRespond(withSuccess(riskJson, MediaType.APPLICATION_JSON));

        TransactionReportResponse result = transactionReportService.processTransaction(transactionId);

        assertEquals("COMPLETE", result.reportStatus());
        assertEquals("COMPLETE", mockRecord.getStatus());
        verify(transactionRepository, times(2)).save(any(TransactionRecord.class));
    }

    @Test
    void testProcessTransaction_FailureUpdatesToFailed() {
        String transactionId = "test-txn-456";
        TransactionRecord mockRecord = TransactionRecord.create(transactionId);
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(mockRecord));

        String ledgerUrl = "https://api.fiscaldata.treasury.gov/services/api/fiscal_service/v1/accounting/od/rates_of_exchange?page%5Bsize%5D=1";
        server.expect(requestTo(ledgerUrl))
                .andRespond(withServerError());

        String riskUrl = "https://api.fiscaldata.treasury.gov/services/api/fiscal_service/v2/accounting/od/avg_interest_rates?page%5Bsize%5D=1";
        String riskJson = "{\"data\":[{\"security_desc\":\"Bonds\",\"avg_interest_rate_amt\":\"2.5\"}]}";
        server.expect(requestTo(riskUrl))
                .andRespond(withSuccess(riskJson, MediaType.APPLICATION_JSON));

        TransactionReportResponse result = transactionReportService.processTransaction(transactionId);

        assertEquals("FAILED", result.reportStatus());
        assertEquals("FAILED", mockRecord.getStatus());
        verify(transactionRepository, times(2)).save(any(TransactionRecord.class));
    }
}
