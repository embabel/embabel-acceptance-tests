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
package com.embabel.acceptance.infrastructure;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
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
 * 
 * Downloads the JAR locally using Maven, then copies it into a Docker container.
 * This avoids Docker build-time network restrictions.
 */
public class EmbabelA2AServerExtension implements BeforeAllCallback, AfterAllCallback {
    
    private static final String GROUP_ID = "com.embabel.example.java";
    private static final String ARTIFACT_ID = "example-agent-java";
    private static final String EMBABEL_RELEASES_REPO = "https://repo.embabel.com/artifactory/libs-release";
    private static final String EMBABEL_SNAPSHOTS_REPO = "https://repo.embabel.com/artifactory/libs-snapshot";
    
    // MCP Plugin download URL - update this if using a different version or source
    private static final String MCP_PLUGIN_VERSION = "v0.1.0";  // Update to match your MCP version
    private static final String MCP_PLUGIN_BASE_URL = "https://github.com/modelcontextprotocol/mcp-docker-plugin/releases/download";
    
    private final String version;
    private final int serverPort;
    private final List<String> jvmArgs;
    private final List<String> serverArgs;
    private final Map<String, String> environment;
    private final Duration startupTimeout;
    private final List<String> mavenRepositoryUrls;
    private final boolean enableLogging;
    
    private GenericContainer<?> container;
    private Path downloadedJarPath;
    
    public static class Builder {
        private String version = "0.3.3-SNAPSHOT";
        private int serverPort = 8080;
        private List<String> jvmArgs = new ArrayList<>();
        private List<String> serverArgs = new ArrayList<>();
        private Map<String, String> environment = new HashMap<>();
        private Duration startupTimeout = Duration.ofSeconds(90);
        private List<String> mavenRepositoryUrls = new ArrayList<>();
        private boolean enableLogging = true;
        private boolean useEmbabelArtifactory = true;
        
        public Builder version(String version) {
            this.version = version;
            return this;
        }
        
        public Builder serverPort(int port) {
            this.serverPort = port;
            return this;
        }
        
        public Builder jvmArg(String arg) {
            this.jvmArgs.add(arg);
            return this;
        }
        
        public Builder serverArg(String arg) {
            this.serverArgs.add(arg);
            return this;
        }
        
        public Builder env(String key, String value) {
            this.environment.put(key, value);
            return this;
        }
        
        public Builder startupTimeout(Duration timeout) {
            this.startupTimeout = timeout;
            return this;
        }
        
        public Builder addMavenRepository(String url) {
            this.mavenRepositoryUrls.add(url);
            return this;
        }
        
        public Builder withEmbabelArtifactory(boolean use) {
            this.useEmbabelArtifactory = use;
            return this;
        }
        
        public Builder enableLogging(boolean enable) {
            this.enableLogging = enable;
            return this;
        }
        
        public EmbabelA2AServerExtension build() {
            // Add Embabel Artifactory repositories if enabled
            if (useEmbabelArtifactory) {
                if (!mavenRepositoryUrls.contains(EMBABEL_RELEASES_REPO)) {
                    mavenRepositoryUrls.add(EMBABEL_RELEASES_REPO);
                }
                if (!mavenRepositoryUrls.contains(EMBABEL_SNAPSHOTS_REPO)) {
                    mavenRepositoryUrls.add(EMBABEL_SNAPSHOTS_REPO);
                }
            }
            
            return new EmbabelA2AServerExtension(this);
        }
    }
    
    private EmbabelA2AServerExtension(Builder builder) {
        this.version = builder.version;
        this.serverPort = builder.serverPort;
        this.jvmArgs = new ArrayList<>(builder.jvmArgs);
        this.serverArgs = new ArrayList<>(builder.serverArgs);
        this.environment = new HashMap<>(builder.environment);
        this.startupTimeout = builder.startupTimeout;
        this.mavenRepositoryUrls = new ArrayList<>(builder.mavenRepositoryUrls);
        this.enableLogging = builder.enableLogging;
    }
    
    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        log("Downloading artifact locally: " + GROUP_ID + ":" + ARTIFACT_ID + ":" + version);
        
        // Download JAR locally using Maven
        downloadedJarPath = downloadJarLocally();
        
        log("Building Docker image with downloaded JAR, Docker CLI, and MCP plugin");
        
        // Build Docker image with the downloaded JAR
        Consumer<DockerfileBuilder> dockerfileBuilder = this::buildDockerfile;
        
        ImageFromDockerfile image = new ImageFromDockerfile()
            .withFileFromPath("app.jar", downloadedJarPath)  // Copy JAR into build context
            .withDockerfileFromBuilder(dockerfileBuilder);
        
        // Determine Docker socket path based on OS
        String dockerSocketPath = getDockerSocketPath();
        
        // Create and configure the container
        container = new GenericContainer<>(image)
            .withExposedPorts(serverPort)
            .withEnv(environment)
            .withFileSystemBind(dockerSocketPath, "/var/run/docker.sock")  // Mount Docker socket
            .withPrivilegedMode(true)  // Allow Docker commands
            .waitingFor(Wait.forListeningPort().withStartupTimeout(startupTimeout));
        
        // Add logging if enabled
        if (enableLogging) {
            container.withLogConsumer(frame -> 
                System.out.print("[EmbabelA2A] " + frame.getUtf8String())
            );
        }
        
        // Start the container
        log("Starting EmbabelA2AServer container...");
        log("Docker socket mounted from: " + dockerSocketPath);
        container.start();
        log("EmbabelA2AServer started successfully on port " + getMappedPort());
        
        // Store container reference in ExtensionContext
        storeInContext(context);
    }
    
    /**
     * Get the Docker socket path based on the operating system.
     * Windows uses a named pipe, while Linux/Mac use a Unix socket.
     */
    private String getDockerSocketPath() {
        String osName = System.getProperty("os.name").toLowerCase();
        
        if (osName.contains("win")) {
            // Windows with Docker Desktop uses a named pipe
            return "//./pipe/docker_engine";
        } else {
            // Linux and Mac use Unix socket
            return "/var/run/docker.sock";
        }
    }
    
    private Path downloadJarLocally() throws Exception {
        // Create temp directory for downloads
        Path tempDir = Files.createTempDirectory("embabel-test-");
        Path jarPath = tempDir.resolve(ARTIFACT_ID + ".jar");
        
        // Detect OS and use appropriate Maven executable
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String mavenCommand = isWindows ? "mvn.cmd" : "mvn";
        
        // Build Maven command
        List<String> command = new ArrayList<>();
        command.add(mavenCommand);
        command.add("dependency:copy");
        command.add(String.format("-Dartifact=%s:%s:%s:jar", GROUP_ID, ARTIFACT_ID, version));
        command.add("-DoutputDirectory=" + tempDir.toAbsolutePath());
        command.add("-Dmdep.stripVersion=true");
        
        // Add remote repositories if configured
        if (!mavenRepositoryUrls.isEmpty()) {
            StringBuilder reposList = new StringBuilder();
            int counter = 1;
            for (String repoUrl : mavenRepositoryUrls) {
                if (reposList.length() > 0) {
                    reposList.append(",");
                }
                reposList.append("repo").append(counter++)
                         .append("::default::")
                         .append(repoUrl);
            }
            command.add("-DremoteRepositories=" + reposList);
        }
        
        log("Running: " + String.join(" ", command));
        
        // Execute Maven command
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        
        // Add Maven to PATH for this process
        Map<String, String> env = pb.environment();
        String currentPath = env.get("PATH");
        String mavenBinPath = "c:\\tools\\apache\\maven\\apache-maven-3.9.6\\bin";
        
        if (currentPath != null && !currentPath.isEmpty()) {
            env.put("PATH", mavenBinPath + File.pathSeparator + currentPath);
        } else {
            env.put("PATH", mavenBinPath);
        }
        
        log("Added Maven to PATH: " + mavenBinPath);
        
        Process process = pb.start();
        
        // Capture output
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                if (enableLogging) {
                    System.out.println("[Maven] " + line);
                }
            }
        }
        
        int exitCode = process.waitFor();
        
        if (exitCode != 0) {
            throw new RuntimeException(
                "Maven download failed with exit code " + exitCode + "\n" +
                "Output:\n" + output
            );
        }
        
        if (!Files.exists(jarPath)) {
            throw new RuntimeException(
                "JAR file not found at: " + jarPath + "\n" +
                "Maven output:\n" + output
            );
        }
        
        log("Downloaded JAR to: " + jarPath);
        return jarPath;
    }
    
    private void buildDockerfile(DockerfileBuilder builder) {
        // Use eclipse-temurin:21-jre-alpine as base image
        builder.from("eclipse-temurin:21-jre-alpine");
        
        // Install Docker CLI and curl for downloading
        builder.run("apk add --no-cache docker-cli curl");
        
        // Create plugin directory
        builder.run("mkdir -p /usr/local/lib/docker/cli-plugins");
        
        // Download and install MCP plugin
        // Try multiple potential download URLs with fallback
        String mcpDownloadCmd = String.format(
            "curl -fsSL %s/%s/docker-mcp-linux-amd64 -o /usr/local/lib/docker/cli-plugins/docker-mcp || " +
            "curl -fsSL https://github.com/modelcontextprotocol/servers/releases/download/%s/docker-mcp-linux-amd64 -o /usr/local/lib/docker/cli-plugins/docker-mcp || " +
            "curl -fsSL https://github.com/anthropics/mcp-docker/releases/download/%s/docker-mcp-linux-amd64 -o /usr/local/lib/docker/cli-plugins/docker-mcp || " +
            "echo 'WARNING: Could not download MCP plugin from any source'",
            MCP_PLUGIN_BASE_URL, MCP_PLUGIN_VERSION,
            MCP_PLUGIN_VERSION,
            MCP_PLUGIN_VERSION
        );
        
        builder.run(mcpDownloadCmd);
        
        // Make it executable (ignore errors if file doesn't exist)
        builder.run("chmod +x /usr/local/lib/docker/cli-plugins/docker-mcp 2>/dev/null || true");
        
        // Verify installation (for logging purposes)
        builder.run("if [ -f /usr/local/lib/docker/cli-plugins/docker-mcp ]; then " +
                   "echo 'MCP plugin installed successfully'; " +
                   "else " +
                   "echo 'WARNING: MCP plugin not installed - docker mcp commands will not work'; " +
                   "fi");
        
        // Set working directory
        builder.workDir("/app");
        
        // Copy the JAR we downloaded
        builder.copy("app.jar", "/app/app.jar");
        
        // Expose the server port
        builder.expose(serverPort);
        
        // Build Java launch command
        List<String> javaCmd = buildJavaCommand();
        builder.entryPoint(javaCmd.toArray(new String[0]));
    }
    
    @Override
    public void afterAll(ExtensionContext context) {
        if (container != null && container.isRunning()) {
            log("Stopping EmbabelA2AServer container...");
            container.stop();
            log("EmbabelA2AServer stopped");
        }
        
        // Cleanup downloaded JAR
        if (downloadedJarPath != null) {
            try {
                Files.deleteIfExists(downloadedJarPath);
                Files.deleteIfExists(downloadedJarPath.getParent());
            } catch (IOException e) {
                // Ignore cleanup errors
            }
        }
    }
    
    public int getMappedPort() {
        return container.getMappedPort(serverPort);
    }
    
    public String getHost() {
        return container.getHost();
    }
    
    public String getBaseUrl() {
        return String.format("http://%s:%d", getHost(), getMappedPort());
    }
    
    public GenericContainer<?> getContainer() {
        return container;
    }
    
    private List<String> buildJavaCommand() {
        List<String> cmd = new ArrayList<>();
        cmd.add("java");
        cmd.addAll(jvmArgs);
        cmd.add("-jar");
        cmd.add("/app/app.jar");
        cmd.addAll(serverArgs);
        return cmd;
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
