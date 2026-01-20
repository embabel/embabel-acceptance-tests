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
 * Acceptance tests for Book Writer Agent.
 */
@ExtendWith(EmbabelA2AServerExtension.class)
@DisplayName("Book Writer Agent Tests")
@Disabled("Disabled until passes A2A Inspector checks")
class BookWriterAgentTest extends AbstractA2ATest {

    @Override
    protected String getPayloadPath() {
        return "payloads/book-writer-request.json";
    }
    
    @Override
    protected String getRequestId() {
        return "req-002";
    }

    @Test
    @DisplayName("Should publish book about Kotlin and Spring")
    void shouldPublishBookAboutKotlinAndSpring(ServerInfo server) {
        log("Requesting book publication at: " + server.getBaseUrl());
        
        Response response = sendA2ARequest(server, payload);
        
        assertSuccessfulA2AResponse(response);
        
        if (response.getStatusCode() == 200) {
            assertJsonRpcCompliance(response);
            assertContentPresent(response);
            log("✓ Book publication completed successfully");
        } else {
            log("✓ Book publication request accepted for async processing");
        }
    }
}
