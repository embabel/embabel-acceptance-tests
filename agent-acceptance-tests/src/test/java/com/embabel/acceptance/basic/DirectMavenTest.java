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

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;

import java.time.Duration;

/**
 * Test Maven download directly to see the actual error message
 */
public class DirectMavenTest {
    
    @Test
    void testMavenDownloadDirectly() {
        System.out.println("=== Testing Maven download directly ===\n");
        
        // Create a simple container that runs Maven and exits
        try (GenericContainer<?> maven = new GenericContainer<>("maven:3.9-eclipse-temurin-21-alpine")
                .withCommand(
                    "mvn",
                    "dependency:copy",
                    "-Dartifact=com.embabel.example.java:example-agent-java:0.3.3-SNAPSHOT:jar",
                    "-DoutputDirectory=/tmp",
                    "-Dmdep.stripVersion=true",
                    "-DremoteRepositories=repo1::default::https://repo.embabel.com/artifactory/libs-release,repo2::default::https://repo.embabel.com/artifactory/libs-snapshot"
                )
                .withStartupCheckStrategy(new OneShotStartupCheckStrategy().withTimeout(Duration.ofMinutes(2)))
                .withLogConsumer(new Slf4jLogConsumer(org.slf4j.LoggerFactory.getLogger("MAVEN")))
        ) {
            
            maven.start();
            
            // Check exit code
            Integer exitCode = maven.getCurrentContainerInfo()
                    .getState()
                    .getExitCodeLong()
                    .intValue();
            
            System.out.println("\n=== Maven Exit Code: " + exitCode + " ===");
            
            if (exitCode == 0) {
                System.out.println("✓ SUCCESS! Artifact downloaded successfully");
            } else {
                System.out.println("✗ FAILED! Maven returned error code: " + exitCode);
                System.out.println("\nCheck the logs above for the actual Maven error message.");
                System.out.println("Look for lines containing:");
                System.out.println("  - 'Could not find artifact'");
                System.out.println("  - '401 Unauthorized'");
                System.out.println("  - '404 Not Found'");
                System.out.println("  - Connection errors");
            }
            
        } catch (Exception e) {
            System.err.println("Error running Maven test: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
