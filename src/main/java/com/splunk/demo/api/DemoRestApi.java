package com.splunk.demo.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.splunk.demo.api.dto.ApiResponse;
import com.splunk.demo.api.dto.TransactionReportResponse;
import com.splunk.demo.service.TransactionReportService;
import java.util.UUID;

@Slf4j 
@RestController
@RequiredArgsConstructor
public class DemoRestApi {

    private final TransactionReportService transactionReportService;

    @GetMapping("/demo")
    public ApiResponse<TransactionReportResponse> getDemo() {
        log.info("Received request for /demo endpoint");
        
        String transactionId = UUID.randomUUID().toString();
        TransactionReportResponse response = transactionReportService.processTransaction(transactionId);
        return ApiResponse.ok(response);
    }

}
