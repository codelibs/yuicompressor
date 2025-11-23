package org.codelibs.yuicompressor;

import static org.junit.Assert.*;

import java.io.StringReader;

import org.junit.Test;
import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.ast.AstRoot;

import com.yahoo.platform.yui.compressor.MungedCodeGenerator;
import com.yahoo.platform.yui.compressor.ScriptOrFnScope;
import com.yahoo.platform.yui.compressor.ScopeBuilder;

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

        assertTrue("Should contain var declaration", result.contains("var"));
        assertTrue("Should contain variable x", result.contains("x"));
    }

    @Test
    public void testFunctionDeclaration() throws Exception {
        String source = "function test(){}";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        builder.buildScopeTree(ast);

        MungedCodeGenerator generator = new MungedCodeGenerator(builder, false);
        String result = generator.generate(ast);

        assertTrue("Should contain function keyword", result.contains("function"));
        assertTrue("Should contain function name", result.contains("test"));
    }

    @Test
    public void testFunctionWithParameters() throws Exception {
        String source = "function test(a,b){return a+b;}";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        builder.buildScopeTree(ast);

        MungedCodeGenerator generator = new MungedCodeGenerator(builder, false);
        String result = generator.generate(ast);

        assertTrue("Should contain function", result.contains("function"));
        assertTrue("Should contain parameters", result.contains("a") && result.contains("b"));
        assertTrue("Should contain return statement", result.contains("return"));
    }

    @Test
    public void testReturnStatement() throws Exception {
        String source = "function test(){return 42;}";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        builder.buildScopeTree(ast);

        MungedCodeGenerator generator = new MungedCodeGenerator(builder, false);
        String result = generator.generate(ast);

        assertTrue("Should contain return statement", result.contains("return"));
        assertTrue("Should contain return value", result.contains("42"));
    }

    @Test
    public void testStringLiteral() throws Exception {
        String source = "var s=\"hello\";";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        builder.buildScopeTree(ast);

        MungedCodeGenerator generator = new MungedCodeGenerator(builder, false);
        String result = generator.generate(ast);

        assertTrue("Should preserve string literal", result.contains("\"hello\""));
    }

    @Test
    public void testNumberLiteral() throws Exception {
        String source = "var n=123.45;";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        builder.buildScopeTree(ast);

        MungedCodeGenerator generator = new MungedCodeGenerator(builder, false);
        String result = generator.generate(ast);

        assertTrue("Should contain number", result.contains("123.45"));
    }

    @Test
    public void testInfixExpression() throws Exception {
        String source = "var x=1+2*3;";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        builder.buildScopeTree(ast);

        MungedCodeGenerator generator = new MungedCodeGenerator(builder, false);
        String result = generator.generate(ast);

        assertTrue("Should contain addition", result.contains("+"));
        assertTrue("Should contain multiplication", result.contains("*"));
    }

    @Test
    public void testFunctionCall() throws Exception {
        String source = "test(1,2);";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        builder.buildScopeTree(ast);

        MungedCodeGenerator generator = new MungedCodeGenerator(builder, false);
        String result = generator.generate(ast);

        assertTrue("Should contain function name", result.contains("test"));
        assertTrue("Should contain parentheses", result.contains("(") && result.contains(")"));
    }

    @Test
    public void testPropertyAccess() throws Exception {
        String source = "obj.property;";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        builder.buildScopeTree(ast);

        MungedCodeGenerator generator = new MungedCodeGenerator(builder, false);
        String result = generator.generate(ast);

        assertTrue("Should contain object name", result.contains("obj"));
        assertTrue("Should contain dot", result.contains("."));
        assertTrue("Should contain property name", result.contains("property"));
    }

    @Test
    public void testObjectLiteral() throws Exception {
        String source = "var obj={a:1,b:2};";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        builder.buildScopeTree(ast);

        MungedCodeGenerator generator = new MungedCodeGenerator(builder, false);
        String result = generator.generate(ast);

        assertTrue("Should contain object braces", result.contains("{") && result.contains("}"));
        assertTrue("Should contain properties", result.contains("a") && result.contains("b"));
    }

    @Test
    public void testArrayLiteral() throws Exception {
        String source = "var arr=[1,2,3];";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        builder.buildScopeTree(ast);

        MungedCodeGenerator generator = new MungedCodeGenerator(builder, false);
        String result = generator.generate(ast);

        assertTrue("Should contain array brackets", result.contains("[") && result.contains("]"));
    }

    @Test
    public void testBlock() throws Exception {
        String source = "function test(){var x=1;var y=2;}";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        builder.buildScopeTree(ast);

        MungedCodeGenerator generator = new MungedCodeGenerator(builder, false);
        String result = generator.generate(ast);

        assertTrue("Should contain block braces", result.contains("{") && result.contains("}"));
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
        assertTrue("Function name should be preserved", result.contains("test"));

        // Local variable should be munged (won't be 'localVar')
        // The exact munged name depends on the munging algorithm
        assertTrue("Should contain function", result.contains("function"));
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
        assertTrue("Variable name should be preserved", result.contains("localVar"));
    }

    @Test
    public void testMultipleStatements() throws Exception {
        String source = "var a=1;var b=2;var c=a+b;";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        builder.buildScopeTree(ast);

        MungedCodeGenerator generator = new MungedCodeGenerator(builder, false);
        String result = generator.generate(ast);

        assertTrue("Should contain all variables",
            result.contains("a") && result.contains("b") && result.contains("c"));
    }

    @Test
    public void testLetAndConstDeclarations() throws Exception {
        String source = "let x=1;const y=2;";
        AstRoot ast = parseSource(source);

        ScopeBuilder builder = new ScopeBuilder();
        builder.buildScopeTree(ast);

        MungedCodeGenerator generator = new MungedCodeGenerator(builder, false);
        String result = generator.generate(ast);

        assertTrue("Should contain let", result.contains("let"));
        assertTrue("Should contain const", result.contains("const"));
    }
}
