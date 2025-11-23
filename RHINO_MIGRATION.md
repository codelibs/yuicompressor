# Rhino Migration Progress

## Current Status: 🔄 Testing Rhino 1.7R5

### Migration Timeline

| Attempt | Version | Release | Result | Main Issues |
|---------|---------|---------|--------|-------------|
| 1 | 1.8.0 | 2024 | ❌ Failed | 100+ errors: Classes deleted, major API rewrite |
| 2 | 1.7.14 | 2022 | ❌ Failed | 100+ errors: IRFactory methods private |
| 3 | 1.7.7.2 | 2017 | ❌ Failed | 100+ errors: Similar to 1.7.14 |
| 4 | 1.7R5 | 2015 | 🔄 Testing | Closest to 1.7R2 (2011) |

### Key Findings

#### Rhino 1.7.7.2 Specific Issues
- **stringToNumber()**: Does NOT need 4th parameter (reverted fix)
- **IRFactory methods**: Still private (same as 1.7.14)
- **Missing classes**: ScriptOrFnNode, FunctionNode, Node.Scope
- **Conclusion**: Too many breaking changes despite being older

### Applied Fixes

#### Fix 1: Hashtable Disambiguation
**Status**: ✅ Kept (works across all versions)

```java
// File: JavaScriptCompressor.java:534
private java.util.Hashtable indexedScopes = new java.util.Hashtable();
```

#### Fix 2: stringToNumber Parameter
**Status**: ⏪ Reverted for 1.7R5

```java
// File: TokenStream.java:494
// Rhino 1.7.14+: stringToNumber(str, 0, base, 10)
// Rhino 1.7R5:  stringToNumber(str, 0, base)
dval = ScriptRuntime.stringToNumber(numString, 0, base);
```

### Why Rhino 1.7R5?

**Reasoning**:
1. **Released 2015**: Only 4 years after our 1.7R2 base (2011)
2. **Pre-modularization**: Before major AST restructuring
3. **Stable API**: Likely maintains ScriptOrFnNode, FunctionNode
4. **Security updates**: Better than 1.7R2, less risky than staying old

**Alternative if 1.7R5 fails**:
- Try 1.7R4 (March 2015)
- Try 1.7R3 (June 2012) - closest to 1.7R2

### Breaking Changes by Version

#### Rhino 1.7R3 → 1.7R5 (Expected: Minor)
- Bug fixes and performance improvements
- Likely no major API changes

#### Rhino 1.7R5 → 1.7.7 (June 2015)
- Unknown changes
- May introduce some API modifications

#### Rhino 1.7.7 → 1.7.7.2 (April 2017)
- **IRFactory privatization begins**
- ScriptOrFnNode becomes problematic
- FunctionNode moves to AST package

#### Rhino 1.7.7.2 → 1.7.14 (Jan 2022)
- More IRFactory methods made private
- AST fully refactored
- Many helper methods removed

#### Rhino 1.7.14 → 1.8.0 (Jan 2024)
- **Complete rewrite**
- UintMap, ObjToIntMap, ObjArray deleted
- Modular architecture
- Java 11+ required

### Critical API Elements We Need

Our custom Rhino files depend on:

**Classes**:
- ✅ `ScriptOrFnNode` - Core to our parser
- ✅ `FunctionNode` - Used by Parser and Decompiler
- ✅ `Node.Scope` - Scoping mechanism
- ⚠️ `UintMap` - Used by Decompiler (deleted in 1.8.0)
- ⚠️ `ObjToIntMap` - Used by TokenStream (deleted in 1.8.0)
- ⚠️ `ObjArray` - Used by Parser (deleted in 1.8.0)

**IRFactory Methods** (must be accessible):
- `createScript()`
- `createFunction()`
- `createBlock()`, `createLeaf()`
- `createName()`, `addChildToBack()`
- `createIf()`, `createWhile()`, `createDoWhile()`
- `createSwitch()`, `addSwitchCase()`, `closeSwitch()`
- `createAssignment()`, `createBinary()`, `createUnary()`
- `createTryCatchFinally()`, `createCatch()`
- Many others...

**Status in Each Version**:
- 1.7R2: ✅ All present and public
- 1.7R5: 🔄 Testing (likely present)
- 1.7.7.2: ❌ Many private, some deleted
- 1.7.14: ❌ More private, more deleted
- 1.8.0: ❌ Most deleted or completely changed

### Custom Token Preservation

Our modifications add two critical tokens:

**CONDCOMMENT (160)**:
```javascript
/*@cc_on
  @if (@_win32)
    // Windows-specific code
  @end
@*/
```

**KEEPCOMMENT (161)**:
```javascript
/*!
 * jQuery v1.8.0
 * Copyright (c) 2012 jQuery Foundation
 */
```

**Requirements**:
- Token IDs must not conflict with Rhino's tokens
- Parser must recognize these tokens
- Decompiler must preserve them
- JavaScriptCompressor must handle them

### Next Steps

1. **If 1.7R5 Succeeds** ✅
   - Run full test suite
   - Verify comment preservation
   - Document any behavior differences
   - **Adopt as stable version**
   - Plan future 1.8.0 migration

2. **If 1.7R5 Fails** ⚠️
   - Try 1.7R4 → 1.7R3 → give up
   - If all fail: **Stay on 1.7R2**
   - Document security implications
   - Consider forking Rhino 1.7R2

3. **Long-term (1.8.0 Migration)** 🔮
   - Complete rewrite required
   - Use modern AST API
   - Reimplement comment hooks
   - **Estimated effort**: 1-2 weeks
   - **Prerequisites**: Thorough testing

### Lessons Learned

1. **API Stability**: Rhino internal APIs changed drastically post-1.7R5
2. **Privatization**: Mozilla intentionally hid internal APIs
3. **Modularization**: 1.8.0 is a different library
4. **Custom Modifications**: Made migration extremely difficult
5. **Version Jumping**: Can't skip major versions

### Recommended Strategy

**Short-term** (Now):
- Get 1.7R5 working with minimal changes
- Freeze at compatible version

**Medium-term** (6-12 months):
- Monitor Rhino security advisories
- Evaluate if 1.7R2→1.7R5 security gains worth it

**Long-term** (1-2 years):
- Plan complete rewrite for 1.8.0+
- Consider alternative JavaScript parsers
- Or maintain forked Rhino 1.7R5

### Build History

```
484bc98 - Upgrade Rhino dependency from 1.7R2 to 1.8.0
9142545 - Document Rhino 1.8.0 compatibility issues and try 1.7.14
3d5a6cd - Fix compatibility issues and try Rhino 1.7.7.2
<next>  - Revert stringToNumber fix, try Rhino 1.7R5
```

### References

- [Rhino 1.7R5 Release](https://github.com/mozilla/rhino/releases/tag/Rhino1_7R5_RELEASE)
- [Rhino 1.7.7.2 Release](https://github.com/mozilla/rhino/releases/tag/Rhino1_7_7_2_RELEASE)
- [Rhino 1.7.14 Release](https://github.com/mozilla/rhino/releases/tag/Rhino1_7_14_Release)
- [Rhino 1.8.0 Release](https://github.com/mozilla/rhino/releases/tag/Rhino1_8_0_Release)
- [YUICompressor Background](https://github.com/yui/yuicompressor)

## Conclusion

The migration is proving more difficult than expected. We're working backward through Rhino versions to find the newest one compatible with our heavily customized 1.7R2-based code. Rhino 1.7R5 (2015) is our best hope for a stable upgrade without major code changes.

If 1.7R5 fails, we may need to accept staying on 1.7R2 or undertake a complete rewrite for 1.8.0.
