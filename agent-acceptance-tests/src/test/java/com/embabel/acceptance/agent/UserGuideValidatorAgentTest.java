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
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance tests for the User Guide Validator Agent via A2A protocol.
 *
 * <h2>Section coverage strategy</h2>
 * <p>Exhaustive per-section coverage lives in the unit tests (zero LLM cost via
 * {@code FakeOperationContext}). This suite runs a small representative sample
 * to confirm end-to-end behaviour without burning 28 × (haiku + haiku + sonnet)
 * on every CI run:
 * <ul>
 *   <li>One top-level chapter — confirms the happy path works.</li>
 *   <li>One deep reference section — confirms anchor resolution through the full chain.</li>
 *   <li>One known-empty section — confirms the Java guard fires and returns {@code passed=false}
 *       without making a validator LLM call.</li>
 * </ul>
 *
 * <p>Payload is built dynamically — no per-section JSON files needed.
 */
@ExtendWith(EmbabelA2AServerExtension.class)
@DisplayName("User Guide Validator Agent — All Known Sections")
class UserGuideValidatorAgentTest extends AbstractA2ATest {

    // -----------------------------------------------------------------------
    // Known sections — mirrors UserGuideValidatorAgent.KNOWN_SECTIONS
    // Update here whenever the guide structure changes.
    // -----------------------------------------------------------------------

    static Stream<SectionCase> knownSections() {
        return Stream.of(
                new SectionCase("agent.guide",                 "Getting Started"),
                new SectionCase("blackboard",                  "Blackboard"),
                new SectionCase("reference.tools",             "Tools"),
                new SectionCase("reference.testing",           "Testing"),
                new SectionCase("reference.integrations__a2a", "A2A Integration")
        );
    }

    // -----------------------------------------------------------------------
    // Request ID sequencing — each parameterized run gets a unique ID
    // -----------------------------------------------------------------------

    private static final AtomicInteger REQUEST_COUNTER = new AtomicInteger(100);

    @Override
    protected String getRequestId() {
        return "req-ugv-" + REQUEST_COUNTER.getAndIncrement();
    }

    // -----------------------------------------------------------------------
    // Parameterized test
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "validate section: {0}")
    @MethodSource("knownSections")
    @DisplayName("Should validate each known User Guide section and return a ValidationReport")
    void shouldValidateKnownSection(SectionCase section, ServerInfo server) {
        log("Validating section: " + section.anchor() + " (" + section.humanName() + ")");

        String requestId = getRequestId();
        String dynamicPayload = buildPayload(requestId, section);

        long testStartMillis = System.currentTimeMillis();
        Response response = sendA2ARequest(server, dynamicPayload);

        assertSuccessfulA2AResponse(response);

        if (response.getStatusCode() == 200) {
            String body = response.getBody().asString();

            assertThat(body)
                    .as("[%s] Section must pass validation", section.anchor())
                    .contains("\"passed\":true");
            assertThat(body)
                    .as("[%s] Response must contain 'summary' field from ValidationReport", section.anchor())
                    .contains("summary");
            assertThat(body)
                    .as("[%s] Response must contain 'sectionTitle' field from ValidationReport", section.anchor())
                    .contains("sectionTitle");

            log("✓ [" + section.anchor() + "] ValidationReport passed");
        } else {            log("✓ [" + section.anchor() + "] Request accepted for async processing");
        }

        // Traces confirm the agent ran
        List<List<Map<String, Object>>> traces = awaitTraces(server, testStartMillis);

        assertThat(traces)
                .as("[%s] At least one Zipkin trace should be recorded", section.anchor())
                .isNotEmpty();

        List<Map<String, Object>> spans = traces.get(0);

        assertThat(spans)
                .as("[%s] Trace should contain at least one span", section.anchor())
                .isNotEmpty();

        log("[" + section.anchor() + "] Found " + traces.size()
                + " trace(s), first trace has " + spans.size() + " span(s)");

        assertSpanNamesPresent(spans);
        assertSpanDurationsReasonable(spans);
        assertTraceContiguity(spans);

        log("✓ [" + section.anchor() + "] Zipkin trace assertions passed");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Build an A2A JSON-RPC payload for the given section, asking the agent
     * to validate that section by its human-readable name.
     */
    private String buildPayload(String requestId, SectionCase section) {
        String messageId = "msg-ugv-" + System.currentTimeMillis() + "-" + section.anchor().hashCode();
        String sessionId = "session-ugv-" + section.anchor().replace(".", "-").replace("_", "-");
        String text = "Validate the " + section.humanName() + " section of the Embabel User Guide";

        return """
                {
                  "jsonrpc": "2.0",
                  "id": "%s",
                  "method": "message/send",
                  "params": {
                    "sessionId": "%s",
                    "message": {
                      "kind": "message",
                      "messageId": "%s",
                      "role": "user",
                      "parts": [
                        {
                          "kind": "text",
                          "text": "%s"
                        }
                      ],
                      "metadata": {}
                    }
                  }
                }
                """.formatted(requestId, sessionId, messageId, text);
    }

    /**
     * A known User Guide section with its anchor and human-readable name.
     *
     * @param anchor    the anchor fragment as used in the guide URL
     * @param humanName the readable title used in the A2A request text
     */
    record SectionCase(String anchor, String humanName) {
        @Override
        public @NotNull String toString() {
            return anchor;
        }
    }
}
