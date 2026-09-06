# Splunk HTTP Event Collector (HEC) Logging Flow

This document provides an in-depth, code-level explanation of how logging works in this Spring Boot application, from the moment an HTTP request is received to how the final JSON log is structured.

## Sample Logs

When the application starts, it generates logs in JSON format. Even if the Splunk server is down, these logs are printed to the console. Here is the full lifecycle of a single request passing through the updated service:

```json
{"@timestamp":"2026-09-05T18:00:53.2415667Z","@version":"1","message":"Received request for /demo endpoint","logger_name":"com.splunk.demo.api.DemoRestApi","thread_name":"http-nio-8080-exec-1","level":"INFO","level_value":20000,"splunkUrl":"http://localhost:8088","splunkToken":"6c2439e8-874f-474c-8397-f924548296b2","correlation_id":"1bfd0a5d-3453-40c4-949d-0a957d15da18"}
{"@timestamp":"2026-09-05T18:00:53.4171901Z","@version":"1","message":"Successfully saved transaction 24ec5caf... to database","logger_name":"com.splunk.demo.service.TransactionReportService","thread_name":"http-nio-8080-exec-1","level":"INFO","level_value":20000,"splunkUrl":"http://localhost:8088","splunkToken":"6c2439e8-874f-474c-8397-f924548296b2","correlation_id":"1bfd0a5d-3453-40c4-949d-0a957d15da18","event_type":"database_call","target_service":"PostgreSQL","downstream_latency_ms":171,"query_type":"INSERT"}
{"@timestamp":"2026-09-05T18:00:54.7049105Z","@version":"1","message":"Successfully called Ledger service for transaction 24ec5caf...","logger_name":"com.splunk.demo.service.TransactionReportService","thread_name":"http-nio-8080-exec-1","level":"INFO","level_value":20000,"splunkUrl":"http://localhost:8088","splunkToken":"6c2439e8-874f-474c-8397-f924548296b2","correlation_id":"1bfd0a5d-3453-40c4-949d-0a957d15da18","event_type":"downstream_call","target_service":"Ledger","downstream_latency_ms":1286,"currency":"Afghani","exchange_rate":"78400.0"}
{"@timestamp":"2026-09-05T18:00:55.0220867Z","@version":"1","message":"Successfully called Risk service for transaction 24ec5caf...","logger_name":"com.splunk.demo.service.TransactionReportService","thread_name":"http-nio-8080-exec-1","level":"INFO","level_value":20000,"splunkUrl":"http://localhost:8088","splunkToken":"6c2439e8-874f-474c-8397-f924548296b2","correlation_id":"1bfd0a5d-3453-40c4-949d-0a957d15da18","event_type":"downstream_call","target_service":"Risk","downstream_latency_ms":318,"security_type":"Treasury Notes","interest_rate":"6.096"}
{"@timestamp":"2026-09-05T18:00:55.0953664Z","@version":"1","message":"Successfully updated transaction 24ec5caf... status to COMPLETE","logger_name":"com.splunk.demo.service.TransactionReportService","thread_name":"http-nio-8080-exec-1","level":"INFO","level_value":20000,"splunkUrl":"http://localhost:8088","splunkToken":"6c2439e8-874f-474c-8397-f924548296b2","correlation_id":"1bfd0a5d-3453-40c4-949d-0a957d15da18","event_type":"database_call","target_service":"PostgreSQL","query_type":"UPDATE"}
```

## Step-by-Step Flow and Code References

### 1. Spring Context and Logback Initialization
When the application starts, Logback reads the `src/main/resources/logback-spring.xml` configuration file. 

```xml
<!-- logback-spring.xml -->
<springProperty scope="context" name="splunkUrl" source="SPLUNK_HEC_URL" defaultValue="http://localhost:8088"/>
<springProperty scope="context" name="splunkToken" source="SPLUNK_HEC_TOKEN" defaultValue=""/>
```
**What happens here:** 
Spring Boot injects properties from `application.properties` (like `SPLUNK_HEC_URL`) into Logback's global context under the names `splunkUrl` and `splunkToken`.

```xml
<!-- logback-spring.xml -->
<appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder" />
</appender>
```
**What happens here:** 
Instead of a standard pattern encoder (which outputs text like `[INFO] 2026-09-05 - Message`), we use `LogstashEncoder`. By default, this encoder automatically converts every log event into a flat JSON object and automatically includes all variables from the Logback global context (which is why `splunkUrl` and `splunkToken` appear in every single log).

### 2. Request Interception (The Filter)
When a request arrives at the server (e.g., to the `/demo` endpoint), it is handled by a Tomcat worker thread (e.g., `http-nio-8080-exec-1`). Before it reaches the controller, it passes through `TransactionCorrelationFilter.java`.

```java
// TransactionCorrelationFilter.java
String correlationId = request.getHeader("X-Correlation-ID");
if (correlationId == null || correlationId.isEmpty()) {
    correlationId = UUID.randomUUID().toString(); // e.g., 0250622c-b44b-4364-b338-3a413708dbd6
}
```
**What happens here:** 
The filter looks for a correlation ID in the HTTP headers. If none exists (like in a direct browser request), it generates a random UUID.

```java
// TransactionCorrelationFilter.java
try {
    MDC.put("correlation_id", correlationId);
    filterChain.doFilter(request, response);
} finally {
    MDC.remove("correlation_id");
}
```
**What happens here:** 
The filter places the generated ID into the **MDC** (Mapped Diagnostic Context). The MDC is a thread-local map provided by SLF4J. This means that *only* the current thread (`http-nio-8080-exec-1`) has access to this specific `correlation_id` in its map.

### 3. Emitting the Log Event
The request proceeds down the filter chain to your API controller (`DemoRestApi.java`).

```java
// DemoRestApi.java (or similar component)
log.info("Received request for /demo endpoint");
```
**What happens here:** 
The controller executes a standard SLF4J log statement. It does not know anything about JSON, Splunk, or the Correlation ID. It just passes the message string and the severity level (`INFO`) to SLF4J.

### 4. JSON Serialization
SLF4J routes the event to Logback, which sends it to the appenders (`CONSOLE` and `SPLUNK`). The `LogstashEncoder` intercepts the event to format it.

**The Encoder's internal flow:**
1. It creates a JSON object.
2. It populates standard fields: `@timestamp` (current time), `message` (the string from the controller), `logger_name` (the class name), `thread_name` (`http-nio-8080-exec-1`), and `level`.
3. It merges in context properties: `splunkUrl` and `splunkToken`.
4. **Crucially:** It checks the thread-local MDC. Because our filter populated the MDC for this specific thread, the encoder finds `"correlation_id": "0250622c..."` and adds it as a top-level JSON key.

### 4. Custom Event Data (Markers in TransactionReportService)
While the MDC is great for data that should appear in *every* log (like the correlation ID), sometimes you want to add specific JSON fields to a *single* log event. This is exactly how we instrumented the API and Database calls in `TransactionReportService.java`.

```java
// TransactionReportService.java (Ledger API Call)
log.info(Markers.append("event_type", "downstream_call")
        .and(Markers.append("target_service", "Ledger"))
        .and(Markers.append("downstream_latency_ms", ledgerLatency))
        .and(Markers.append("currency", currency))
        .and(Markers.append("exchange_rate", exchangeRate)), 
        "Successfully called Ledger service for transaction {}", transactionId);
```
**What happens here:**
1. This service uses Logstash `Markers` to attach custom key-value pairs directly to a specific `log.info()` call. 
2. Because we are making real HTTP requests and Database calls, we calculate the execution latency manually (`System.currentTimeMillis() - start`) and append it as `downstream_latency_ms`.
3. We also parse the raw nested JSON from the Treasury APIs, extract the specific business metrics (like `currency`, `exchange_rate`, `security_type`), and append them to the log.
4. When the `LogstashEncoder` processes this specific log event, it sees the markers and injects those fields as top-level JSON keys. 
5. This is incredibly powerful for Splunk. It allows you to create dashboards for API latency (`avg(downstream_latency_ms)`), alert on slow Postgres `INSERT` vs `UPDATE` queries (`query_type`), and aggregate business metrics without having to parse complex text messages or raw JSON blobs.

### 5. Final Appender Output
Once the JSON string is built, it is handed to the Appenders.
- **Console Appender:** Prints the JSON string directly to standard out (which is what you see in the console logs).
- **Splunk Async Appender:** Puts the JSON string into an in-memory queue. In the background, the `HttpEventCollectorLogbackAppender` takes batches of these JSON strings, opens an HTTP connection to the `SPLUNK_HEC_URL`, and POSTs them. 

*(If Splunk is down, the POST request will fail or timeout, which is why it might slow down application shutdown or cause Logback internal errors, but the Console Appender will still successfully print the JSON.)*
