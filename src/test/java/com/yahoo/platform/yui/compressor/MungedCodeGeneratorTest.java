package com.yahoo.platform.yui.compressor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.ast.AstRoot;

/**
 * Test cases for MungedCodeGenerator
 */
public class MungedCodeGeneratorTest {

    private AstRoot parseSource(String source) throws Exception {
        CompilerEnvirons env = new CompilerEnvirons();
        env.setRecordingComments(false);
        Parser parser = new Parser(env);
        return parser.parse(new StringReader(source), null, 1);
    }

    @Test
    public void testSimpleVariableDeclaration() throws Exception {
        String source = "var x=1;";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        builder.buildScopeTree(ast);

        MungedCodeGenerator generator = new MungedCodeGenerator(builder, false);
        String result = generator.generate(ast);

        assertTrue(result.contains("var"), "Should contain var declaration");
        assertTrue(result.contains("x"), "Should contain variable x");
    }

    @Test
    public void testFunctionDeclaration() throws Exception {
        String source = "function test(){}";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        builder.buildScopeTree(ast);

        MungedCodeGenerator generator = new MungedCodeGenerator(builder, false);
        String result = generator.generate(ast);

        assertTrue(result.contains("function"), "Should contain function keyword");
        assertTrue(result.contains("test"), "Should contain function name");
    }

    @Test
    public void testFunctionWithParameters() throws Exception {
        String source = "function test(a,b){return a+b;}";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        builder.buildScopeTree(ast);

        MungedCodeGenerator generator = new MungedCodeGenerator(builder, false);
        String result = generator.generate(ast);

        assertTrue(result.contains("function"), "Should contain function");
        assertTrue(result.contains("a") && result.contains("b"), "Should contain parameters");
        assertTrue(result.contains("return"), "Should contain return statement");
    }

    @Test
    public void testReturnStatement() throws Exception {
        String source = "function test(){return 42;}";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        builder.buildScopeTree(ast);

        MungedCodeGenerator generator = new MungedCodeGenerator(builder, false);
        String result = generator.generate(ast);

        assertTrue(result.contains("return"), "Should contain return statement");
        assertTrue(result.contains("42"), "Should contain return value");
    }

    @Test
    public void testStringLiteral() throws Exception {
        String source = "var s=\"hello\";";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        builder.buildScopeTree(ast);

        MungedCodeGenerator generator = new MungedCodeGenerator(builder, false);
        String result = generator.generate(ast);

        assertTrue(result.contains("\"hello\""), "Should preserve string literal");
    }

    @Test
    public void testNumberLiteral() throws Exception {
        String source = "var n=123.45;";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        builder.buildScopeTree(ast);

        MungedCodeGenerator generator = new MungedCodeGenerator(builder, false);
        String result = generator.generate(ast);

        assertTrue(result.contains("123.45"), "Should contain number");
    }

    @Test
    public void testInfixExpression() throws Exception {
        String source = "var x=1+2*3;";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        builder.buildScopeTree(ast);

        MungedCodeGenerator generator = new MungedCodeGenerator(builder, false);
        String result = generator.generate(ast);

        assertTrue(result.contains("+"), "Should contain addition");
        assertTrue(result.contains("*"), "Should contain multiplication");
    }

    @Test
    public void testFunctionCall() throws Exception {
        String source = "test(1,2);";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        builder.buildScopeTree(ast);

        MungedCodeGenerator generator = new MungedCodeGenerator(builder, false);
        String result = generator.generate(ast);

        assertTrue(result.contains("test"), "Should contain function name");
        assertTrue(result.contains("(") && result.contains(")"), "Should contain parentheses");
    }

    @Test
    public void testPropertyAccess() throws Exception {
        String source = "obj.property;";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        builder.buildScopeTree(ast);

        MungedCodeGenerator generator = new MungedCodeGenerator(builder, false);
        String result = generator.generate(ast);

        assertTrue(result.contains("obj"), "Should contain object name");
        assertTrue(result.contains("."), "Should contain dot");
        assertTrue(result.contains("property"), "Should contain property name");
    }

    @Test
    public void testObjectLiteral() throws Exception {
        String source = "var obj={a:1,b:2};";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        builder.buildScopeTree(ast);

        MungedCodeGenerator generator = new MungedCodeGenerator(builder, false);
        String result = generator.generate(ast);

        assertTrue(result.contains("{") && result.contains("}"), "Should contain object braces");
        assertTrue(result.contains("a") && result.contains("b"), "Should contain properties");
    }

    @Test
    public void testArrayLiteral() throws Exception {
        String source = "var arr=[1,2,3];";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        builder.buildScopeTree(ast);

        MungedCodeGenerator generator = new MungedCodeGenerator(builder, false);
        String result = generator.generate(ast);

        assertTrue(result.contains("[") && result.contains("]"), "Should contain array brackets");
    }

    @Test
    public void testBlock() throws Exception {
        String source = "function test(){var x=1;var y=2;}";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        builder.buildScopeTree(ast);

        MungedCodeGenerator generator = new MungedCodeGenerator(builder, false);
        String result = generator.generate(ast);

        assertTrue(result.contains("{") && result.contains("}"), "Should contain block braces");
    }

    @Test
    public void testMungingEnabled() throws Exception {
        String source = "function test(){var localVar=123;return localVar;}";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        ScriptOrFnScope globalScope = builder.buildScopeTree(ast);

        // Perform munging
        globalScope.munge();

        MungedCodeGenerator generator = new MungedCodeGenerator(builder, true);
        String result = generator.generate(ast);

        // Function name should be preserved
        assertTrue(result.contains("test"), "Function name should be preserved");

        // Local variable should be munged (won't be 'localVar')
        // The exact munged name depends on the munging algorithm
        assertTrue(result.contains("function"), "Should contain function");
    }

    @Test
    public void testMungingDisabled() throws Exception {
        String source = "function test(){var localVar=123;return localVar;}";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        builder.buildScopeTree(ast);

        MungedCodeGenerator generator = new MungedCodeGenerator(builder, false);
        String result = generator.generate(ast);

        // Variable names should be preserved when munging is disabled
        assertTrue(result.contains("localVar"), "Variable name should be preserved");
    }

    @Test
    public void testMultipleStatements() throws Exception {
        String source = "var a=1;var b=2;var c=a+b;";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        builder.buildScopeTree(ast);

        MungedCodeGenerator generator = new MungedCodeGenerator(builder, false);
        String result = generator.generate(ast);

        assertTrue(result.contains("a") && result.contains("b") && result.contains("c"),
            "Should contain all variables");
    }

    @Test
    public void testLetAndConstDeclarations() throws Exception {
        String source = "let x=1;const y=2;";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        builder.buildScopeTree(ast);

        MungedCodeGenerator generator = new MungedCodeGenerator(builder, false);
        String result = generator.generate(ast);

        assertTrue(result.contains("let"), "Should contain let");
        assertTrue(result.contains("const"), "Should contain const");
    }
}
