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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Acceptance tests for Wikipedia Research Agent.
 */
@ExtendWith(EmbabelA2AServerExtension.class)
@DisplayName("Wikipedia Research Agent Tests")
@Disabled("Disabled until passes A2A Inspector checks")
class WikiAgentTest extends AbstractA2ATest {

    @Override
    protected String getPayloadPath() {
        return "payloads/wiki-research-request.json";
    }
    
    @Override
    protected String getRequestId() {
        return "req-008";
    }

    @Test
    @DisplayName("Should perform Wikipedia research on Kotlin")
    void shouldResearchKotlinOnWikipedia(ServerInfo server) {
        log("Requesting Wikipedia research at: " + server.getBaseUrl());
        
        Response response = sendA2ARequest(server, payload);
        
        assertSuccessfulA2AResponse(response);
        
        if (response.getStatusCode() == 200) {
            assertJsonRpcCompliance(response);
            assertContentPresent(response);
            log("✓ Wikipedia research completed successfully");
        } else {
            log("✓ Research request accepted for async processing");
        }
    }
}
