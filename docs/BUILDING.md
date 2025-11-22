# Building YUI Compressor

This guide explains how to build YUI Compressor from source.

## Prerequisites

- **Java**: JDK 11 or later
- **Maven**: 3.6.0 or later
- **Node.js**: 20.0.0 or later (for Node.js wrapper)

## Building with Maven

### Standard Build

Build the project and create the JAR file:

```bash
mvn clean package
```

This will:
- Compile all Java sources
- Run unit tests
- Create `target/yuicompressor-<version>.jar` (shaded JAR with all dependencies)

### Skip Tests

To build without running tests:

```bash
mvn clean package -DskipTests
```

### Build for Release

To build release artifacts (sources, javadoc, and signed JARs):

```bash
mvn clean package -P release
```

This requires:
- GPG configured for signing
- Proper credentials for Maven Central deployment

## Building the Node.js Package

The Node.js wrapper requires the JAR file to be built first:

```bash
# Build Java JAR
mvn clean package

# Install Node.js dependencies
npm install

# Run Node.js tests
npm test
```

## Build Output

After a successful build, you'll find:

- `target/yuicompressor-<version>.jar` - Main executable JAR
- `target/original-yuicompressor-<version>.jar` - JAR without dependencies
- `target/classes/` - Compiled Java classes
- `target/test-classes/` - Compiled test classes

## Project Structure

```
yuicompressor/
├── src/
│   ├── main/java/          # Java source code
│   └── test/
│       ├── java/           # Java test code
│       └── resources/      # Test resources (CSS/JS test files)
├── nodejs/                 # Node.js wrapper
├── tests/
│   └── node/              # Node.js tests
├── docs/                  # Documentation
├── pom.xml                # Maven configuration
└── package.json           # Node.js configuration
```

## Troubleshooting

### Java Version Issues

Ensure you're using Java 11 or later:

```bash
java -version
```

### Maven Not Found

Install Maven or use the Maven wrapper if available.

### Build Failures

1. Clean the project first: `mvn clean`
2. Check your Java version
3. Ensure all dependencies can be downloaded
4. Check for firewall/proxy issues blocking Maven Central

## IDE Setup

### IntelliJ IDEA

1. File → Open → Select `pom.xml`
2. Import as Maven project
3. Set Project SDK to Java 11+

### Eclipse

1. File → Import → Maven → Existing Maven Projects
2. Select the project root directory
3. Configure Java 11+ in project settings

### VS Code

1. Install "Java Extension Pack"
2. Open the project folder
3. Maven should be auto-detected

## Continuous Integration

The project includes a `.travis.yml` configuration for Travis CI. You can adapt this for other CI systems like GitHub Actions, Jenkins, or GitLab CI.
