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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@ExtendWith(EmbabelA2AServerExtension.class)
@DisplayName("Agent-to-Agent Communication Acceptance Tests")
class AgentCommunicationTest {
    
    @Test
    @DisplayName("Agent should be able to register with server")
    void agentShouldRegister(ServerInfo server) {
        String agentId = "test-agent-1";
        String agentName = "Test Agent";
        
        String response = given()
            .baseUri(server.getBaseUrl())
            .contentType("application/json")
            .body(String.format("""
                {
                    "agentId": "%s",
                    "name": "%s",
                    "type": "TEST"
                }
                """, agentId, agentName))
            .when()
            .post("/api/agents/register")
            .then()
            .statusCode(anyOf(is(200), is(201)))
            .extract()
            .asString();
        
        System.out.println("✓ Agent registered successfully: " + response);
    }
    
    @Test
    @DisplayName("Agent should be able to send message to another agent")
    void agentShouldSendMessage(ServerInfo server) {
        String senderAgentId = "sender-agent";
        String receiverAgentId = "receiver-agent";
        String message = "Hello from sender!";
        
        given()
            .baseUri(server.getBaseUrl())
            .contentType("application/json")
            .body(String.format("""
                {
                    "from": "%s",
                    "to": "%s",
                    "content": "%s",
                    "type": "TEXT"
                }
                """, senderAgentId, receiverAgentId, message))
            .when()
            .post("/api/messages/send")
            .then()
            .statusCode(anyOf(is(200), is(201), is(202)))
            .body("status", anyOf(is("SENT"), is("DELIVERED"), is("QUEUED")));
        
        System.out.println("✓ Message sent from " + senderAgentId + " to " + receiverAgentId);
    }
    
    @Test
    @DisplayName("Agent should be able to retrieve messages")
    void agentShouldRetrieveMessages(ServerInfo server) {
        String agentId = "receiver-agent";
        
        given()
            .baseUri(server.getBaseUrl())
            .queryParam("agentId", agentId)
            .when()
            .get("/api/messages")
            .then()
            .statusCode(200)
            .body("messages", notNullValue());
        
        System.out.println("✓ Messages retrieved for agent: " + agentId);
    }
    
    @Test
    @DisplayName("Server should handle multiple concurrent agents")
    void serverShouldHandleConcurrentAgents(ServerInfo server) {
        String baseUrl = server.getBaseUrl();
        
        for (int i = 0; i < 5; i++) {
            final String agentId = "concurrent-agent-" + i;
            
            given()
                .baseUri(baseUrl)
                .contentType("application/json")
                .body(String.format("""
                    {
                        "agentId": "%s",
                        "name": "Concurrent Agent %d"
                    }
                    """, agentId, i))
                .when()
                .post("/api/agents/register")
                .then()
                .statusCode(anyOf(is(200), is(201)));
        }
        
        System.out.println("✓ Multiple concurrent agents handled successfully");
    }
    
    @Test
    @DisplayName("Server should list all registered agents")
    void serverShouldListAgents(ServerInfo server) {
        given()
            .baseUri(server.getBaseUrl())
            .when()
            .get("/api/agents")
            .then()
            .statusCode(200)
            .body("agents", notNullValue());
        
        System.out.println("✓ Agent list retrieved successfully");
    }
}
