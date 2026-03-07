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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the Embabel User Guide has at least 8 top-level chapters.
 *
 * <p>The structural check is pure Java inside the agent — no LLM call is made.
 * If the guide has fewer than 8 chapters, {@code structureValid=false} and the
 * agent returns {@code passed=false} with {@code "structural check"} in the summary.
 */
@ExtendWith(EmbabelA2AServerExtension.class)
@DisplayName("User Guide — Structural Integrity")
class UserGuideStructureTest extends AbstractA2ATest {

    private static final int MIN_CHAPTERS = 8;

    @Override
    protected Optional<String> getPayloadPath() {
        return Optional.of("payloads/user-guide-structure-request.json");
    }

    @Override
    protected String getRequestId() {
        return "req-ugv-structure";
    }

    @Test
    @DisplayName("User Guide must have at least " + MIN_CHAPTERS + " top-level chapters")
    void userGuideMustHaveAtLeastMinChapters(ServerInfo server) {
        log("Checking User Guide structural integrity (expected >= " + MIN_CHAPTERS + " chapters)");

        Response response = sendA2ARequest(server, payload);
        assertSuccessfulA2AResponse(response);

        if (response.getStatusCode() == 200) {
            String body = response.getBody().asString();

            assertThat(body)
                    .as("User Guide must have at least %d top-level chapters — " +
                        "if this fails, TOP_LEVEL_ANCHORS in UserGuideValidatorAgent needs updating", MIN_CHAPTERS)
                    .contains("\"passed\":true");

            log("✓ User Guide structural integrity confirmed (>= " + MIN_CHAPTERS + " chapters)");
        } else {
            log("✓ Structural check request accepted for async processing");
        }
    }
}
