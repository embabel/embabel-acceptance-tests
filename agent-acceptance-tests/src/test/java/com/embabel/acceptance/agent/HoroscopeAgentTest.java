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

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance tests for Horoscope Agent interactions via A2A protocol.
 * Verifies both the A2A response and the distributed traces exported to Zipkin.
 */
@ExtendWith(EmbabelA2AServerExtension.class)
@DisplayName("Horoscope Agent Tests")
class HoroscopeAgentTest extends AbstractA2ATest {

    @Override
    protected String getPayloadPath() {
        return "payloads/horoscope-agent-request.json";
    }

    @Override
    protected String getRequestId() {
        return "req-001";
    }

    @Test
    @DisplayName("Initiates StarFinder Agentic flow and collects Zipkin traces with expected span structure")
    void shouldSendHoroscopeMessageAndReceiveStory(ServerInfo server) throws IOException {
        log("Sending horoscope message and verifying Zipkin traces");

        long testStartMillis = System.currentTimeMillis();
        Response response = sendA2ARequest(server, payload);

        assertSuccessfulA2AResponse(response);

        if (response.getStatusCode() == 200) {
            assertJsonRpcCompliance(response);
            assertContentPresent(response);
            log("✓ Horoscope message sent and response received");
        } else {
            log("✓ Message accepted for async processing");
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

        // Dump all trace details to stdout for visibility
        logAllTraces(traces);

        // Log summary first so it is always visible, even if assertions fail
        TraceSummary summary = createAndLogTraceSummary(traces);

        // Verify span structure
        assertSpanNamesPresent(spans);
        assertSpanDurationsReasonable(spans);
        assertTraceContiguity(spans);

        log("✓ Zipkin trace assertions passed");

        log("Zipkin Url: " + server.getZipkinBaseUrl());

    }

}
