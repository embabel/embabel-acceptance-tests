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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;

/**
 * Verbose debug test to diagnose Maven download issues.
 * This test enables Maven debug output to see exactly what's happening.
 */
@DisplayName("Verbose Maven Debug Test")
class VerboseMavenDebugTest {
    
    @RegisterExtension
    static EmbabelA2AServerExtension server = 
        new EmbabelA2AServerExtension.Builder()
            .version("0.3.3-SNAPSHOT")
            .serverPort(8080)
            .enableLogging(true)      // Show all container output
            .startupTimeout(Duration.ofMinutes(5))
            .build();
    
    @Test
    @DisplayName("Debug Maven download - check console for detailed Maven output")
    void debugMavenDownload() {
        System.out.println("\n=== SERVER STARTED SUCCESSFULLY ===");
        System.out.println("Server URL: " + server.getBaseUrl());
        System.out.println("Port: " + server.getMappedPort());
        System.out.println("\nIf you see this message, the artifact was downloaded successfully!");
    }
}
