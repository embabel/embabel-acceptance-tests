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
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

@ExtendWith(EmbabelA2AServerExtension.class)
@DisplayName("Wikipedia Research Agent Tests")
class WikiAgentTest {

    @Test
    @DisplayName("Should perform Wikipedia research on Kotlin programming language")
    void shouldResearchKotlinOnWikipedia(ServerInfo server) throws IOException {
        String baseUrl = server.getBaseUrl();
        String payload = loadJsonPayload("payloads/wiki-research-request.json");
        
        System.out.println("Requesting Wikipedia research at: " + baseUrl);
        
        Response response = given()
            .log().all()
            .baseUri(baseUrl)
            .contentType(ContentType.JSON)
            .body(payload)
            .when()
            .post("/a2a")
            .then()
            .log().all()
            .extract()
            .response();
        
        int statusCode = response.getStatusCode();
        System.out.println("Response status: " + statusCode);
        
        assertThat(statusCode)
            .as("Server should accept the research request")
            .isIn(200, 202);
        
        if (statusCode == 200) {
            response.then()
                .body("jsonrpc", equalTo("2.0"))
                .body("id", equalTo("wiki-001"))
                .body("result", notNullValue());
            
            System.out.println("✓ Wikipedia research completed successfully");
        } else {
            System.out.println("✓ Research request accepted for async processing");
        }
    }
    
    @Test
    @DisplayName("Should validate JSON-RPC protocol compliance for wiki research")
    void shouldValidateJsonRpcProtocol(ServerInfo server) throws IOException {
        String baseUrl = server.getBaseUrl();
        String payload = loadJsonPayload("payloads/wiki-research-request.json");
        
        Response response = given()
            .baseUri(baseUrl)
            .contentType(ContentType.JSON)
            .body(payload)
            .when()
            .post("/a2a")
            .then()
            .extract()
            .response();
        
        if (response.getStatusCode() == 200) {
            response.then()
                .body("jsonrpc", equalTo("2.0"))
                .body("$", hasKey("id"))
                .body("$", anyOf(hasKey("result"), hasKey("error")));
            
            boolean hasResult = response.path("result") != null;
            boolean hasError = response.path("error") != null;
            
            assertThat(hasResult ^ hasError)
                .as("JSON-RPC response must have either result or error, not both")
                .isTrue();
            
            System.out.println("✓ JSON-RPC protocol compliance validated");
        }
    }
    
    @Test
    @DisplayName("Should research Domain Driven Design")
    void shouldResearchDomainDrivenDesign(ServerInfo server) {
        String baseUrl = server.getBaseUrl();
        
        String payload = """
            {
              "jsonrpc": "2.0",
              "id": "wiki-ddd-001",
              "method": "com.embabel.example.wikipedia.WikiAgent.performResearch",
              "params": {
                "query": "Domain Driven Design by Eric Evans"
              }
            }
            """;
        
        Response response = given()
            .log().all()
            .baseUri(baseUrl)
            .contentType(ContentType.JSON)
            .body(payload)
            .when()
            .post("/a2a")
            .then()
            .log().all()
            .extract()
            .response();
        
        assertThat(response.getStatusCode()).isIn(200, 202);
        
        if (response.getStatusCode() == 200) {
            String result = response.path("result").toString();
            assertThat(result)
                .as("Result should contain research findings")
                .isNotEmpty();
            
            System.out.println("✓ Domain Driven Design research completed");
        }
    }
    
    @Test
    @DisplayName("Should research Spring Framework")
    void shouldResearchSpringFramework(ServerInfo server) {
        String baseUrl = server.getBaseUrl();
        
        String payload = """
            {
              "jsonrpc": "2.0",
              "id": "wiki-spring-001",
              "method": "com.embabel.example.wikipedia.WikiAgent.performResearch",
              "params": {
                "query": "Spring Framework Java"
              }
            }
            """;
        
        Response response = given()
            .baseUri(baseUrl)
            .contentType(ContentType.JSON)
            .body(payload)
            .when()
            .post("/a2a")
            .then()
            .extract()
            .response();
        
        assertThat(response.getStatusCode()).isIn(200, 202);
        
        System.out.println("✓ Spring Framework research processed");
    }
    
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
