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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

    // Maven artifact coordinates
    private static final String GROUP_ID = "com.embabel.example.java";
    private static final String ARTIFACT_ID = "example-agent-java";
    private static final String DEFAULT_VERSION = "0.3.3-SNAPSHOT";

    // Repository URLs
    private static final List<String> MAVEN_REPOSITORIES = List.of(
            "https://repo.embabel.com/artifactory/libs-release",
            "https://repo.embabel.com/artifactory/libs-snapshot"
    );

    // Container configuration
    private static final int DEFAULT_SERVER_PORT = 8080;
    private static final String DEFAULT_JVM_ARGS = "-Xmx1024m";
    private static final Duration DEFAULT_STARTUP_TIMEOUT = Duration.ofMinutes(2);
    private static final String DOCKER_BASE_IMAGE = "eclipse-temurin:21-jre-alpine";

    // Extension context keys
    private static final String STORE_KEY_CONTAINER = "embabelContainer";
    private static final String STORE_KEY_PORT = "embabelPort";
    private static final String STORE_KEY_BASE_URL = "embabelBaseUrl";

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
            this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl cannot be null");
            this.port = port;
            this.container = Objects.requireNonNull(container, "container cannot be null");
        }

        public String getBaseUrl() { return baseUrl; }
        public int getPort() { return port; }
        public GenericContainer<?> getContainer() { return container; }
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (!isContainerRunning()) {
            synchronized (LOCK) {
                if (!isContainerRunning()) {
                    initializeContainer();
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
        var store = extensionContext.getStore(ExtensionContext.Namespace.GLOBAL);
        String baseUrl = store.get(STORE_KEY_BASE_URL, String.class);
        Integer port = store.get(STORE_KEY_PORT, Integer.class);
        return new ServerInfo(baseUrl, port, container);
    }

    private boolean isContainerRunning() {
        return container != null && container.isRunning();
    }

    private void initializeContainer() throws Exception {
        log("Initializing container (singleton - shared across all tests)");

        downloadedJarPath = downloadJarLocally();
        var image = buildDockerImage(downloadedJarPath);
        var envVars = buildEnvironmentVariables();

        container = new GenericContainer<>(image)
                .withExposedPorts(DEFAULT_SERVER_PORT)
                .withEnv(envVars)
                .withLogConsumer(frame -> System.out.print("[EmbabelA2A] " + frame.getUtf8String()))
                .waitingFor(Wait.forListeningPort().withStartupTimeout(DEFAULT_STARTUP_TIMEOUT));

        log("Starting container...");
        container.start();
        log("Container started on port " + getMappedPort());

        registerShutdownHook();
    }

    private ImageFromDockerfile buildDockerImage(Path jarPath) {
        return new ImageFromDockerfile()
                .withFileFromPath("app.jar", jarPath)
                .withDockerfileFromBuilder(this::buildDockerfile);
    }

    private Map<String, String> buildEnvironmentVariables() {
        return Map.of(
                "OPENAI_API_KEY", getEnvironmentVariable("EMBABEL_OI_API_KEY"),
                "ANTHROPIC_API_KEY", getEnvironmentVariable("EMBABEL_AC_API_KEY"),
                "AWS_REGION", "us-east-2",
                "AWS_ACCESS_KEY_ID", getEnvironmentVariable("EMBABEL_AS_KEY_ID"),
                "AWS_SECRET_ACCESS_KEY", getEnvironmentVariable("EMBABEL_ST_AS_KEY")
                );
    }

    private String getEnvironmentVariable(String key) {
        return Optional.ofNullable(System.getenv(key))
                .orElseThrow(() -> new IllegalStateException("Environment variable not set: " + key));
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (isContainerRunning()) {
                log("Stopping container on JVM shutdown");
                try {
                    container.stop();
                } finally {
                    cleanupDownloadedJar();
                }
            }
        }));
    }

    private void cleanupDownloadedJar() {
        if (downloadedJarPath != null) {
            try {
                Files.deleteIfExists(downloadedJarPath);
                Files.deleteIfExists(downloadedJarPath.getParent());
            } catch (IOException e) {
                System.err.println("Failed to cleanup downloaded jar: " + e.getMessage());
            }
        }
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

        List<String> command = buildMavenCommand(tempDir);

        int exitCode = executeMavenCommand(command);
        if (exitCode != 0) {
            throw new RuntimeException("Maven download failed with exit code: " + exitCode);
        }

        if (!Files.exists(jarPath)) {
            throw new RuntimeException("JAR not found at expected path: " + jarPath);
        }

        log("Downloaded JAR to: " + jarPath);
        return jarPath;
    }

    private List<String> buildMavenCommand(Path outputDirectory) {
        String mavenExecutable = getMavenExecutable();
        String repositoriesArg = buildRepositoriesArgument();

        List<String> command = new ArrayList<>();
        command.add(mavenExecutable);
        command.add("dependency:copy");
        command.add("-U");
        command.add(String.format("-Dartifact=%s:%s:%s:jar", GROUP_ID, ARTIFACT_ID, DEFAULT_VERSION));
        command.add("-DoutputDirectory=" + outputDirectory.toAbsolutePath());
        command.add("-Dmdep.stripVersion=true");

        if (!repositoriesArg.isEmpty()) {
            command.add("-DremoteRepositories=" + repositoriesArg);
        }

        return command;
    }

    private String getMavenExecutable() {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String wrapperName = isWindows ? "mvnw.cmd" : "mvnw";

        Path currentDir = Paths.get(System.getProperty("user.dir"));

        while (currentDir != null) {
            Path wrapperPath = currentDir.resolve(wrapperName);

            if (Files.exists(wrapperPath) && Files.isExecutable(wrapperPath)) {
                log("Using Maven wrapper: " + wrapperPath);
                return wrapperPath.toString();
            }

            currentDir = currentDir.getParent();
        }

        // Fall back to system Maven (useful for CI/CD environments)
        String systemMaven = isWindows ? "mvn.cmd" : "mvn";
        log("Maven wrapper not found, using system Maven: " + systemMaven);
        return systemMaven;
    }

    private String buildRepositoriesArgument() {
        return IntStream.range(0, MAVEN_REPOSITORIES.size())
                .mapToObj(i -> String.format("repo%d::default::%s", i + 1, MAVEN_REPOSITORIES.get(i)))
                .collect(Collectors.joining(","));
    }

    private int executeMavenCommand(List<String> command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            reader.lines().forEach(line -> System.out.println("[Maven] " + line));
        }

        return process.waitFor();
    }

    private void buildDockerfile(DockerfileBuilder builder) {
        builder.from(DOCKER_BASE_IMAGE);
        builder.workDir("/app");
        builder.copy("app.jar", "/app/app.jar");
        builder.expose(DEFAULT_SERVER_PORT);
        builder.entryPoint("java", DEFAULT_JVM_ARGS, "-jar", "/app/app.jar");
    }

    private void storeInContext(ExtensionContext context) {
        var store = context.getStore(ExtensionContext.Namespace.GLOBAL);
        store.put(STORE_KEY_CONTAINER, container);
        store.put(STORE_KEY_PORT, getMappedPort());
        store.put(STORE_KEY_BASE_URL, getBaseUrl());
    }

    private void log(String message) {
        System.out.println("[EmbabelA2AServerExtension] " + message);
    }
}
