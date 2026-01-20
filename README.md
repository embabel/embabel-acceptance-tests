# 🤖 Embabel Agent Acceptance Tests

<img align="left" src="https://github.com/embabel/embabel-agent/blob/main/embabel-agent-api/images/315px-Meister_der_Weltenchronik_001.jpg?raw=true" width="180">


![Build](https://github.com/embabel/embabel-agent-examples/actions/workflows/maven.yml/badge.svg)

[//]: # ([![Quality Gate Status]&#40;https://sonarcloud.io/api/project_badges/measure?project=embabel_embabel-agent&metric=alert_status&token=d275d89d09961c114b8317a4796f84faf509691c&#41;]&#40;https://sonarcloud.io/summary/new_code?id=embabel_embabel-agent&#41;)

[//]: # ([![Bugs]&#40;https://sonarcloud.io/api/project_badges/measure?project=embabel_embabel-agent&metric=bugs&#41;]&#40;https://sonarcloud.io/summary/new_code?id=embabel_embabel-agent&#41;)

![Kotlin](https://img.shields.io/badge/kotlin-%237F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)
![Java](https://img.shields.io/badge/java-%23ED8B00.svg?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring](https://img.shields.io/badge/spring-%236DB33F.svg?style=for-the-badge&logo=spring&logoColor=white)
![Apache Tomcat](https://img.shields.io/badge/apache%20tomcat-%23F8DC75.svg?style=for-the-badge&logo=apache-tomcat&logoColor=black)
![Apache Maven](https://img.shields.io/badge/Apache%20Maven-C71A36?style=for-the-badge&logo=Apache%20Maven&logoColor=white)
![ChatGPT](https://img.shields.io/badge/chatGPT-74aa9c?style=for-the-badge&logo=openai&logoColor=white)
![Jinja](https://img.shields.io/badge/jinja-white.svg?style=for-the-badge&logo=jinja&logoColor=black)
![JSON](https://img.shields.io/badge/JSON-000?logo=json&logoColor=fff)
![GitHub Actions](https://img.shields.io/badge/github%20actions-%232671E5.svg?style=for-the-badge&logo=githubactions&logoColor=white)
![SonarQube](https://img.shields.io/badge/SonarQube-black?style=for-the-badge&logo=sonarqube&logoColor=4E9BCD)
![Docker](https://img.shields.io/badge/docker-%230db7ed.svg?style=for-the-badge&logo=docker&logoColor=white)
![IntelliJ IDEA](https://img.shields.io/badge/IntelliJIDEA-000000.svg?style=for-the-badge&logo=intellij-idea&logoColor=white)

&nbsp;&nbsp;&nbsp;&nbsp;

Acceptance tests for **EmbabelA2AServer** using **Testcontainers** and **JUnit 5**.

## Overview

This test suite automatically:
1. Downloads `example-agent-java` from **Embabel Artifactory** using Maven wrapper
2. Builds and launches it as a Docker container
3. Runs focused acceptance tests to verify A2A agent functionality

**Architecture**: All tests extend `AbstractA2ATest` for minimal, DDD-focused testing.

## ⚡ Quick Start

```bash
# 1. Ensure Docker is running
docker --version

# 2. Run all tests
./mvnw clean test

# 3. Run specific test
./mvnw test -Dtest=HoroscopeAgentTest
```

**No credentials needed!** Maven wrapper and Artifactory access is automatic.

## Prerequisites

- **Java 21** or later
- **Docker** installed and running
- **Maven wrapper** (included in project)

## Test Architecture

### DDD-Focused Design

All agent tests follow a **minimal, single-responsibility pattern**:

```
AbstractA2ATest (abstract)
    ↓
    ├── HoroscopeAgentTest
    ├── FactCheckerAgentTest
    ├── AdventureAgentTest
    └── ... (9 total agent tests)
```

Each test focuses on **one question**: *Does this agent do what it's supposed to do?*

### AbstractA2ATest

Abstract base class providing:
- ✅ Payload loading and caching
- ✅ HTTP request handling
- ✅ Domain-driven assertions
- ✅ Common infrastructure

**Subclasses only implement:**
```java
protected abstract String getPayloadPath();  // e.g., "payloads/horoscope-agent-request.json"
protected abstract String getRequestId();     // e.g., "req-001"
```

## Usage Example

### Minimal Agent Test

```java
@ExtendWith(EmbabelA2AServerExtension.class)
@DisplayName("Horoscope Agent Tests")
class HoroscopeAgentTest extends AbstractA2ATest {
    
    @Override
    protected String getPayloadPath() {
        return "payloads/horoscope-agent-request.json";
    }
    
    @Override
    protected String getRequestId() {
        return "req-001";
    }

    @Test
    @DisplayName("Should send horoscope message and receive AI-generated story")
    void shouldSendHoroscopeMessageAndReceiveStory(ServerInfo server) {
        log("Sending horoscope message to server at: " + server.getBaseUrl());
        
        Response response = sendA2ARequest(server, payload);
        
        assertSuccessfulA2AResponse(response);
        
        if (response.getStatusCode() == 200) {
            assertJsonRpcCompliance(response);
            assertContentPresent(response);
            log("✓ Horoscope message sent and response received");
        } else {
            log("✓ Message accepted for async processing");
        }
    }
}
```

**That's it! ~50 lines vs ~180 lines in traditional approach.**

### Available Assertion Methods (from AbstractA2ATest)

```java
// Domain-driven assertions
protected void assertSuccessfulA2AResponse(Response response)
protected void assertJsonRpcCompliance(Response response)
protected void assertContentPresent(Response response)

// Infrastructure methods
protected Response sendA2ARequest(ServerInfo server, String payload)
protected String loadJsonPayload(String resourcePath)
protected void log(String message)
```

### ServerInfo API

```java
@Test
void myTest(ServerInfo server) {
    String baseUrl = server.getBaseUrl();        // Full URL
    int port = server.getPort();                 // Mapped port
    GenericContainer<?> container = server.getContainer();  // Testcontainers instance
}
```

## Test Classes

### Agent Tests (9 total)

All extend `AbstractA2ATest`:

| Test Class | Agent Function | Status |
|-----------|----------------|--------|
| `HoroscopeAgentTest` | Generate horoscope stories | ✅ Active |
| `FactCheckerAgentTest` | Fact-check content | ✅ Active |
| `AdventureAgentTest` | Create adventure stories | ⏸️ Disabled |
| `BankSupportAgentTest` | Handle banking queries | ⏸️ Disabled |
| `BookWriterAgentTest` | Write books | ⏸️ Disabled |
| `InterestingFactsAgentTest` | Find interesting facts | ⏸️ Disabled |
| `MealPreparationAgentTest` | Prepare meals | ⏸️ Disabled |
| `StoryRevisionAgentTest` | Revise stories | ⏸️ Disabled |
| `WikiAgentTest` | Research on Wikipedia | ⏸️ Disabled |

### Basic Infrastructure Test

**`EmbabelA2AServerBasicTest`** - Verifies server startup
- Server starts successfully
- Health endpoint responds
- Server info accessible

## How It Works

```
Test Execution
    ↓
@ExtendWith(EmbabelA2AServerExtension.class)
    ↓
Maven Wrapper Downloads JAR
    ├─ From: https://repo.embabel.com/artifactory/libs-release
    └─ Or: https://repo.embabel.com/artifactory/libs-snapshot
    ↓
Builds Docker Image (eclipse-temurin:21-jre-alpine)
    ↓
Starts Container (8080 → Random Host Port)
    ↓
Tests Execute Against Running Server
    ↓
Container Stops on JVM Shutdown
```

## Configuration

### EmbabelA2AServerExtension

Default configuration (in extension class):

```java
private static final String DEFAULT_VERSION = "0.3.3-SNAPSHOT";
private static final int DEFAULT_SERVER_PORT = 8080;
private static final String DEFAULT_JVM_ARGS = "-Xmx512m";
private static final Duration DEFAULT_STARTUP_TIMEOUT = Duration.ofMinutes(2);
```

**To modify**: Edit `EmbabelA2AServerExtension.java`

### Singleton Container Pattern

The extension uses a **singleton pattern**:
- Container initialized **once** per test run
- Shared across **all test classes**
- Starts before first test
- Stops on JVM shutdown

**Benefits:**
- ⚡ Fast test execution (no repeated startup)
- 💰 Resource efficient
- 🔄 Consistent state

## Running Tests

```bash
# Run all tests
./mvnw test

# Run specific test
./mvnw test -Dtest=HoroscopeAgentTest

# Run only active tests
./mvnw test -Dtest=HoroscopeAgentTest,FactCheckerAgentTest

# Run with verbose output
./mvnw test -X

# Clean and test
./mvnw clean test
```

## Creating a New Agent Test

```java
@ExtendWith(EmbabelA2AServerExtension.class)
@DisplayName("My New Agent Tests")
class MyNewAgentTest extends AbstractA2ATest {

    @Override
    protected String getPayloadPath() {
        return "payloads/my-agent-request.json";
    }
    
    @Override
    protected String getRequestId() {
        return "req-010";
    }

    @Test
    @DisplayName("Should perform agent-specific action")
    void shouldPerformAction(ServerInfo server) {
        log("Testing new agent at: " + server.getBaseUrl());
        
        Response response = sendA2ARequest(server, payload);
        assertSuccessfulA2AResponse(response);
        
        if (response.getStatusCode() == 200) {
            assertJsonRpcCompliance(response);
            assertContentPresent(response);
            log("✓ Action completed successfully");
        }
    }
}
```

**That's all you need!** ~30-40 lines per test.

## Project Structure

```
agent-acceptance-tests/
├── src/
│   ├── main/java/
│   │   └── (none - test-only project)
│   └── test/
│       ├── java/com/embabel/acceptance/
│       │   ├── agent/
│       │   │   ├── AbstractA2ATest.java          ← Base class
│       │   │   ├── HoroscopeAgentTest.java        ← Agent tests
│       │   │   ├── FactCheckerAgentTest.java
│       │   │   └── ... (7 more agent tests)
│       │   ├── basic/
│       │   │   └── EmbabelA2AServerBasicTest.java ← Infrastructure test
│       │   └── jupiter/
│       │       └── EmbabelA2AServerExtension.java ← JUnit extension
│       └── resources/
│           └── payloads/
│               ├── horoscope-agent-request.json
│               ├── fact-checker-request.json
│               └── ... (7 more payloads)
├── pom.xml
├── mvnw                                           ← Maven wrapper
├── mvnw.cmd
└── README.md
```

## Troubleshooting

### Docker Not Running

```
Cannot connect to Docker daemon
```

**Solution:** Start Docker Desktop

### Maven Wrapper Not Found

```
Maven wrapper not found
```

**Solution:** The extension searches parent directories. Ensure `mvnw` exists in project root or parent.

### Container Startup Timeout

```
Container did not start within 2 minutes
```

**Solutions:**
1. Check Docker has adequate resources (memory, CPU)
2. Increase timeout in `EmbabelA2AServerExtension.java`
3. Check container logs for errors

### First Run is Slow

**This is normal!** First run:
- Downloads Maven dependencies (~100MB)
- Downloads example-agent-java artifact
- Builds Docker image

**Subsequent runs are much faster** (everything cached).

### Artifact Not Found

```
JAR not found at expected path
```

**Solutions:**
1. Verify version `0.3.3-SNAPSHOT` exists in Embabel Artifactory
2. Check network access to `repo.embabel.com`
3. Clear Maven local repository: `rm -rf ~/.m2/repository/com/embabel`

## Key Features

✅ **DDD-Focused** - Domain-driven design with clear concepts  
✅ **Minimal Tests** - ~50 lines per agent test  
✅ **Single Responsibility** - Each test answers one question  
✅ **Zero Duplication** - Common code in base class  
✅ **Automatic Download** - Maven wrapper handles dependencies  
✅ **Singleton Container** - Fast, resource-efficient  
✅ **Docker Isolation** - Clean test environment  
✅ **Random Ports** - No port conflicts  
✅ **CI/CD Ready** - Works in all environments  

## CI/CD Integration

### GitHub Actions

```yaml
name: Acceptance Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
      
      - name: Run tests
        run: ./mvnw clean test
```

### GitLab CI

```yaml
acceptance-tests:
  image: maven:3.9-eclipse-temurin-21
  services:
    - docker:dind
  variables:
    DOCKER_HOST: tcp://docker:2375
  script:
    - ./mvnw clean test
```

## Benefits of Refactored Approach

### Before (Traditional)
- ~180 lines per test class
- Duplicated infrastructure code
- Mixed domain and technical concerns
- Hard to maintain

### After (Refactored)
- ~50 lines per test class
- Shared infrastructure in base class
- Pure domain focus
- Easy to maintain and extend

**Code reduction: ~72%**

## Analogy

Think of the refactored tests like a **professional workshop**:
- **Base class** = Shared tool station with common tools
- **Agent tests** = Craftsmen focusing only on their specialty
- **No duplication** = Everyone uses the same high-quality tools

vs. Traditional approach where each craftsman carries their own complete toolkit.

---

**Ready to test?**

```bash
./mvnw clean test
```

That's it! 🚀
