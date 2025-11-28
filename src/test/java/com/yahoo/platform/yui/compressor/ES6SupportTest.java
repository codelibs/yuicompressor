/*
 * YUI Compressor
 * http://developer.yahoo.com/yui/compressor/
 * Author: Julien Lecomte - http://www.julienlecomte.net/
 * Copyright (c) 2011 Yahoo! Inc. All rights reserved.
 * The copyrights embodied in the content of this file are licensed
 * by Yahoo! Inc. under the BSD (revised) open source license.
 */
package com.yahoo.platform.yui.compressor;

import static org.junit.Assert.*;

import java.io.StringReader;
import java.io.StringWriter;

import org.junit.Ignore;
import org.junit.Test;
import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.ast.AstRoot;

/**
 * Test cases for ES6+ syntax support
 */
public class ES6SupportTest {

    private String compress(String source) throws Exception {
        return compress(source, true);
    }

    private String compress(String source, boolean munge) throws Exception {
        StringReader reader = new StringReader(source);
        JavaScriptCompressor compressor = new JavaScriptCompressor(reader, null);
        StringWriter writer = new StringWriter();
        compressor.compress(writer, -1, munge, false, false, false);
        return writer.toString();
    }

    private AstRoot parseSource(String source) throws Exception {
        CompilerEnvirons env = new CompilerEnvirons();
        env.setRecordingComments(false);
        env.setLanguageVersion(Context.VERSION_ES6);
        Parser parser = new Parser(env);
        return parser.parse(new StringReader(source), null, 1);
    }

    // ===== Arrow Function Tests =====

    @Test
    public void testArrowFunctionSingleParam() throws Exception {
        String source = "const f = x => x * 2;";
        String result = compress(source);
        assertTrue("Should contain arrow syntax", result.contains("=>"));
    }

    @Test
    public void testArrowFunctionMultipleParams() throws Exception {
        String source = "const add = (a, b) => a + b;";
        String result = compress(source);
        assertTrue("Should contain arrow syntax", result.contains("=>"));
    }

    @Test
    public void testArrowFunctionWithBlock() throws Exception {
        String source = "const f = x => { return x * 2; };";
        String result = compress(source);
        assertTrue("Should contain arrow syntax", result.contains("=>"));
        assertTrue("Should contain block braces", result.contains("{"));
    }

    @Test
    public void testArrowFunctionMunging() throws Exception {
        String source = "const f = (longParamName) => longParamName * 2;";
        String result = compress(source, true);
        assertFalse("Parameter should be munged", result.contains("longParamName"));
    }

    // ===== Template Literal Tests =====

    @Test
    public void testTemplateLiteralSimple() throws Exception {
        String source = "const s = `hello world`;";
        String result = compress(source);
        assertTrue("Should contain template literal", result.contains("`"));
    }

    @Test
    public void testTemplateLiteralWithInterpolation() throws Exception {
        String source = "const name = \"World\"; const s = `Hello ${name}!`;";
        String result = compress(source);
        assertTrue("Should contain template interpolation", result.contains("${"));
    }

    // ===== Let and Const Tests =====

    @Test
    public void testLetDeclaration() throws Exception {
        String source = "let x = 1; let y = 2;";
        String result = compress(source);
        assertTrue("Should preserve let keyword", result.contains("let "));
    }

    @Test
    public void testConstDeclaration() throws Exception {
        String source = "const x = 1; const y = 2;";
        String result = compress(source);
        assertTrue("Should preserve const keyword", result.contains("const "));
    }

    // ===== Control Flow Tests =====

    @Test
    public void testIfStatement() throws Exception {
        String source = "if (x > 0) { console.log(x); }";
        String result = compress(source);
        assertTrue("Should contain if statement", result.contains("if("));
    }

    @Test
    public void testIfElseStatement() throws Exception {
        String source = "if (x > 0) { console.log('positive'); } else { console.log('non-positive'); }";
        String result = compress(source);
        assertTrue("Should contain if", result.contains("if("));
        assertTrue("Should contain else", result.contains("else"));
    }

    @Test
    public void testForLoop() throws Exception {
        String source = "for (let i = 0; i < 10; i++) { console.log(i); }";
        String result = compress(source);
        assertTrue("Should contain for loop", result.contains("for("));
    }

    @Test
    public void testWhileLoop() throws Exception {
        String source = "while (x > 0) { x--; }";
        String result = compress(source);
        assertTrue("Should contain while loop", result.contains("while("));
    }

    @Test
    public void testDoWhileLoop() throws Exception {
        String source = "do { x++; } while (x < 10);";
        String result = compress(source);
        assertTrue("Should contain do", result.contains("do"));
        assertTrue("Should contain while", result.contains("while("));
    }

    @Test
    public void testSwitchStatement() throws Exception {
        String source = "switch(x) { case 1: break; case 2: break; default: break; }";
        String result = compress(source);
        assertTrue("Should contain switch", result.contains("switch("));
        assertTrue("Should contain case", result.contains("case "));
        assertTrue("Should contain default", result.contains("default:"));
    }

    @Test
    public void testTryCatchFinally() throws Exception {
        String source = "try { foo(); } catch(e) { console.log(e); } finally { cleanup(); }";
        String result = compress(source);
        assertTrue("Should contain try", result.contains("try"));
        assertTrue("Should contain catch", result.contains("catch("));
        assertTrue("Should contain finally", result.contains("finally"));
    }

    // ===== Operator Tests =====

    @Test
    public void testComparisonOperators() throws Exception {
        String source = "const a = x == y; const b = x === y; const c = x != y; const d = x !== y;";
        String result = compress(source);
        assertTrue("Should contain ==", result.contains("=="));
        assertTrue("Should contain ===", result.contains("==="));
        assertTrue("Should contain !=", result.contains("!="));
        assertTrue("Should contain !==", result.contains("!=="));
    }

    @Test
    public void testLogicalOperators() throws Exception {
        String source = "const a = x && y; const b = x || y; const c = !x;";
        String result = compress(source);
        assertTrue("Should contain &&", result.contains("&&"));
        assertTrue("Should contain ||", result.contains("||"));
        assertTrue("Should contain !", result.contains("!"));
    }

    @Test
    public void testBitwiseOperators() throws Exception {
        String source = "const a = x & y; const b = x | y; const c = x ^ y; const d = ~x;";
        String result = compress(source);
        assertTrue("Should contain &", result.contains("&"));
        assertTrue("Should contain |", result.contains("|"));
        assertTrue("Should contain ^", result.contains("^"));
        assertTrue("Should contain ~", result.contains("~"));
    }

    @Test
    public void testTernaryOperator() throws Exception {
        String source = "const r = x > 0 ? 'positive' : 'non-positive';";
        String result = compress(source);
        assertTrue("Should contain ?", result.contains("?"));
        assertTrue("Should contain :", result.contains(":"));
    }

    @Test
    public void testIncrementDecrement() throws Exception {
        String source = "x++; ++y; x--; --y;";
        String result = compress(source);
        assertTrue("Should contain ++", result.contains("++"));
        assertTrue("Should contain --", result.contains("--"));
    }

    // ===== Object and Array Tests =====

    @Test
    public void testObjectLiteral() throws Exception {
        String source = "const obj = { a: 1, b: 2 };";
        String result = compress(source);
        assertTrue("Should contain object braces", result.contains("{"));
        assertTrue("Should contain property", result.contains("a:"));
    }

    @Test
    public void testArrayLiteral() throws Exception {
        String source = "const arr = [1, 2, 3];";
        String result = compress(source);
        assertTrue("Should contain array brackets", result.contains("["));
    }

    @Test
    public void testElementAccess() throws Exception {
        String source = "const x = arr[0];";
        String result = compress(source);
        assertTrue("Should contain element access", result.contains("[0]"));
    }

    // ===== Function Tests =====

    @Test
    public void testFunctionDeclaration() throws Exception {
        String source = "function foo(a, b) { return a + b; }";
        String result = compress(source);
        assertTrue("Should contain function", result.contains("function"));
        assertTrue("Should contain function name", result.contains("foo"));
    }

    @Test
    public void testFunctionExpression() throws Exception {
        String source = "const foo = function(a, b) { return a + b; };";
        String result = compress(source);
        assertTrue("Should contain function", result.contains("function"));
    }

    @Test
    public void testNewExpression() throws Exception {
        String source = "const d = new Date();";
        String result = compress(source);
        assertTrue("Should contain new", result.contains("new "));
        assertTrue("Should contain Date", result.contains("Date"));
    }

    // ===== Keyword Tests =====

    @Test
    public void testThisKeyword() throws Exception {
        String source = "const obj = { name: 'test', getName: function() { return this.name; } };";
        String result = compress(source);
        assertTrue("Should contain this", result.contains("this"));
    }

    @Test
    public void testTypeofOperator() throws Exception {
        String source = "const t = typeof x;";
        String result = compress(source);
        assertTrue("Should contain typeof", result.contains("typeof "));
    }

    @Test
    public void testInstanceofOperator() throws Exception {
        String source = "const is = x instanceof Date;";
        String result = compress(source);
        assertTrue("Should contain instanceof", result.contains(" instanceof "));
    }

    @Test
    public void testInOperator() throws Exception {
        String source = "const has = 'a' in obj;";
        String result = compress(source);
        assertTrue("Should contain in", result.contains(" in "));
    }

    // ===== Regular Expression Tests =====

    @Test
    public void testRegExpLiteral() throws Exception {
        String source = "const re = /test/gi;";
        String result = compress(source);
        assertTrue("Should contain regex", result.contains("/test/"));
        assertTrue("Should contain flags", result.contains("gi"));
    }

    // ===== Scope and Munging Tests =====

    @Test
    public void testVariableMunging() throws Exception {
        String source = "function test() { var longVariableName = 1; return longVariableName; }";
        String result = compress(source, true);
        assertFalse("Variable should be munged", result.contains("longVariableName"));
    }

    @Test
    public void testPropertyNotMunged() throws Exception {
        String source = "const obj = { propertyName: 1 }; console.log(obj.propertyName);";
        String result = compress(source, true);
        assertTrue("Property should NOT be munged", result.contains("propertyName"));
    }

    @Test
    public void testGlobalNotMunged() throws Exception {
        String source = "console.log('test'); window.alert('hello');";
        String result = compress(source, true);
        assertTrue("console should NOT be munged", result.contains("console"));
        assertTrue("window should NOT be munged", result.contains("window"));
    }

    // ===== AST Parsing Tests =====

    @Test
    public void testParseArrowFunction() throws Exception {
        String source = "const f = x => x * 2;";
        AstRoot ast = parseSource(source);
        assertNotNull("Should parse arrow function", ast);
    }

    @Test
    public void testParseTemplateLiteral() throws Exception {
        String source = "const s = `hello ${name}`;";
        AstRoot ast = parseSource(source);
        assertNotNull("Should parse template literal", ast);
    }

    @Ignore("Rhino 1.8.0 does not support ES6 class syntax")
    @Test
    public void testParseClass() throws Exception {
        String source = "class Foo { constructor(x) { this.x = x; } getX() { return this.x; } }";
        AstRoot ast = parseSource(source);
        assertNotNull("Should parse class", ast);
    }

    @Test
    public void testParseForOf() throws Exception {
        // Note: Rhino 1.8.0 does not support 'const' in for-of loops, using 'let' instead
        String source = "for (let x of arr) { console.log(x); }";
        AstRoot ast = parseSource(source);
        assertNotNull("Should parse for-of loop", ast);
    }

    @Test
    public void testParseDestructuring() throws Exception {
        String source = "const [a, b] = arr; const {x, y} = obj;";
        AstRoot ast = parseSource(source);
        assertNotNull("Should parse destructuring", ast);
    }

    @Ignore("Rhino 1.8.0 does not support spread operator in arrays")
    @Test
    public void testParseSpread() throws Exception {
        String source = "const arr2 = [...arr1, 4, 5];";
        AstRoot ast = parseSource(source);
        assertNotNull("Should parse spread operator", ast);
    }

    @Test
    public void testParseDefaultParams() throws Exception {
        String source = "function foo(x = 1, y = 2) { return x + y; }";
        AstRoot ast = parseSource(source);
        assertNotNull("Should parse default parameters", ast);
    }

    @Test
    public void testParseRestParams() throws Exception {
        String source = "function foo(...args) { return args.length; }";
        AstRoot ast = parseSource(source);
        assertNotNull("Should parse rest parameters", ast);
    }

    // ===== Getter/Setter Tests =====

    @Test
    public void testObjectGetterMethod() throws Exception {
        String source = "var obj = { get value() { return this._value; } };";
        String result = compress(source);
        assertTrue("Should contain 'get' keyword", result.contains("get "));
        assertTrue("Should contain getter method", result.contains("value()"));
    }

    @Test
    public void testObjectSetterMethod() throws Exception {
        String source = "var obj = { set value(v) { this._value = v; } };";
        String result = compress(source);
        assertTrue("Should contain 'set' keyword", result.contains("set "));
        assertTrue("Should contain setter method", result.contains("value("));
    }

    @Test
    public void testObjectGetterSetterCombined() throws Exception {
        String source = "var obj = { get x() { return this._x; }, set x(v) { this._x = v; } };";
        String result = compress(source);
        assertTrue("Should contain 'get' keyword", result.contains("get "));
        assertTrue("Should contain 'set' keyword", result.contains("set "));
    }

    // ===== For-in Loop Tests =====

    @Test
    public void testForInLoop() throws Exception {
        String source = "for (var key in obj) { console.log(key); }";
        String result = compress(source);
        assertTrue("Should contain 'for' keyword", result.contains("for("));
        assertTrue("Should contain 'in' keyword", result.contains(" in "));
    }

    @Test
    public void testForInLoopWithLet() throws Exception {
        String source = "for (let key in obj) { console.log(key); }";
        String result = compress(source);
        assertTrue("Should contain 'for' keyword", result.contains("for("));
        assertTrue("Should contain 'in' keyword", result.contains(" in "));
    }

    // ===== For-of Loop Tests =====

    @Test
    public void testForOfLoopWithVar() throws Exception {
        String source = "for (var item of arr) { console.log(item); }";
        String result = compress(source);
        assertTrue("Should contain 'for' keyword", result.contains("for("));
        assertTrue("Should contain 'of' keyword", result.contains(" of "));
    }

    @Test
    public void testForOfLoopWithLet() throws Exception {
        String source = "for (let item of arr) { console.log(item); }";
        String result = compress(source);
        assertTrue("Should contain 'for' keyword", result.contains("for("));
        assertTrue("Should contain 'of' keyword", result.contains(" of "));
    }

    @Test
    public void testForOfLoopMunging() throws Exception {
        // Note: Global scope variables are not munged (could be referenced externally)
        // Wrap in a function to test munging behavior
        String source = "function test() { for (let longItemName of arr) { console.log(longItemName); } }";
        String result = compress(source, true);
        assertFalse("Loop variable should be munged inside function", result.contains("longItemName"));
    }

    // ===== Scope Block Tests =====

    @Test
    public void testBlockScopeWithLet() throws Exception {
        String source = "{ let blockVar = 1; console.log(blockVar); }";
        String result = compress(source);
        assertTrue("Should contain block braces", result.contains("{"));
        assertTrue("Should contain 'let' keyword", result.contains("let "));
    }

    @Test
    public void testBlockScopeWithConst() throws Exception {
        String source = "{ const BLOCK_CONST = 42; console.log(BLOCK_CONST); }";
        String result = compress(source);
        assertTrue("Should contain block braces", result.contains("{"));
        assertTrue("Should contain 'const' keyword", result.contains("const "));
    }

    @Test
    public void testNestedBlockScopes() throws Exception {
        String source = "{ let outer = 1; { let inner = 2; console.log(inner); } console.log(outer); }";
        String result = compress(source);
        assertNotNull("Should compress nested blocks", result);
        assertTrue("Should contain 'let' keyword", result.contains("let "));
    }

    // ===== Array Destructuring with Empty Elements =====

    @Test
    public void testArrayDestructuringWithEmptyElement() throws Exception {
        // Test: const [a, , b] = arr; (middle element is empty)
        String source = "const [first, , third] = [1, 2, 3];";
        String result = compress(source);
        assertNotNull("Should handle array destructuring with empty elements", result);
    }

    @Test
    public void testFunctionParameterDestructuringWithEmptyElement() throws Exception {
        // Test: function([a, , b]) where middle element is skipped
        String source = "function test([first, , third]) { return first + third; }";
        String result = compress(source);
        assertNotNull("Should handle parameter destructuring with empty elements", result);
    }

    // ===== Combined ES6 Features =====

    @Test
    public void testForOfWithDestructuring() throws Exception {
        String source = "for (let [key, value] of entries) { console.log(key, value); }";
        String result = compress(source);
        assertTrue("Should contain 'for' keyword", result.contains("for("));
        assertTrue("Should contain 'of' keyword", result.contains(" of "));
    }

    @Test
    public void testArrowFunctionInForOf() throws Exception {
        String source = "for (let x of arr) { const fn = y => y * x; console.log(fn(2)); }";
        String result = compress(source);
        assertTrue("Should contain arrow syntax", result.contains("=>"));
        assertTrue("Should contain 'of' keyword", result.contains(" of "));
    }
}
