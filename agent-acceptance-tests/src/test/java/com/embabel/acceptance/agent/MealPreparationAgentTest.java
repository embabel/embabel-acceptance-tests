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
 * Acceptance tests for Meal Preparation Agent.
 */
@ExtendWith(EmbabelA2AServerExtension.class)
@DisplayName("Meal Preparation Agent Tests - Cook Meal")
@Disabled("Disabled until passes A2A Inspector checks")
class MealPreparationAgentTest extends AbstractA2ATest {

    @Override
    protected String getPayloadPath() {
        return "payloads/meal-preparation-request.json";
    }
    
    @Override
    protected String getRequestId() {
        return "req-009";
    }

    @Test
    @DisplayName("Should prepare meal according to order")
    void shouldPrepareMeal(ServerInfo server) {
        log("Requesting meal preparation at: " + server.getBaseUrl());
        
        Response response = sendA2ARequest(server, payload);
        
        assertSuccessfulA2AResponse(response);
        
        if (response.getStatusCode() == 200) {
            assertJsonRpcCompliance(response);
            assertContentPresent(response);
            log("✓ Meal prepared successfully");
        } else {
            log("✓ Meal preparation request accepted for async processing");
        }
    }
}
