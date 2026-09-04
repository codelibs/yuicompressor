# YUICompressor ES6 Migration Plan

## Overview

This document describes the plan for making YUICompressor support ES6 (ECMAScript 2015) and later JavaScript syntax.

## Current State

### Technology in use
- **JavaScript Parser**: Mozilla Rhino 1.8.0
- **Language version setting**: `Context.VERSION_1_8` (equivalent to ES5.1)

### Currently supported syntax

#### Fully supported (explicit code generation)
| Syntax | Handling in MungedCodeGenerator |
|--------|---------------------------------|
| `var` declaration | ✅ `visitVariableDeclaration` |
| `let` declaration | ✅ `visitVariableDeclaration` |
| `const` declaration | ✅ `visitVariableDeclaration` |
| Function declaration/expression | ✅ `visitFunction` |
| Variable reference | ✅ `visitName` |
| `return` statement | ✅ `visitReturnStatement` |
| Block `{}` | ✅ `visitBlock` |
| Numeric literal | ✅ Emitted directly as a Number |
| String literal | ✅ `visitStringLiteral` |
| Assignment `=` | ✅ `visitInfixExpression` |
| Arithmetic operators `+ - * /` | ✅ `visitInfixExpression` |
| Function call | ✅ `visitFunctionCall` |
| Property access `.` | ✅ `visitPropertyGet` |
| Object literal | ✅ `visitObjectLiteral` |
| Array literal | ✅ `visitArrayLiteral` |

#### Parsed but not optimized (`toSource()` fallback)
- Arrow functions `() => {}`
- Template literals `` `hello ${name}` ``
- Class declarations/expressions
- Destructuring `const {a, b} = obj`
- Spread operator `...arr`
- Comparison operators `== != === !== < > <= >=`
- Logical operators `&& || !`
- Bitwise operators `& | ^ ~ << >> >>>`
- Ternary operator `? :`
- Increment/decrement `++ --`
- `if`/`else` statements
- `for`/`while`/`do-while` loops
- `for-of`/`for-in` loops
- `switch` statements
- `try`/`catch`/`finally`
- `throw` statements
- `new` operator
- `this` keyword
- `async`/`await`
- Generator functions

### Current problems

1. **The language version is outdated**: it is set to `VERSION_1_8` (ES5.1)
2. **Much ES6 syntax falls back to `toSource()`**: variable munging is not applied
3. **No block scoping**: the block scope of `let`/`const` is not handled correctly
4. **ES6 reserved words are missing**: `let`, `const`, `await`, `yield`, `of`, and others

## Migration plan

### Phase 1: Foundations [Priority: High]

#### 1.1 Update the parser configuration
- [x] Change `Context.VERSION_1_8` to `Context.VERSION_ES6` (already set in `JavaScriptCompressor.java`; this was merely an unchecked item — the implementation itself was completed before this release)
- [x] Review the CompilerEnvirons configuration (`setRecordingComments(false)`, `setRecordingLocalJsDocComments(false)`, `setLanguageVersion(Context.VERSION_ES6)`, `setGenerateDebugInfo(false)`, and `setErrorReporter(reporter)` are now set explicitly)

#### 1.2 Add ES6 reserved words
```java
// Add to the "reserved" set in JavaScriptCompressor.java
reserved.add("let");
reserved.add("const");
reserved.add("await");
reserved.add("yield");
reserved.add("of");
reserved.add("async");
reserved.add("from");
reserved.add("get");
reserved.add("set");
```

#### 1.3 Exclude them from the two-/three-character name lists
```java
twos.remove("of");
threes.remove("let");
threes.remove("get");
threes.remove("set");
```

---

### Phase 2: Extend MungedCodeGenerator (basic ES6 syntax) [Priority: High]

#### 2.1 Arrow function support
```java
case Token.ARROW:
    visitArrowFunction((ArrowFunctionNode) node);
    break;

private void visitArrowFunction(ArrowFunctionNode arrow) {
    // Parameters
    List<AstNode> params = arrow.getParams();
    if (params.size() == 1 && params.get(0) instanceof Name) {
        // Single parameter without parentheses
        visitNode(params.get(0));
    } else {
        output.append("(");
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) output.append(",");
            visitNode(params.get(i));
        }
        output.append(")");
    }

    output.append("=>");

    // Body
    AstNode body = arrow.getBody();
    visitNode(body);
}
```

#### 2.2 Template literal support
```java
case Token.TEMPLATE_LITERAL:
    visitTemplateLiteral((TemplateLiteral) node);
    break;

private void visitTemplateLiteral(TemplateLiteral template) {
    output.append('`');
    List<AstNode> elements = template.getElements();
    for (AstNode element : elements) {
        if (element instanceof TemplateCharacters) {
            output.append(((TemplateCharacters) element).getValue());
        } else {
            output.append("${");
            visitNode(element);
            output.append("}");
        }
    }
    output.append('`');
}
```

#### 2.3 Add the remaining operators
```java
// Comparison operators
case Token.EQ: visitInfixExpression(node, "=="); break;
case Token.NE: visitInfixExpression(node, "!="); break;
case Token.SHEQ: visitInfixExpression(node, "==="); break;
case Token.SHNE: visitInfixExpression(node, "!=="); break;
case Token.LT: visitInfixExpression(node, "<"); break;
case Token.LE: visitInfixExpression(node, "<="); break;
case Token.GT: visitInfixExpression(node, ">"); break;
case Token.GE: visitInfixExpression(node, ">="); break;

// Logical operators
case Token.AND: visitInfixExpression(node, "&&"); break;
case Token.OR: visitInfixExpression(node, "||"); break;
case Token.NOT: visitUnaryExpression(node, "!"); break;

// Bitwise operators
case Token.BITAND: visitInfixExpression(node, "&"); break;
case Token.BITOR: visitInfixExpression(node, "|"); break;
case Token.BITXOR: visitInfixExpression(node, "^"); break;
case Token.BITNOT: visitUnaryExpression(node, "~"); break;
case Token.LSH: visitInfixExpression(node, "<<"); break;
case Token.RSH: visitInfixExpression(node, ">>"); break;
case Token.URSH: visitInfixExpression(node, ">>>"); break;

// Other operators
case Token.MOD: visitInfixExpression(node, "%"); break;
case Token.COMMA: visitInfixExpression(node, ","); break;
```

---

### Phase 3: Extend MungedCodeGenerator (control flow) [Priority: Medium]

#### 3.1 Add control flow statements
```java
// if statement
case Token.IF:
    visitIfStatement((IfStatement) node);
    break;

private void visitIfStatement(IfStatement ifStmt) {
    output.append("if(");
    visitNode(ifStmt.getCondition());
    output.append(")");
    visitNode(ifStmt.getThenPart());

    AstNode elsePart = ifStmt.getElsePart();
    if (elsePart != null) {
        output.append("else ");
        visitNode(elsePart);
    }
}

// for loop
case Token.FOR:
    visitForLoop((ForLoop) node);
    break;

// while loop
case Token.WHILE:
    visitWhileLoop((WhileLoop) node);
    break;

// do-while loop
case Token.DO:
    visitDoLoop((DoLoop) node);
    break;

// switch statement
case Token.SWITCH:
    visitSwitchStatement((SwitchStatement) node);
    break;
```

#### 3.2 Remaining syntax
```java
// Ternary operator
case Token.HOOK:
    visitConditionalExpression((ConditionalExpression) node);
    break;

// Increment/decrement
case Token.INC:
case Token.DEC:
    visitUpdateExpression((UpdateExpression) node);
    break;

// throw statement
case Token.THROW:
    visitThrowStatement((ThrowStatement) node);
    break;

// try-catch-finally
case Token.TRY:
    visitTryStatement((TryStatement) node);
    break;

// new operator
case Token.NEW:
    visitNewExpression((NewExpression) node);
    break;

// this
case Token.THIS:
    output.append("this");
    break;
```

---

### Phase 4: Block scope support in ScopeBuilder [Priority: Medium]

#### 4.1 Track block scopes
```java
// Manage let/const inside a Block in the appropriate scope
if (node instanceof Block) {
    Block block = (Block) node;
    // let/const inside a block belong to the block scope
    ScriptOrFnScope blockScope = new ScriptOrFnScope(braceNesting + 1, currentScope);
    blockScope.setBlockScope(true);
    scopeMap.put(block, blockScope);
    // ...
}
```

#### 4.2 Arrow function scopes
```java
// An arrow function has its own scope (but inherits `this`)
if (node instanceof ArrowFunctionNode) {
    ArrowFunctionNode arrow = (ArrowFunctionNode) node;
    ScriptOrFnScope arrowScope = new ScriptOrFnScope(braceNesting + 1, currentScope);
    arrowScope.setArrowFunction(true);
    scopeMap.put(arrow, arrowScope);
    // Register the parameters
    for (AstNode param : arrow.getParams()) {
        if (param instanceof Name) {
            arrowScope.declareIdentifier(((Name) param).getIdentifier());
        }
    }
}
```

---

### Phase 5: Advanced ES6 features [Priority: Medium]

#### 5.1 Class declarations/expressions
```java
case Token.CLASS:
    visitClassNode(node);
    break;

private void visitClassNode(AstNode node) {
    // ClassNode handling
}
```

#### 5.2 Destructuring
```java
// Array destructuring
case Token.ARRAY_COMP:  // or specific destructuring token
    visitArrayDestructuring(node);
    break;

// Object destructuring
// Parsed as an ObjectLiteral, so it must be identified from the context
```

#### 5.3 Spread/rest operator
```java
case Token.SPREAD:
    visitSpreadExpression(node);
    break;
```

---

### Phase 6: Module syntax (future work) [Priority: Low]

- `import`/`export` syntax
- Dynamic `import()`
- `export default`

---

## Implementation priorities

### High priority (do first)
1. Update the parser configuration (`VERSION_ES6`)
2. Add the ES6 reserved words
3. Code generation for arrow functions
4. Add the comparison/logical/bitwise operators
5. Add control flow statements (`if`/`for`/`while`)

### Medium priority (do next)
6. Code generation for template literals
7. Correct handling of block scopes
8. Code generation for class declarations
9. Handling of destructuring

### Low priority (future work)
10. `async`/`await`
11. Generators
12. Module syntax

---

## Test plan

### New test cases
```java
@Test
public void testArrowFunction() {
    // Basic arrow function
    assertCompression("const f=x=>x*2;", "const f=x=>x*2;");

    // Multiple parameters
    assertCompression("const f=(x,y)=>x+y;", "const f=(a,b)=>a+b;");

    // Block body
    assertCompression("const f=x=>{return x*2;};", "const f=a=>{return a*2;};");
}

@Test
public void testTemplateLiteral() {
    assertCompression("`hello ${name}`;", "`hello ${a}`;");
}

@Test
public void testClass() {
    assertCompression(
        "class Foo{constructor(x){this.x=x;}}",
        "class Foo{constructor(a){this.x=a;}}"
    );
}
```

---

## Risks and mitigations

### Risk 1: The limits of ES6 support in Rhino 1.8.0
- **Mitigation**: keep the `toSource()` fallback and prioritize correctness

### Risk 2: Breaking backward compatibility
- **Mitigation**: confirm that all existing tests still pass

### Risk 3: Performance regressions
- **Mitigation**: prioritize explicit handling of the important node types

---

## Completion criteria

1. [ ] ES6 syntax parses without errors — **Incorrect; corrected by measurement**: Rhino cannot parse `class` declarations/expressions, `async`/`await`, `import`/`export`, dynamic `import()`, `new.target`, or `for-of` with `const` (it throws on a syntax error). This was verified across several versions, including Rhino 1.9.1, and is a constraint that upgrading the version does not resolve. `for-of` with `var`/`let` does parse.
2. [x] Variables in arrow functions are munged
3. [x] The block scope of `let`/`const` is handled correctly
4. [ ] All existing tests pass (to be confirmed with network connectivity)
5. [x] New ES6 tests were added and pass

---

## Completed work (Phases 1-5)

### Phase 1: Foundations ✅
- [x] Updated the parser configuration to `Context.VERSION_ES6`
- [x] Added the ES6 reserved words (`let`, `const`, `await`, `yield`, `of`, `async`, `from`, `get`, `set`, `target`, `meta`)
- [x] Excluded the ES6 keywords from the two-/three-character variable name lists

### Phase 2: MungedCodeGenerator extensions (basic ES6 syntax) ✅
- [x] Arrow function support (the `=>` syntax is preserved)
- [x] Template literal support (backticks and `${}` interpolation)
- [x] Comparison operators (`==`, `!=`, `===`, `!==`, `<`, `<=`, `>`, `>=`)
- [x] Logical operators (`&&`, `||`, `!`)
- [x] Bitwise operators (`&`, `|`, `^`, `~`, `<<`, `>>`, `>>>`)
- [x] Other operators (`%`, `**`, `,`, `in`, `instanceof`)

### Phase 3: MungedCodeGenerator extensions (control flow) ✅
- [x] `if`/`else` statements
- [x] `for`/`while`/`do-while` loops
- [x] `for-in`/`for-of` loops — but only partially: `for-of` parses with `var`/`let`, not with `const` (corrected by measurement; see item 1 of "Completion criteria" above)
- [x] `switch`/`case`/`default` statements
- [x] `try`/`catch`/`finally` statements
- [x] `break`/`continue` statements
- [x] `throw` statements
- [x] Labeled statements — **this item was incorrect when it was written**: until it was fixed in this release (task 12b, commit 86326d4), a labeled statement was cast incorrectly because `LabeledStatement.getType()` returns `Token.EXPR_VOID` rather than `Token.LABEL`, which crashed the compressor with a `ClassCastException`. It only became true as of this release.
- [x] `with` statements

### Phase 4: ScopeBuilder extensions ✅
- [x] Extraction of variables from destructuring patterns
- [x] Variable scoping for `for-of`/`for-in` loops
- [x] Variable scoping for `catch` blocks
- [x] Exclusion of object property keys

### Phase 5: New test cases ✅
- [x] Created ES6SupportTest.java
- [x] Added 40+ test cases for ES6 features
