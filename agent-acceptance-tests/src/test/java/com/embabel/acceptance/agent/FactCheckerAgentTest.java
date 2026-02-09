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

import com.embabel.acceptance.jupiter.EmbabelA2AServerExtension;
import com.embabel.acceptance.jupiter.EmbabelA2AServerExtension.ServerInfo;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance tests for Fact Checker Agent.
 */
@ExtendWith(EmbabelA2AServerExtension.class)
@DisplayName("Fact Checker Agent Tests")
class FactCheckerAgentTest extends AbstractA2ATest {

    @Override
    protected String getPayloadPath() {
        return "payloads/fact-checker-request.json";
    }
    
    @Override
    protected String getRequestId() {
        return "req-003";
    }

    @Test
    @DisplayName("Should fact-check content about Kotlin and Spring")
    void shouldFactCheckKotlinContent(ServerInfo server) {
        log("Requesting fact check at: " + server.getBaseUrl());

        long testStartMillis = System.currentTimeMillis();
        Response response = sendA2ARequest(server, payload);

        assertSuccessfulA2AResponse(response);

        if (response.getStatusCode() == 200) {
            assertJsonRpcCompliance(response);
            assertContentPresent(response);
            log("✓ Fact-check completed successfully");
        } else {
            log("✓ Fact-check request accepted for async processing");
        }

        // Traces are exported asynchronously — poll Zipkin until they arrive
        List<List<Map<String, Object>>> traces = awaitTraces(server, testStartMillis);

        assertThat(traces)
                .as("At least one trace should be recorded in Zipkin")
                .isNotEmpty();

        List<Map<String, Object>> spans = traces.get(0);

        assertThat(spans)
                .as("Trace should contain at least one span")
                .isNotEmpty();

        log("Found " + traces.size() + " trace(s), first trace has " + spans.size() + " span(s)");

        logAllTraces(traces);

        assertSpanNamesPresent(spans);
        assertSpanDurationsReasonable(spans);
        assertTraceContiguity(spans);

        TraceSummary summary = createAndLogTraceSummary(traces);

        log("✓ Zipkin trace assertions passed");
    }
}
