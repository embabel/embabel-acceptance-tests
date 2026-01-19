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
@DisplayName("Book Writer Agent Tests")
class BookWriterAgentTest {

    @Test
    @DisplayName("Should publish book about Kotlin and Spring")
    void shouldPublishBookAboutKotlinAndSpring(ServerInfo server) throws IOException {
        String baseUrl = server.getBaseUrl();
        String payload = loadJsonPayload("payloads/book-publish-request.json");
        
        System.out.println("Requesting book publication at: " + baseUrl);
        
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
            .as("Server should accept the book publication request")
            .isIn(200, 202);
        
        if (statusCode == 200) {
            response.then()
                .body("jsonrpc", equalTo("2.0"))
                .body("id", equalTo("book-001"))
                .body("result", notNullValue());
            
            System.out.println("✓ Book publication completed successfully");
        } else {
            System.out.println("✓ Book publication request accepted for async processing");
        }
    }
    
    @Test
    @DisplayName("Should validate JSON-RPC protocol compliance")
    void shouldValidateJsonRpcProtocol(ServerInfo server) throws IOException {
        String baseUrl = server.getBaseUrl();
        String payload = loadJsonPayload("payloads/book-publish-request.json");
        
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
    @DisplayName("Should publish book about Domain Driven Design")
    void shouldPublishBookAboutDDD(ServerInfo server) {
        String baseUrl = server.getBaseUrl();
        
        String payload = """
            {
              "jsonrpc": "2.0",
              "id": "book-ddd-001",
              "method": "com.embabel.example.crew.bookwriter.BookWriter.publishBook",
              "params": {
                "topic": "Domain Driven Design patterns in modern software architecture"
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
                .as("Result should contain published book details")
                .isNotEmpty();
            
            System.out.println("✓ Domain Driven Design book published");
        }
    }
    
    @Test
    @DisplayName("Should publish book about microservices")
    void shouldPublishBookAboutMicroservices(ServerInfo server) {
        String baseUrl = server.getBaseUrl();
        
        String payload = """
            {
              "jsonrpc": "2.0",
              "id": "book-microservices-001",
              "method": "com.embabel.example.crew.bookwriter.BookWriter.publishBook",
              "params": {
                "topic": "Building microservices with Spring Boot and Kubernetes"
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
        
        System.out.println("✓ Microservices book publication processed");
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
