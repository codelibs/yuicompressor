# Rhino 1.8.0 Migration Plan

## Current Status: ⚠️ COMPILATION ERRORS

The migration from Rhino 1.7R2 to 1.8.0 has revealed significant API incompatibilities.

### Completed Steps

1. ✅ **Dependency Update**: Updated pom.xml to use `org.mozilla:rhino-all:1.8.0`
2. ✅ **Custom Rhino Files Retained**: Kept customized classes for YUICompressor functionality
3. ⚠️ **Build Attempted**: Compilation failed with 100+ errors

## Compilation Errors Analysis

### 1. Deleted/Removed Classes

The following internal classes were removed in Rhino 1.8.0 (as mentioned in release notes):

- ❌ `UintMap` - Replaced with standard Java collections
- ❌ `ObjToIntMap` - Replaced with standard Java collections
- ❌ `ObjArray` - Replaced with `java.util.ArrayList`
- ❌ `ScriptOrFnNode` - Merged into AST node hierarchy
- ❌ `Node.Scope` - Changed to `org.mozilla.javascript.ast.Scope`
- ❌ `FunctionNode` - Changed to `org.mozilla.javascript.ast.FunctionNode`

**Used in our custom files:**
- `Decompiler.java`: Uses `UintMap`, `FunctionNode`
- `TokenStream.java`: Uses `ObjToIntMap`
- `Parser.java`: Uses `ScriptOrFnNode`, `Node.Scope`, `ObjArray`, `FunctionNode`

### 2. API Method Signature Changes

Many IRFactory methods have changed signatures or been removed:

- `IRFactory` constructor now requires different parameters
- `createScript()` → Removed or changed
- `createName(String)` → Changed signature
- `createBlock(int)` → Changed signature
- `createLoopNode()` → Added parameters
- `createSwitch()` → Removed
- `createWhile()`, `createDoWhile()` → Changed signatures
- `createForIn()` → Added parameters (now requires AST nodes)
- `createCatch()` → Changed parameters
- `createTryCatchFinally()` → Added parameters
- `createThrow()`, `createBreak()`, `createContinue()` → Changed/removed
- `stringToNumber()` → Added parameters

### 3. Access Modifier Changes

Several methods are now private (were package-private or public):

- ❌ `createAssignment()` → private
- ❌ `addSwitchCase()` → private
- ❌ `closeSwitch()` → private
- ❌ `createBinary()` → private

### 4. Namespace Conflicts

- `java.util.Hashtable` vs `org.mozilla.javascript.Hashtable` ambiguity

## Migration Strategy Options

### Option 1: Complete Rewrite (Recommended for Long-term)

**Pros:**
- Future-proof
- Uses modern Rhino APIs
- Better maintainability

**Cons:**
- Significant development effort
- Need to reimplement CONDCOMMENT/KEEPCOMMENT functionality

**Approach:**
1. Remove custom Rhino classes entirely
2. Use Rhino 1.8.0's AST API directly
3. Implement comment preservation using TokenStream hooks or post-processing

### Option 2: Incremental Migration (Recommended for Now)

**Pros:**
- Maintains existing functionality
- Step-by-step approach
- Can be done incrementally

**Cons:**
- Still significant effort
- May need to maintain custom classes longer

**Approach:**
1. Replace deleted classes with Java standard equivalents
2. Update method signatures to match Rhino 1.8.0
3. Adapt to new AST structure
4. Maintain CONDCOMMENT/KEEPCOMMENT tokens

### Option 3: Use Intermediate Version

Try Rhino 1.7.14 (2022) first, which may have better compatibility:

**Pros:**
- Smaller API changes
- Still gets security updates and bug fixes
- Easier migration path

**Cons:**
- Not the latest version
- Will eventually need to migrate to 1.8.0

## Recommended Action Plan

### Phase 1: Try Intermediate Version (1.7.14)

1. Update pom.xml to use Rhino 1.7.14
2. Attempt build
3. If successful, run tests
4. Document any remaining issues

### Phase 2: If 1.7.14 works, plan for 1.8.0

1. Identify specific incompatibilities with 1.8.0
2. Plan incremental updates to custom files
3. Create compatibility layer if needed

### Phase 3: If 1.7.14 doesn't work, proceed with full migration

1. **Replace deleted classes:**
   ```java
   // UintMap → HashMap<Integer, Object>
   // ObjToIntMap → HashMap<Object, Integer>
   // ObjArray → ArrayList<Object>
   ```

2. **Update Parser.java:**
   - Use `org.mozilla.javascript.ast.*` classes
   - Update IRFactory usage
   - Fix method signatures

3. **Update TokenStream.java:**
   - Replace ObjToIntMap with HashMap
   - Update stringToNumber calls

4. **Update Decompiler.java:**
   - Replace UintMap with HashMap
   - Update FunctionNode references

5. **Update JavaScriptCompressor.java:**
   - Fix Hashtable ambiguity (use `java.util.Hashtable` explicitly)

## Custom Token Support

The customized Rhino files add two essential tokens for comment handling:
- **CONDCOMMENT (160)**: JScript conditional comments `/*@cc_on ... @*/`
- **KEEPCOMMENT (161)**: Important comments to preserve `/*! ... */`

**Critical**: Must ensure these token IDs don't conflict with new Rhino 1.8.0 tokens.

## Next Steps

1. Try Rhino 1.7.14 first (pragmatic approach)
2. If that fails, begin systematic class replacement
3. Update RHINO_MIGRATION.md with progress
4. Commit incremental fixes

## References

- [Rhino 1.8.0 Release Notes](https://github.com/mozilla/rhino/releases/tag/Rhino1_8_0_Release)
- [Rhino 1.7.14 Release Notes](https://github.com/mozilla/rhino/releases/tag/Rhino1_7_14_Release)
- [Rhino Migration Guide](https://mozilla.github.io/rhino/)
- [Remove obsolete classes PR #1562](https://github.com/mozilla/rhino/pull/1562)
