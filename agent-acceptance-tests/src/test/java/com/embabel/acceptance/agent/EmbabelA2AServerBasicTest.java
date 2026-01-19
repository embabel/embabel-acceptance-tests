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
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Acceptance tests for EmbabelA2AServer basic functionality.
 * 
 * Domain: Agent-to-Agent Communication Server Verification
 * 
 * These tests verify that the EmbabelA2AServer can be successfully launched
 * from Embabel Artifactory and is ready to accept agent communication requests.
 * 
 * No authentication required - Embabel Artifactory is publicly accessible.
 */
@DisplayName("EmbabelA2AServer Basic Acceptance Tests")
class EmbabelA2AServerBasicTest {
    
    /**
     * Simple configuration - just specify the version!
     * The extension automatically downloads from Embabel Artifactory.
     * No credentials needed.
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
    @DisplayName("Server should start successfully and be accessible")
    void serverShouldStart() {
        // Given: Server is started via extension
        
        // When: We check if container is running
        boolean isRunning = server.getContainer().isRunning();
        
        // Then: Server should be running
        assertThat(isRunning).isTrue();
        
        // And: Server should have a mapped port
        int port = server.getMappedPort();
        assertThat(port).isGreaterThan(0);
        
        // And: Base URL should be properly formed
        String baseUrl = server.getBaseUrl();
        assertThat(baseUrl).contains("http://");
        assertThat(baseUrl).contains(String.valueOf(port));
        
        System.out.println("✓ Server started successfully at: " + baseUrl);
    }
    
    @Test
    @DisplayName("Server should respond to health check")
    void serverShouldRespondToHealthCheck() {
        // Given: Server is running
        String baseUrl = server.getBaseUrl();
        
        // When: We call the health endpoint
        // Note: Adjust the endpoint path based on your actual API
        given()
            .baseUri(baseUrl)
            .when()
            .get("/actuator/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
        
        System.out.println("✓ Health check passed");
    }
    
    @Test
    @DisplayName("Server information should be accessible")
    void serverInfoShouldBeAccessible() {
        // Given: Server is running
        String baseUrl = server.getBaseUrl();
        
        // When: We request server information
        // Note: Adjust based on your actual API endpoints
        given()
            .baseUri(baseUrl)
            .when()
            .get("/actuator/info")
            .then()
            .statusCode(anyOf(is(200), is(404))); // 404 if info endpoint not configured
        
        System.out.println("✓ Server info endpoint checked");
    }
}
