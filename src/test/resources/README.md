# YUI Compressor Test Files

This directory contains test files for the YUI Compressor.

## Adding a Test

To add a test:

1. Create a `filename.css` or `filename.js` file with the source code to be compressed
2. Create a `filename.css.min` or `filename.js.min` file containing the expected minified output

That's all! The test framework will automatically discover and run these tests.

## File Types

- **CSS files**: Test CSS compression features
- **JS files**: Test JavaScript compression features
- **`.FAIL` files**: Known failing tests that document issues

## Test Scripts

Tests can be run using:
- **Node.js**: `npm test` (runs Node.js wrapper tests)
- **Maven**: `mvn test` (runs Java unit tests)
- **Shell**: `./tests/suite.sh` (runs shell-based comparison tests)
