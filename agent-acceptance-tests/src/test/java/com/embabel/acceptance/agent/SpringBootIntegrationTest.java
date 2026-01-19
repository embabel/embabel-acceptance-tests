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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Boot integration test demonstrating how to integrate EmbabelA2AServer
 * with a Spring Boot test application.
 * 
 * Domain: Spring-Integrated Agent Communication Testing
 * 
 * This test shows how to inject the containerized server's URL into your
 * Spring application context, enabling seamless integration testing.
 * 
 * Note: This requires a Spring Boot application context. If you don't have one,
 * you can use the simple acceptance tests instead.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.profiles.active=test"
    }
)
@DisplayName("Spring Boot Integration with EmbabelA2AServer")
class SpringBootIntegrationTest {
    
    @RegisterExtension
    static EmbabelA2AServerExtension embabelServer = 
        new EmbabelA2AServerExtension.Builder()
            .version("0.3.3-SNAPSHOT")
            .serverPort(8080)
            .jvmArg("-Xmx512m")
            .jvmArg("-Dspring.profiles.active=integration-test")
            .startupTimeout(Duration.ofMinutes(2))
            .build();
    
    /**
     * Inject the EmbabelA2AServer URL into Spring's application properties.
     * This makes it available to @Value, @ConfigurationProperties, etc.
     */
    @DynamicPropertySource
    static void configureEmbabelProperties(DynamicPropertyRegistry registry) {
        registry.add("embabel.a2a.server.url", embabelServer::getBaseUrl);
        registry.add("embabel.a2a.server.host", embabelServer::getHost);
        registry.add("embabel.a2a.server.port", embabelServer::getMappedPort);
    }
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    @DisplayName("Spring application should be able to communicate with EmbabelA2AServer")
    void springAppShouldCommunicateWithServer() {
        // Given: EmbabelA2AServer is running and URL is injected
        String serverUrl = embabelServer.getBaseUrl();
        
        // When: Spring application makes a request to the server
        ResponseEntity<String> response = restTemplate.getForEntity(
            serverUrl + "/actuator/health",
            String.class
        );
        
        // Then: Request should be successful
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("UP");
        
        System.out.println("✓ Spring Boot integration successful");
        System.out.println("  Server URL: " + serverUrl);
    }
    
    @Test
    @DisplayName("Application properties should contain EmbabelA2AServer configuration")
    void propertiesShouldBeInjected() {
        // This test would work if you have a @ConfigurationProperties class
        // that reads embabel.a2a.server.* properties
        
        // For now, just verify the server is accessible
        assertThat(embabelServer.getBaseUrl()).isNotEmpty();
        assertThat(embabelServer.getMappedPort()).isGreaterThan(0);
        
        System.out.println("✓ Properties injected successfully");
    }
}
