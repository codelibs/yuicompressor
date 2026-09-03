package com.yahoo.platform.yui.compressor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.ast.AstRoot;

/**
 * Test cases for ScopeBuilder
 */
public class ScopeBuilderTest {

    private AstRoot parseSource(String source) throws Exception {
        CompilerEnvirons env = new CompilerEnvirons();
        env.setRecordingComments(false);
        Parser parser = new Parser(env);
        return parser.parse(new StringReader(source), null, 1);
    }

    @Test
    public void testGlobalScopeCreation() throws Exception {
        String source = "var x = 1;";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        ScriptOrFnScope globalScope = builder.buildScopeTree(ast);

        assertNotNull(globalScope, "Global scope should be created");
        assertNull(globalScope.getParentScope(), "Global scope should have no parent");
    }

    @Test
    public void testGlobalVariableDeclaration() throws Exception {
        String source = "var globalVar = 42;";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        ScriptOrFnScope globalScope = builder.buildScopeTree(ast);

        JavaScriptIdentifier id = globalScope.getIdentifier("globalVar");
        assertNotNull(id, "Global variable should be declared");
        assertEquals("globalVar", id.getValue(), "Variable name should match");
    }

    @Test
    public void testFunctionScopeCreation() throws Exception {
        String source = "function test() { var x = 1; }";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        ScriptOrFnScope globalScope = builder.buildScopeTree(ast);

        // Global scope should exist
        assertNotNull(globalScope, "Global scope should exist");
    }

    @Test
    public void testFunctionParameterDeclaration() throws Exception {
        String source = "function test(param1, param2) { return param1 + param2; }";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        builder.buildScopeTree(ast);

        // We can't directly access the function scope, but we can verify no exceptions are thrown
        // The actual parameter munging is tested in integration tests
    }

    @Test
    public void testLocalVariableDeclaration() throws Exception {
        String source = "function test() { var localVar = 123; return localVar; }";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        ScriptOrFnScope globalScope = builder.buildScopeTree(ast);

        // Local variable should not be in global scope
        JavaScriptIdentifier id = globalScope.getIdentifier("localVar");
        assertNull(id, "Local variable should not be in global scope");
    }

    @Test
    public void testMultipleVariableDeclarations() throws Exception {
        String source = "var a = 1, b = 2, c = 3;";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        ScriptOrFnScope globalScope = builder.buildScopeTree(ast);

        assertNotNull(globalScope.getIdentifier("a"), "Variable 'a' should be declared");
        assertNotNull(globalScope.getIdentifier("b"), "Variable 'b' should be declared");
        assertNotNull(globalScope.getIdentifier("c"), "Variable 'c' should be declared");
    }

    @Test
    public void testNestedFunctions() throws Exception {
        String source = "function outer() { function inner() { var x = 1; } }";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        ScriptOrFnScope globalScope = builder.buildScopeTree(ast);

        // Should complete without errors
        assertNotNull(globalScope, "Global scope should exist");
    }

    @Test
    public void testVariableInitializer() throws Exception {
        String source = "var x = function() { return 42; };";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        ScriptOrFnScope globalScope = builder.buildScopeTree(ast);

        JavaScriptIdentifier id = globalScope.getIdentifier("x");
        assertNotNull(id, "Variable 'x' should be declared");
    }

    @Test
    public void testPropertyAccessNotTreatedAsVariable() throws Exception {
        String source = "var obj = {}; obj.property = 42;";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        ScriptOrFnScope globalScope = builder.buildScopeTree(ast);

        // 'obj' should be declared, but 'property' should not
        assertNotNull(globalScope.getIdentifier("obj"), "Variable 'obj' should be declared");
        assertNull(globalScope.getIdentifier("property"), "Property name should not be declared as variable");
    }

    @Test
    public void testLetAndConstDeclarations() throws Exception {
        String source = "let x = 1; const y = 2;";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        ScriptOrFnScope globalScope = builder.buildScopeTree(ast);

        assertNotNull(globalScope.getIdentifier("x"), "Let variable 'x' should be declared");
        assertNotNull(globalScope.getIdentifier("y"), "Const variable 'y' should be declared");
    }

    @Test
    public void testEmptySource() throws Exception {
        String source = "";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        ScriptOrFnScope globalScope = builder.buildScopeTree(ast);

        assertNotNull(globalScope, "Global scope should exist even for empty source");
    }

    @Test
    public void testComplexExpression() throws Exception {
        String source = "var result = (function(x) { return x * 2; })(5);";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        ScriptOrFnScope globalScope = builder.buildScopeTree(ast);

        assertNotNull(globalScope.getIdentifier("result"), "Variable 'result' should be declared");
    }
}
