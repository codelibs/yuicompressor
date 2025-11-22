# Rhino 1.8.0 Migration Plan

## Current Status

This project has been partially migrated from Rhino 1.7R2 to Rhino 1.8.0.

### Completed Steps

1. ✅ **Dependency Update**: Updated pom.xml to use `org.mozilla:rhino-all:1.8.0` instead of `rhino:js:1.7R2`
2. ✅ **Custom Rhino Files Retained**: The customized Rhino classes are kept for YUICompressor functionality

### Custom Rhino Files

The following files in `src/main/java/org/mozilla/javascript/` are customized for YUICompressor:

- **Token.java**: Adds two custom tokens for comment handling
  - `CONDCOMMENT` (160): JScript conditional comments (e.g., `/*@cc_on ... @*/`)
  - `KEEPCOMMENT` (161): Important comments to preserve (e.g., `/*! ... */`)

- **TokenStream.java**: Tokenizer that recognizes the custom comment types

- **Parser.java**: Parser that handles the custom tokens

- **Decompiler.java**: Decompiler that can reconstruct code with custom comments

These customizations are essential for YUICompressor's core functionality:
- Preserving license headers and important comments
- Handling IE conditional compilation comments
- Maintaining source code comments when needed

### Rhino 1.8.0 Changes (from 1.7R2)

Major changes between Rhino 1.7R2 (2011) and 1.8.0 (2024):

1. **Java Version**: Requires Java 11+ (we already use Java 11 ✅)
2. **Modularization**: Rhino is split into multiple modules
   - Using `rhino-all` for backward compatibility
3. **Default Language Level**: Now ES6 (was ES5)
4. **API Changes**: Many internal APIs have been updated
5. **New Features**: Promise, BigInt, Template Literals, Arrow Functions, etc.

### Next Steps

When network connectivity is available:

1. **Build Test**:
   ```bash
   mvn clean compile
   ```

2. **Check Compatibility**: Verify if custom Rhino classes are compatible with Rhino 1.8.0
   - Token values may have changed
   - Internal APIs may have been modified
   - Class structures may have been refactored

3. **Update Custom Files if Needed**:
   - Compare our Token.java constants with Rhino 1.8.0's Token class
   - Ensure CONDCOMMENT (160) and KEEPCOMMENT (161) don't conflict with new tokens
   - Update Parser, TokenStream, and Decompiler if internal APIs changed

4. **Run Tests**:
   ```bash
   mvn test
   ```

5. **Integration Testing**:
   - Test with various JavaScript files
   - Verify comment preservation works correctly
   - Check conditional comment handling

### Potential Issues

1. **Token ID Conflicts**: Rhino 1.8.0 may have added new tokens that conflict with our custom token IDs (160, 161)
   - **Solution**: Renumber our custom tokens to higher values if needed

2. **API Breaking Changes**: Internal classes we extend may have changed signatures
   - **Solution**: Update method signatures to match Rhino 1.8.0

3. **Class Shadowing**: Maven shade plugin configuration may need adjustment
   - Currently: Excludes meta-inf files only
   - May need: Additional filters for Rhino 1.8.0 modules

### Testing Checklist

Once build succeeds:

- [ ] Basic JavaScript minification
- [ ] Preserve `/*! ... */` comments
- [ ] Handle `/*@cc_on ... @*/` conditional comments
- [ ] Variable name obfuscation
- [ ] CSS compression
- [ ] Command-line interface
- [ ] Existing unit tests pass

### Rollback Plan

If migration fails:

1. Revert pom.xml to use `rhino:js:1.7R2`
2. Restore original maven-shade-plugin filters
3. Custom Rhino files remain unchanged

### References

- [Rhino 1.8.0 Release Notes](https://github.com/mozilla/rhino/releases/tag/Rhino1_8_0_Release)
- [Rhino Migration Guide](https://mozilla.github.io/rhino/)
- [YUICompressor Original Repository](https://github.com/yui/yuicompressor)
