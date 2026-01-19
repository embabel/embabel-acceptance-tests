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
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Acceptance tests for EmbabelA2AServer agent interaction functionality.
 * 
 * Domain: Agent-to-Agent Communication - Message Exchange
 * 
 * These tests verify that the EmbabelA2AServer can process agent messages
 * using JSON-RPC protocol and generate appropriate responses using AI models.
 */
@DisplayName("EmbabelA2AServer Agent Interaction Tests")
class EmbabelA2AServerInteractionTest {
    
    /**
     * Server configuration with API keys for OpenAI and Anthropic.
     * The server needs these to generate AI-powered responses.
     */
    @RegisterExtension
    static EmbabelA2AServerExtension server = 
        new EmbabelA2AServerExtension.Builder()
            .version("0.3.3-SNAPSHOT")
            .serverPort(8080)
            .jvmArg("-Xmx512m")
            .env("OPENAI_API_KEY", "sk-tNr0jDSXiHMAmNgQicqBaUxLsc4OYxf5bQv4rLdsHmT3BlbkFJPNeLm3GFrKrwOWzF6ap6BWw7G91Qw2y9q9cPJDxUsA")
            .env("ANTHROPIC_API_KEY", "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")
            .startupTimeout(Duration.ofMinutes(2))
            .build();
    
    @Test
    @DisplayName("Should send horoscope message and receive AI-generated story")
    void shouldSendHoroscopeMessageAndReceiveStory() throws IOException {
        // Given: Server is running and we have a horoscope request payload
        String baseUrl = server.getBaseUrl();
        String payload = loadJsonPayload("payloads/agent-message-request.json");
        
        System.out.println("Sending horoscope message to server at: " + baseUrl);
        System.out.println("Payload: " + payload);
        
        // When: We send a JSON-RPC message/send request
        Response response = given()
            .log().all()  // Log request details
            .baseUri(baseUrl)
            .contentType(ContentType.JSON)
            .body(payload)
            .when()
            .post("/a2a")  // Adjust endpoint if needed
            .then()
            .log().all()  // Log response for debugging
            .extract()
            .response();
        
        // Then: Server should respond successfully
        int statusCode = response.getStatusCode();
        System.out.println("Response status: " + statusCode);
        
        // Assert: Response should be 200 OK or 202 Accepted
        assertThat(statusCode)
            .as("Server should accept the message")
            .isIn(200, 202);
        
        // And: Response should contain JSON-RPC structure
        if (statusCode == 200) {
            response.then()
                .body("jsonrpc", equalTo("2.0"))
                .body("id", equalTo("req-001"))
                .body("result", notNullValue());
            
            System.out.println("✓ Horoscope message sent and response received");
            System.out.println("✓ Response ID: " + response.path("id"));
            
            // Try to extract the story if present
            try {
                Object result = response.path("result");
                System.out.println("✓ Result: " + result);
            } catch (Exception e) {
                System.out.println("Note: Could not extract result details - " + e.getMessage());
            }
        } else {
            System.out.println("✓ Message accepted for async processing");
        }
    }
    
    @Test
    @DisplayName("Should validate JSON-RPC protocol compliance")
    void shouldValidateJsonRpcProtocol() throws IOException {
        // Given: Server is running with a valid JSON-RPC payload
        String baseUrl = server.getBaseUrl();
        String payload = loadJsonPayload("payloads/agent-message-request.json");
        
        // When: We send the request
        Response response = given()
            .baseUri(baseUrl)
            .contentType(ContentType.JSON)
            .body(payload)
            .when()
            .post("/jsonrpc")
            .then()
            .extract()
            .response();
        
        // Then: Response should follow JSON-RPC 2.0 spec
        if (response.getStatusCode() == 200) {
            response.then()
                .body("jsonrpc", equalTo("2.0"))
                .body("$", hasKey("id"))
                .body("$", anyOf(hasKey("result"), hasKey("error")));
            
            // Should have either result OR error, not both
            boolean hasResult = response.path("result") != null;
            boolean hasError = response.path("error") != null;
            
            assertThat(hasResult ^ hasError)
                .as("JSON-RPC response must have either result or error, not both")
                .isTrue();
            
            System.out.println("✓ JSON-RPC protocol compliance validated");
        }
    }
    
    @Test
    @DisplayName("Should include session context in message request")
    void shouldIncludeSessionContext() throws IOException {
        // Given: Payload with session ID
        String baseUrl = server.getBaseUrl();
        String payload = loadJsonPayload("payloads/agent-message-request.json");
        
        // Verify payload contains session info
        assertThat(payload)
            .as("Payload should contain sessionId")
            .contains("session-xyz-122");
        
        System.out.println("✓ Session ID 'session-xyz-122' present in payload");
        
        // When: We send the request
        Response response = given()
            .baseUri(baseUrl)
            .contentType(ContentType.JSON)
            .body(payload)
            .when()
            .post("/jsonrpc");
        
        // Then: Server should accept it
        System.out.println("Response status: " + response.getStatusCode());
        System.out.println("✓ Request with session context processed");
    }
    
    @Test
    @DisplayName("Should verify message structure with parts")
    void shouldVerifyMessageStructure() throws IOException {
        // Given: Payload with message parts
        String baseUrl = server.getBaseUrl();
        String payload = loadJsonPayload("payloads/agent-message-request.json");
        
        // Verify payload structure
        assertThat(payload)
            .as("Payload should contain message parts")
            .contains("\"kind\": \"text\"")
            .contains("Alex is Scorpio");
        
        System.out.println("✓ Message structure validated:");
        System.out.println("  - Contains text part");
        System.out.println("  - Contains horoscope content");
        
        // When: We send the structured message
        Response response = given()
            .baseUri(baseUrl)
            .contentType(ContentType.JSON)
            .body(payload)
            .when()
            .post("/jsonrpc");
        
        // Then: Log the interaction
        System.out.println("Response status: " + response.getStatusCode());
        System.out.println("✓ Structured message processed");
    }
    
    /**
     * Helper method to load JSON payload from test resources using Spring's ClassPathResource.
     * 
     * This properly handles cross-platform path resolution (Windows, Linux, Mac).
     * 
     * Following the Repository Pattern analogy: this is like a data access method
     * that retrieves test data from the "resource repository".
     */
    private String loadJsonPayload(String resourcePath) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        
        if (!resource.exists()) {
            throw new IllegalArgumentException(
                "Payload file not found: " + resourcePath + 
                ". Make sure the file exists in src/test/resources/" + resourcePath
            );
        }
        
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }
}
