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
package com.embabel.acceptance.jupiter;

import org.junit.jupiter.api.extension.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.images.builder.dockerfile.DockerfileBuilder;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

/**
 * JUnit5 Extension for managing EmbabelA2AServer container lifecycle.
 * SINGLETON pattern - container initialized ONCE and shared across all test classes.
 * 
 * Usage in test classes:
 * <pre>
 * {@code
 * @ExtendWith(EmbabelA2AServerExtension.class)
 * class MyTest {
 *     @Test
 *     void test(ServerInfo server) {
 *         String baseUrl = server.getBaseUrl();
 *     }
 * }
 * }
 * </pre>
 */
public class EmbabelA2AServerExtension implements BeforeAllCallback, AfterAllCallback, ParameterResolver {
    
    private static final String GROUP_ID = "com.embabel.example.java";
    private static final String ARTIFACT_ID = "example-agent-java";
    private static final String EMBABEL_RELEASES_REPO = "https://repo.embabel.com/artifactory/libs-release";
    private static final String EMBABEL_SNAPSHOTS_REPO = "https://repo.embabel.com/artifactory/libs-snapshot";

    // Default configuration - modify here for all tests
    private static final String DEFAULT_VERSION = "0.3.3-SNAPSHOT";
    private static final int DEFAULT_SERVER_PORT = 8080;
    private static final String DEFAULT_JVM_ARGS = "-Xmx512m";
    private static final Duration DEFAULT_STARTUP_TIMEOUT = Duration.ofMinutes(2);

    // Singleton container - shared across ALL test classes
    private static volatile GenericContainer<?> container;
    private static volatile Path downloadedJarPath;
    private static final Object LOCK = new Object();
    
    /**
     * Server information holder for parameter injection.
     */
    public static class ServerInfo {
        private final String baseUrl;
        private final int port;
        private final GenericContainer<?> container;
        
        public ServerInfo(String baseUrl, int port, GenericContainer<?> container) {
            this.baseUrl = baseUrl;
            this.port = port;
            this.container = container;
        }
        
        public String getBaseUrl() { return baseUrl; }
        public int getPort() { return port; }
        public GenericContainer<?> getContainer() { return container; }
    }
    
    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (container == null || !container.isRunning()) {
            synchronized (LOCK) {
                if (container == null || !container.isRunning()) {
                    log("Initializing container (singleton - shared across all tests)");
                    downloadedJarPath = downloadJarLocally();
                    
                    Consumer<DockerfileBuilder> dockerfileBuilder = this::buildDockerfile;
                    ImageFromDockerfile image = new ImageFromDockerfile()
                        .withFileFromPath("app.jar", downloadedJarPath)
                        .withDockerfileFromBuilder(dockerfileBuilder);

                    Map<String, String> envVars = new HashMap<>();
                    envVars.put("OPENAI_API_KEY", System.getenv().get("OPENAI_API_KEY"));
                    envVars.put("ANTHROPIC_API_KEY", System.getenv().get("ANTHROPIC_API_KEY"));
                    
                    container = new GenericContainer<>(image)
                        .withExposedPorts(DEFAULT_SERVER_PORT)
                        .withEnv(envVars)
                        .waitingFor(Wait.forListeningPort().withStartupTimeout(DEFAULT_STARTUP_TIMEOUT));
                    
                    container.withLogConsumer(frame -> System.out.print("[EmbabelA2A] " + frame.getUtf8String()));
                    
                    log("Starting container...");
                    container.start();
                    log("Container started on port " + getMappedPort());
                    
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        if (container != null && container.isRunning()) {
                            log("Stopping container on JVM shutdown");
                            container.stop();
                            if (downloadedJarPath != null) {
                                try {
                                    Files.deleteIfExists(downloadedJarPath);
                                    Files.deleteIfExists(downloadedJarPath.getParent());
                                } catch (IOException e) { e.printStackTrace(); }
                            }
                        }
                    }));
                } else {
                    log("Reusing existing container (singleton)");
                }
            }
        } else {
            log("Reusing existing container (singleton)");
        }
        
        storeInContext(context);
    }
    
    @Override
    public void afterAll(ExtensionContext context) {
        log("Test class completed - container remains running for other tests");
    }
    
    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType() == ServerInfo.class;
    }
    
    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        ExtensionContext.Store store = extensionContext.getStore(ExtensionContext.Namespace.GLOBAL);
        String baseUrl = store.get("embabelBaseUrl", String.class);
        Integer port = store.get("embabelPort", Integer.class);
        return new ServerInfo(baseUrl, port, container);
    }
    
    private int getMappedPort() {
        return container.getMappedPort(DEFAULT_SERVER_PORT);
    }
    
    private String getHost() {
        return container.getHost();
    }
    
    private String getBaseUrl() {
        return String.format("http://%s:%d", getHost(), getMappedPort());
    }
    
    private Path downloadJarLocally() throws Exception {
        Path tempDir = Files.createTempDirectory("embabel-test-");
        Path jarPath = tempDir.resolve(ARTIFACT_ID + ".jar");
        
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String mavenCommand = isWindows ? "c:\\tools\\apache\\maven\\apache-maven-3.9.6\\bin\\mvn.cmd" : "mvn";
        
        List<String> mavenRepositoryUrls = Arrays.asList(EMBABEL_RELEASES_REPO, EMBABEL_SNAPSHOTS_REPO);
        
        List<String> command = new ArrayList<>();
        command.add(mavenCommand);
        command.add("dependency:copy");
        command.add(String.format("-Dartifact=%s:%s:%s:jar", GROUP_ID, ARTIFACT_ID, DEFAULT_VERSION));
        command.add("-DoutputDirectory=" + tempDir.toAbsolutePath());
        command.add("-Dmdep.stripVersion=true");
        
        if (!mavenRepositoryUrls.isEmpty()) {
            StringBuilder reposList = new StringBuilder();
            int counter = 1;
            for (String repoUrl : mavenRepositoryUrls) {
                if (reposList.length() > 0) reposList.append(",");
                reposList.append("repo").append(counter++).append("::default::").append(repoUrl);
            }
            command.add("-DremoteRepositories=" + reposList);
        }
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        
        Map<String, String> env = pb.environment();

        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                System.out.println("[Maven] " + line);
            }
        }
        
        if (process.waitFor() != 0) {
            throw new RuntimeException("Maven download failed\n" + output);
        }
        if (!Files.exists(jarPath)) {
            throw new RuntimeException("JAR not found at: " + jarPath);
        }
        
        log("Downloaded JAR to: " + jarPath);
        return jarPath;
    }
    
    private void buildDockerfile(DockerfileBuilder builder) {
        builder.from("eclipse-temurin:21-jre-alpine");
        builder.workDir("/app");
        builder.copy("app.jar", "/app/app.jar");
        builder.expose(DEFAULT_SERVER_PORT);
        
        List<String> javaCmd = new ArrayList<>();
        javaCmd.add("java");
        javaCmd.add(DEFAULT_JVM_ARGS);
        javaCmd.add("-jar");
        javaCmd.add("/app/app.jar");
        
        builder.entryPoint(javaCmd.toArray(new String[0]));
    }
    
    private void storeInContext(ExtensionContext context) {
        ExtensionContext.Store store = context.getStore(ExtensionContext.Namespace.GLOBAL);
        store.put("embabelContainer", container);
        store.put("embabelPort", getMappedPort());
        store.put("embabelBaseUrl", getBaseUrl());
    }
    
    private void log(String message) {
        System.out.println("[EmbabelA2AServerExtension] " + message);
    }
}
