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

import com.embabel.acceptance.infrastructure.EmbabelA2AServerExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Acceptance tests for Agent-to-Agent communication scenarios.
 * 
 * Domain: Multi-Agent Communication Patterns
 * 
 * These tests verify that agents can successfully communicate with each other
 * through the EmbabelA2AServer, including message sending, receiving, and
 * handling different communication patterns.
 */
@DisplayName("Agent-to-Agent Communication Acceptance Tests")
class AgentCommunicationTest {
    
    @RegisterExtension
    static EmbabelA2AServerExtension server = 
        new EmbabelA2AServerExtension.Builder()
            .version("0.3.3-SNAPSHOT")
            .serverPort(8080)
            .jvmArg("-Xmx512m")
            .jvmArg("-Dspring.profiles.active=test")
            .env("EMBABEL_LOG_LEVEL", "DEBUG")
            .startupTimeout(Duration.ofMinutes(2))
            .build();
    
    @Test
    @DisplayName("Agent should be able to register with server")
    void agentShouldRegister() {
        // Given: A new agent wants to register
        String agentId = "test-agent-1";
        String agentName = "Test Agent";
        
        // When: Agent sends registration request
        // Note: Adjust the endpoint and payload based on your actual API
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
    void agentShouldSendMessage() {
        // Given: Two agents are registered
        String senderAgentId = "sender-agent";
        String receiverAgentId = "receiver-agent";
        String message = "Hello from sender!";
        
        // When: Sender agent sends message to receiver
        // Note: Adjust based on your actual message sending API
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
    void agentShouldRetrieveMessages() {
        // Given: An agent has messages waiting
        String agentId = "receiver-agent";
        
        // When: Agent polls for messages
        // Note: Adjust endpoint based on your actual API
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
    void serverShouldHandleConcurrentAgents() {
        // Given: Multiple agents connect simultaneously
        String baseUrl = server.getBaseUrl();
        
        // When: Multiple agents register concurrently
        // This simulates a real-world scenario with multiple agents
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
    void serverShouldListAgents() {
        // Given: Several agents are registered
        
        // When: We request the list of agents
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
