/*
 * Copyright 2024-2026 Embabel Pty Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.embabel.acceptance.agent;

import com.embabel.acceptance.jupiter.EmbabelA2AServerExtension.ServerInfo;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;

/**
 * Abstract base class for A2A agent acceptance tests.
 * Provides common functionality for payload handling and domain-driven assertions.
 */
public abstract class AbstractA2ATest {

    protected static final String A2A_ENDPOINT = "/a2a";
    protected static final String JSONRPC_VERSION = "2.0";
    protected static final Duration ZIPKIN_POLL_TIMEOUT = Duration.ofSeconds(30);
    protected static final Duration ZIPKIN_POLL_INTERVAL = Duration.ofSeconds(2);

    protected String payload;

    @BeforeEach
    void setUp() throws IOException {
        payload = loadJsonPayload(getPayloadPath());
    }

    protected abstract String getPayloadPath();

    protected abstract String getRequestId();

    protected void assertSuccessfulA2AResponse(Response response) {
        int statusCode = response.getStatusCode();
        log("Response status: " + statusCode);

        assertThat(statusCode)
                .as("Server should accept the message with 200 (OK) or 202 (Accepted)")
                .isIn(200, 202);
    }

    protected void assertJsonRpcCompliance(Response response) {
        response.then()
                .body("jsonrpc", equalTo(JSONRPC_VERSION))
                .body("id", equalTo(getRequestId()))
                .body("$", anyOf(hasKey("result"), hasKey("error")));
    }

    protected void assertContentPresent(Response response) {
        Object result = response.path("result");
        assertThat(result)
                .as("Response should contain result object")
                .isNotNull();
    }

    protected Response sendA2ARequest(ServerInfo server, String requestPayload) {
        return given()
                .log().all()
                .baseUri(server.getBaseUrl())
                .contentType(ContentType.JSON)
                .body(requestPayload)
                .when()
                .post(A2A_ENDPOINT)
                .then()
                .log().all()
                .extract()
                .response();
    }

    protected String loadJsonPayload(String resourcePath) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);

        if (!resource.exists()) {
            throw new IllegalArgumentException(
                    "Payload file not found: " + resourcePath +
                            ". Make sure the file exists in src/test/resources/" + resourcePath
            );
        }

        return resource.getContentAsString(StandardCharsets.UTF_8);
    }

    protected void log(String message) {
        System.out.println("[" + getClass().getSimpleName() + "] " + message);
    }

    // ------------------------------------------------------------------
    // Trace logging
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    protected void logAllTraces(List<List<Map<String, Object>>> traces) {
        log("═══════════════════════════════════════════════════════════");
        log("                   ZIPKIN TRACE DUMP");
        log("═══════════════════════════════════════════════════════════");

        for (int t = 0; t < traces.size(); t++) {
            List<Map<String, Object>> spans = traces.get(t);
            log("───────────────────────────────────────────────────────");
            log("Trace " + (t + 1) + "/" + traces.size()
                    + "  traceId=" + spans.get(0).get("traceId")
                    + "  spans=" + spans.size());
            log("───────────────────────────────────────────────────────");

            for (int s = 0; s < spans.size(); s++) {
                Map<String, Object> span = spans.get(s);
                log("  Span " + (s + 1) + "/" + spans.size());
                log("    name          : " + span.get("name"));
                log("    spanId        : " + span.get("id"));
                log("    parentId      : " + span.getOrDefault("parentId", "<root>"));
                log("    kind          : " + span.getOrDefault("kind", "<unset>"));
                log("    duration (µs) : " + span.getOrDefault("duration", "<unset>"));
                log("    timestamp     : " + span.getOrDefault("timestamp", "<unset>"));

                Map<String, Object> localEndpoint = (Map<String, Object>) span.get("localEndpoint");
                if (localEndpoint != null) {
                    log("    service       : " + localEndpoint.getOrDefault("serviceName", "<unknown>"));
                    log("    ip            : " + localEndpoint.getOrDefault("ipv4", localEndpoint.getOrDefault("ipv6", "<unset>")));
                }

                Map<String, Object> remoteEndpoint = (Map<String, Object>) span.get("remoteEndpoint");
                if (remoteEndpoint != null) {
                    log("    remote        : " + remoteEndpoint.getOrDefault("serviceName", "<unknown>")
                            + " @ " + remoteEndpoint.getOrDefault("ipv4", ""));
                }

                Map<String, String> tags = (Map<String, String>) span.get("tags");
                if (tags != null && !tags.isEmpty()) {
                    log("    tags:");
                    tags.entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .forEach(entry -> log("      " + entry.getKey() + " = " + entry.getValue()));
                }

                List<Map<String, Object>> annotations = (List<Map<String, Object>>) span.get("annotations");
                if (annotations != null && !annotations.isEmpty()) {
                    log("    annotations:");
                    annotations.forEach(ann ->
                            log("      [" + ann.get("timestamp") + "] " + ann.get("value")));
                }

                log("");
            }
        }
        log("═══════════════════════════════════════════════════════════");
    }

    // ------------------------------------------------------------------
    // Zipkin query helpers
    // ------------------------------------------------------------------

    /**
     * Poll Zipkin until at least one trace appears for the service.
     * Traces are exported asynchronously, so the first query right after
     * the A2A call may return nothing.
     */
    @SuppressWarnings("unchecked")
    protected List<List<Map<String, Object>>> awaitTraces(ServerInfo server) {
        return await()
                .atMost(ZIPKIN_POLL_TIMEOUT)
                .pollInterval(ZIPKIN_POLL_INTERVAL)
                .alias("Waiting for Zipkin traces")
                .until(() -> fetchTraces(server), traces -> !traces.isEmpty());
    }

    @SuppressWarnings("unchecked")
    protected List<List<Map<String, Object>>> fetchTraces(ServerInfo server) {
        return given()
                .baseUri(server.getZipkinBaseUrl())
                .queryParam("lookback", 60000)
                .when()
                .get("/api/v2/traces")
                .then()
                .extract()
                .as(List.class);
    }

    // ------------------------------------------------------------------
    // Trace assertions
    // ------------------------------------------------------------------

    /**
     * Verify that key span names are present, indicating Spring AI
     * actually performed LLM operations.
     */
    protected void assertSpanNamesPresent(List<Map<String, Object>> spans) {
        List<String> spanNames = spans.stream()
                .map(span -> (String) span.get("name"))
                .toList();

        log("Span names: " + spanNames);

        assertThat(spanNames)
                .as("Trace should contain recognizable operation spans")
                .isNotEmpty();
    }

    /**
     * Every span should have a non-negative duration.
     */
    protected void assertSpanDurationsReasonable(List<Map<String, Object>> spans) {
        for (Map<String, Object> span : spans) {
            Object durationObj = span.get("duration");
            if (durationObj instanceof Number duration) {
                assertThat(duration.longValue())
                        .as("Span '%s' should have a non-negative duration", span.get("name"))
                        .isGreaterThanOrEqualTo(0);
            }
        }
    }

    // ------------------------------------------------------------------
    // Trace summary
    // ------------------------------------------------------------------

    /**
     * Create a {@link TraceSummary} by aggregating across all traces, and log it.
     */
    protected TraceSummary createAndLogTraceSummary(List<List<Map<String, Object>>> traces) {
        TraceSummary summary = TraceSummary.fromTraces(traces);
        // Log each line individually so the test prefix is applied
        for (String line : summary.format().split("\n")) {
            log(line);
        }
        return summary;
    }

    /**
     * All spans within a single trace must share the same traceId.
     */
    protected void assertTraceContiguity(List<Map<String, Object>> spans) {
        if (spans.size() <= 1) return;

        String expectedTraceId = (String) spans.get(0).get("traceId");

        for (Map<String, Object> span : spans) {
            assertThat((String) span.get("traceId"))
                    .as("All spans should belong to the same trace")
                    .isEqualTo(expectedTraceId);
        }
    }
}
