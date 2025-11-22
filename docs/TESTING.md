# Testing YUI Compressor

This guide explains how to run and create tests for YUI Compressor.

## Running Tests

### Maven Tests (Java)

Run all Java unit tests:

```bash
mvn test
```

Run specific test class:

```bash
mvn test -Dtest=ClassName
```

### Node.js Tests

Run the Node.js wrapper tests:

```bash
npm test
```

This runs the test suite defined in `tests/node/tests.js` using YUITest.

### Shell-based Tests

Run the shell test suite (if available):

```bash
cd src/test/resources
./suite.sh
```

## Test Structure

### Java Tests

Located in `src/test/java/`:
- Unit tests for Java compressor classes
- Use JUnit framework
- Test both JavaScript and CSS compression

### Node.js Tests

Located in `tests/node/`:
- `tests.js` - Main test suite
- `files/` - Large test files (e.g., yui.js)
- Tests the Node.js wrapper functionality

### Test Resources

Located in `src/test/resources/`:
- Pairs of files: `filename.ext` and `filename.ext.min`
- Source file contains the original code
- `.min` file contains expected compressed output
- Automatically discovered by test framework

## Creating Tests

### Adding a Java Test

1. Create a test class in `src/test/java/`
2. Extend `TestCase` or use JUnit annotations
3. Write test methods
4. Run with `mvn test`

Example:

```java
import org.junit.Test;
import static org.junit.Assert.*;

public class MyTest {
    @Test
    public void testCompression() {
        // Test code here
        assertEquals(expected, actual);
    }
}
```

### Adding a Resource-based Test

1. Create a source file: `src/test/resources/mytest.js` or `mytest.css`
2. Create expected output: `src/test/resources/mytest.js.min` or `mytest.css.min`
3. The test framework will automatically discover and run it

Example CSS test:

**mytest.css**:
```css
body {
    background-color: #ffffff;
    margin: 0px;
}
```

**mytest.css.min**:
```css
body{background-color:#fff;margin:0}
```

### Adding a Node.js Test

Edit `tests/node/tests.js` and add a new test case:

```javascript
suite.add(new YUITest.TestCase({
    name: 'My Test',
    'test: my feature': function() {
        var test = this;
        compressor.compress(input, options, function(err, output) {
            test.resume(function() {
                Assert.isNull(err);
                Assert.areEqual(expected, output);
            });
        });
        test.wait();
    }
}));
```

## Test Files

### Known Failing Tests

Files with `.FAIL` extension are known failing tests that document issues:
- `hsla-issue81.css.FAIL`
- `issue71.js.FAIL`
- `issue172.css.FAIL`
- `rgb-issue81.css.FAIL`

These tests:
- Document known bugs
- Prevent regressions
- Show expected behavior once fixed

### Test Categories

Tests are organized by feature:

**CSS Tests**:
- Color compression (`color*.css`)
- Data URLs (`dataurl*.css`)
- Media queries (`media*.css`)
- IE hacks (`ie*.css`)
- Pseudo-classes (`pseudo*.css`)

**JavaScript Tests**:
- Variable munging (`_munge.js`)
- String combination (`_string_combo*.js`)
- Syntax errors (`_syntax_error.js`)
- Promise syntax (`promise-catch-finally*.js`)
- jQuery compatibility (`jquery-1.6.4.js`)

## Debugging Tests

### Maven Test Debugging

Run tests with debugging output:

```bash
mvn test -X
```

Run single test with debugging:

```bash
mvn test -Dtest=ClassName#methodName -X
```

### Node.js Test Debugging

Run with Node.js debugger:

```bash
node --inspect-brk node_modules/.bin/yuitest tests/node/tests.js
```

Then open Chrome DevTools at `chrome://inspect`

### Manual Testing

Test a file manually with the CLI:

```bash
# JavaScript
java -jar target/yuicompressor-*.jar input.js -o output.js

# CSS
java -jar target/yuicompressor-*.jar --type css input.css -o output.css

# Node.js wrapper
node nodejs/cli.js input.js -o output.js
```

## Test Coverage

To generate test coverage reports:

```bash
mvn clean test jacoco:report
```

View the report at `target/site/jacoco/index.html`

## Continuous Testing

### Watch Mode

For continuous testing during development, use tools like:

```bash
# Maven (with spring-boot-devtools or similar)
mvn test -Dspring.devtools.restart.enabled=true

# Node.js (with nodemon)
npx nodemon --exec npm test
```

### Pre-commit Hooks

Set up Git hooks to run tests before committing:

```bash
# .git/hooks/pre-commit
#!/bin/bash
mvn test
npm test
```

## Performance Testing

### Benchmarking Compression

Time compression of large files:

```bash
time java -jar target/yuicompressor-*.jar large-file.js -o /dev/null
```

### Memory Profiling

Run with Java profiling:

```bash
java -Xmx512m -verbose:gc -jar target/yuicompressor-*.jar input.js
```

## Reporting Test Failures

When reporting test failures, include:
1. Input file content
2. Expected output
3. Actual output
4. Command used
5. Java/Node.js version
6. Operating system

Example:

```
Input:
var x = 1 + 1;

Expected:
var x=2;

Actual:
var x=1+1;

Command:
java -jar yuicompressor.jar --type js test.js

Environment:
- Java: 11.0.12
- YUI Compressor: 2.4.10
- OS: Ubuntu 20.04
```
