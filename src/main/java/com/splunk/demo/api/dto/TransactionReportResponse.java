package com.splunk.demo.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionReportResponse(
    String transactionId,
    String reportStatus,
    ForexExposure forexExposure,
    InterestRisk interestRisk,
    String ledgerError,
    String riskError,
    String databaseError
) {
    public record ForexExposure(String currency, String exchangeRate) {}
    public record InterestRisk(String securityDesc, String avgInterestRateAmt) {}
}
