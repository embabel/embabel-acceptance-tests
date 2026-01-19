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
package com.embabel.acceptance.basic;

import com.embabel.acceptance.jupiter.EmbabelA2AServerExtension;
import com.embabel.acceptance.jupiter.EmbabelA2AServerExtension.ServerInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

@ExtendWith(EmbabelA2AServerExtension.class)
@DisplayName("EmbabelA2AServer Basic Acceptance Tests")
class EmbabelA2AServerBasicTest {
    
    @Test
    @DisplayName("Server should start successfully and be accessible")
    void serverShouldStart(ServerInfo server) {
        boolean isRunning = server.getContainer().isRunning();
        assertThat(isRunning).isTrue();
        
        int port = server.getPort();
        assertThat(port).isGreaterThan(0);
        
        String baseUrl = server.getBaseUrl();
        assertThat(baseUrl).contains("http://");
        assertThat(baseUrl).contains(String.valueOf(port));
        
        System.out.println("✓ Server started successfully at: " + baseUrl);
    }
    
    @Test
    @DisplayName("Server should respond to health check")
    void serverShouldRespondToHealthCheck(ServerInfo server) {
        String baseUrl = server.getBaseUrl();
        
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
    void serverInfoShouldBeAccessible(ServerInfo server) {
        String baseUrl = server.getBaseUrl();
        
        given()
            .baseUri(baseUrl)
            .when()
            .get("/actuator/info")
            .then()
            .statusCode(anyOf(is(200), is(404)));
        
        System.out.println("✓ Server info endpoint checked");
    }
}
