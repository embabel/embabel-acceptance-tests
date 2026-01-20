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

import com.embabel.acceptance.jupiter.EmbabelA2AServerExtension.ServerInfo;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Abstract base class for A2A agent acceptance tests.
 * Provides common functionality for payload handling and domain-driven assertions.
 */
public abstract class AbstractA2ATest {

    protected static final String A2A_ENDPOINT = "/a2a";
    protected static final String JSONRPC_VERSION = "2.0";
    
    protected String payload;

    @BeforeEach
    void setUp() throws IOException {
        payload = loadJsonPayload(getPayloadPath());
    }

    protected abstract String getPayloadPath();
    protected abstract String getRequestId();

    protected void assertSuccessfulA2AResponse(Response response) {
        int statusCode = response.getStatusCode();
        log("Response status: " + statusCode);
        
        assertThat(statusCode)
            .as("Server should accept the message with 200 (OK) or 202 (Accepted)")
            .isIn(200, 202);
    }
    
    protected void assertJsonRpcCompliance(Response response) {
        response.then()
            .body("jsonrpc", equalTo(JSONRPC_VERSION))
            .body("id", equalTo(getRequestId()))
            .body("$", anyOf(hasKey("result"), hasKey("error")));
    }
    
    protected void assertContentPresent(Response response) {
        Object result = response.path("result");
        assertThat(result)
            .as("Response should contain result object")
            .isNotNull();
    }
    
    protected Response sendA2ARequest(ServerInfo server, String requestPayload) {
        return given()
            .log().all()
            .baseUri(server.getBaseUrl())
            .contentType(ContentType.JSON)
            .body(requestPayload)
            .when()
            .post(A2A_ENDPOINT)
            .then()
            .log().all()
            .extract()
            .response();
    }
    
    protected String loadJsonPayload(String resourcePath) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        
        if (!resource.exists()) {
            throw new IllegalArgumentException(
                "Payload file not found: " + resourcePath + 
                ". Make sure the file exists in src/test/resources/" + resourcePath
            );
        }
        
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }
    
    protected void log(String message) {
        System.out.println("[" + getClass().getSimpleName() + "] " + message);
    }
}
