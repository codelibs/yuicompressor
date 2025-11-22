# YUI Compressor Documentation

Welcome to the YUI Compressor documentation! This directory contains comprehensive guides and reference materials.

## Documentation Index

### Getting Started
- **[../README.md](../README.md)** - Main project README with quick start guide
- **[BUILDING.md](BUILDING.md)** - Build instructions and IDE setup
- **[TESTING.md](TESTING.md)** - Testing guide and test creation

### Reference
- **[../CHANGELOG.md](../CHANGELOG.md)** - Version history and changes
- **[TOOLS.md](TOOLS.md)** - Development and debugging tools

### API Documentation
- Generated Javadocs: Run `mvn javadoc:javadoc` and view at `target/site/apidocs/`

## Web Documentation

The official web documentation at http://yui.github.com/yuicompressor is powered by [Selleck](http://yui.github.com/selleck).

### Building Web Docs

Install Selleck:
```bash
npm -g install selleck
```

Clone the gh-pages branch:
```bash
git clone git://github.com/yui/yuicompressor.git
git clone git://github.com/yui/yuicompressor.git yuicompressor-pages
cd yuicompressor-pages
git fetch
git checkout -t origin/gh-pages
```

Generate documentation:
```bash
cd ../yuicompressor/docs/
selleck -o ../../yuicompressor-pages/
```

The documentation templates are:
- `index.mustache` - Main documentation page
- `css.mustache` - CSS-specific documentation
- `project.json` - Project metadata

## Contributing to Documentation

When adding new features or making changes:

1. Update relevant documentation files
2. Add examples where appropriate
3. Update CHANGELOG.md
4. Consider adding tests (see TESTING.md)
5. Update web documentation templates if needed

## Documentation Style

- Use clear, concise language
- Include code examples
- Add command-line examples with expected output
- Document edge cases and known issues
- Keep examples self-contained

## Additional Resources

- [YUI Compressor GitHub](https://github.com/yui/yuicompressor)
- [YUI Library](http://yuilibrary.com/)
- [Issue Tracker](https://github.com/yui/yuicompressor/issues)
