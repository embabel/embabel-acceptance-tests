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

/**
 * Debug test to see the actual Maven error output
 */
@DisplayName("Debug Maven Download")
class DebugMavenDownloadTest {
    
    @RegisterExtension
    static EmbabelA2AServerExtension server = 
        new EmbabelA2AServerExtension.Builder()
            .version("0.3.3-SNAPSHOT")
            .serverPort(8080)
            .enableLogging(true)  // Enable full logging
            .startupTimeout(Duration.ofMinutes(5))  // More time to see logs
            .build();
    
    @Test
    @DisplayName("Try to start server - watch the Maven output")
    void debugTest() {
        // This test will show us the Maven error in detail
        System.out.println("Server started at: " + server.getBaseUrl());
    }
}
