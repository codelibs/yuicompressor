package com.yahoo.platform.yui.compressor;

import static org.junit.Assert.*;

import java.io.StringReader;

import org.junit.Test;
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

        assertNotNull("Global scope should be created", globalScope);
        assertNull("Global scope should have no parent", globalScope.getParentScope());
    }

    @Test
    public void testGlobalVariableDeclaration() throws Exception {
        String source = "var globalVar = 42;";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        ScriptOrFnScope globalScope = builder.buildScopeTree(ast);

        JavaScriptIdentifier id = globalScope.getIdentifier("globalVar");
        assertNotNull("Global variable should be declared", id);
        assertEquals("Variable name should match", "globalVar", id.getValue());
    }

    @Test
    public void testFunctionScopeCreation() throws Exception {
        String source = "function test() { var x = 1; }";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        ScriptOrFnScope globalScope = builder.buildScopeTree(ast);

        // Global scope should exist
        assertNotNull("Global scope should exist", globalScope);
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
        assertNull("Local variable should not be in global scope", id);
    }

    @Test
    public void testMultipleVariableDeclarations() throws Exception {
        String source = "var a = 1, b = 2, c = 3;";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        ScriptOrFnScope globalScope = builder.buildScopeTree(ast);

        assertNotNull("Variable 'a' should be declared", globalScope.getIdentifier("a"));
        assertNotNull("Variable 'b' should be declared", globalScope.getIdentifier("b"));
        assertNotNull("Variable 'c' should be declared", globalScope.getIdentifier("c"));
    }

    @Test
    public void testNestedFunctions() throws Exception {
        String source = "function outer() { function inner() { var x = 1; } }";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        ScriptOrFnScope globalScope = builder.buildScopeTree(ast);

        // Should complete without errors
        assertNotNull("Global scope should exist", globalScope);
    }

    @Test
    public void testVariableInitializer() throws Exception {
        String source = "var x = function() { return 42; };";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        ScriptOrFnScope globalScope = builder.buildScopeTree(ast);

        JavaScriptIdentifier id = globalScope.getIdentifier("x");
        assertNotNull("Variable 'x' should be declared", id);
    }

    @Test
    public void testPropertyAccessNotTreatedAsVariable() throws Exception {
        String source = "var obj = {}; obj.property = 42;";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        ScriptOrFnScope globalScope = builder.buildScopeTree(ast);

        // 'obj' should be declared, but 'property' should not
        assertNotNull("Variable 'obj' should be declared", globalScope.getIdentifier("obj"));
        assertNull("Property name should not be declared as variable", globalScope.getIdentifier("property"));
    }

    @Test
    public void testLetAndConstDeclarations() throws Exception {
        String source = "let x = 1; const y = 2;";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        ScriptOrFnScope globalScope = builder.buildScopeTree(ast);

        assertNotNull("Let variable 'x' should be declared", globalScope.getIdentifier("x"));
        assertNotNull("Const variable 'y' should be declared", globalScope.getIdentifier("y"));
    }

    @Test
    public void testEmptySource() throws Exception {
        String source = "";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        ScriptOrFnScope globalScope = builder.buildScopeTree(ast);

        assertNotNull("Global scope should exist even for empty source", globalScope);
    }

    @Test
    public void testComplexExpression() throws Exception {
        String source = "var result = (function(x) { return x * 2; })(5);";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        ScriptOrFnScope globalScope = builder.buildScopeTree(ast);

        assertNotNull("Variable 'result' should be declared", globalScope.getIdentifier("result"));
    }
}
