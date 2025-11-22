# Rhino Migration Progress

## Current Status: 🔧 IN PROGRESS - Testing 1.7.7.2

### Migration Attempts

#### Attempt 1: Rhino 1.8.0 ❌
- **Result**: 100+ compilation errors
- **Issues**: Major API breaking changes
  - Deleted classes: `UintMap`, `ObjToIntMap`, `ObjArray`, `ScriptOrFnNode`, `Node.Scope`, `FunctionNode`
  - Many IRFactory methods removed or made private
  - Method signature changes across the board

#### Attempt 2: Rhino 1.7.14 ❌
- **Result**: 100+ compilation errors
- **Issues**: Still too many breaking changes
  - IRFactory methods made private: `createAssignment`, `createBinary`, `createIf`, etc.
  - AST restructuring: `Node.Scope` → `org.mozilla.javascript.ast.Scope`
  - Deleted methods: `createScript`, `createLeaf`, `addChildToBack`, etc.

#### Attempt 3: Rhino 1.7.7.2 (Current) 🔄
- **Status**: Testing in progress
- **Rationale**: Released in 2017, closer to our 1.7R2 base (2011)
- **Fixes Applied**:
  - ✅ Fixed Hashtable ambiguity (explicitly use `java.util.Hashtable`)
  - ✅ Fixed `stringToNumber()` call (added 4th parameter)
- **Pending**: Build verification when network available

### Fixes Applied

#### 1. Hashtable Namespace Conflict
**File**: `JavaScriptCompressor.java:534`

**Before**:
```java
private Hashtable indexedScopes = new Hashtable();
```

**After**:
```java
private java.util.Hashtable indexedScopes = new java.util.Hashtable();
```

**Reason**: Rhino 1.7.14+ has its own `org.mozilla.javascript.Hashtable` class causing ambiguity.

#### 2. ScriptRuntime.stringToNumber() Signature
**File**: `TokenStream.java:494`

**Before**:
```java
dval = ScriptRuntime.stringToNumber(numString, 0, base);
```

**After**:
```java
// Rhino 1.7.14+ requires additional parameter (radix)
dval = ScriptRuntime.stringToNumber(numString, 0, base, 10);
```

**Reason**: Method signature changed from 3 to 4-5 parameters in Rhino 1.7.14+.

### Version Comparison

| Version | Release | Status | Issues |
|---------|---------|--------|--------|
| 1.7R2   | 2011    | Current | Old, security concerns |
| 1.7R5   | 2015    | Untested | - |
| 1.7.7.2 | 2017    | Testing | Minor API changes |
| 1.7.14  | 2022    | Failed  | IRFactory methods private |
| 1.8.0   | 2024    | Failed  | Major API rewrite |

### Remaining Challenges

Even with Rhino 1.7.7.2, we may face:

1. **IRFactory API Changes**: Some methods may have changed signatures
2. **AST Node Structure**: May have evolved between 1.7R2 and 1.7.7.2
3. **ScriptOrFnNode**: May have been refactored
4. **Custom Token IDs**: Need to verify no conflicts (CONDCOMMENT=160, KEEPCOMMENT=161)

### Next Steps

1. **Complete Build with 1.7.7.2**
   - Verify compilation success
   - Fix any remaining minor errors
   - Run test suite

2. **If 1.7.7.2 Works**
   - Document any behavior changes
   - Test comment preservation (KEEPCOMMENT, CONDCOMMENT)
   - Run integration tests
   - Consider this as stable intermediate version

3. **If 1.7.7.2 Fails**
   - Try even older versions (1.7R5, 1.7R4, 1.7R3)
   - Consider creating compatibility shim layer
   - Document minimum viable Rhino version

4. **Future Migration to 1.8.0**
   - Would require complete rewrite of custom classes
   - Use modern AST API (org.mozilla.javascript.ast.*)
   - Reimplement comment preservation differently
   - Estimated effort: Several days of development

### Custom Functionality Preserved

Our custom Rhino modifications add two essential tokens:

- **CONDCOMMENT (160)**: IE conditional compilation comments
  ```javascript
  /*@cc_on @if (@_win32) ... @end @*/
  ```

- **KEEPCOMMENT (161)**: License/important comments
  ```javascript
  /*! Copyright (c) ... */
  ```

These are critical for:
- Preserving license headers during minification
- Supporting IE conditional compilation
- Maintaining required attribution comments

### Build History

```
484bc98 - Upgrade Rhino dependency from 1.7R2 to 1.8.0
9142545 - Document Rhino 1.8.0 compatibility issues and try 1.7.14
<next>  - Fix Hashtable/stringToNumber, try Rhino 1.7.7.2
```

### References

- [Rhino Releases](https://github.com/mozilla/rhino/releases)
- [Rhino 1.7.7.2 Release Notes](https://github.com/mozilla/rhino/releases/tag/Rhino1_7_7_2_RELEASE)
- [Rhino 1.8.0 Breaking Changes](https://github.com/mozilla/rhino/releases/tag/Rhino1_8_0_Release)
- [YUI Compressor Documentation](https://github.com/yui/yuicompressor)

## Conclusion

The migration from Rhino 1.7R2 to the latest version is more complex than anticipated due to significant internal API changes. We're taking a pragmatic approach:

1. **Short-term**: Find the newest compatible version (testing 1.7.7.2)
2. **Medium-term**: Use that version with minimal changes
3. **Long-term**: Plan complete rewrite for Rhino 1.8.0+ compatibility

This incremental approach minimizes risk while still improving security and compatibility.
