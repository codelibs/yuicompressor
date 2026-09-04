package org.codelibs.yuicompressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;

import com.yahoo.platform.yui.compressor.JavaScriptCompressor;
import com.yahoo.platform.yui.compressor.MungedCodeGenerator;

/**
 * Round-trip table for every parameter form Rhino 1.8.0 parses.
 *
 * <p>Every row either compresses correctly or fails loudly. There is no third
 * outcome, because the third outcome is what this replaces: {@code
 * visitParameterList} used to emit the bare binding name and ignore everything
 * around it, so {@code function f(a=1)} became {@code function f(a)} - changing
 * {@code f()} from 1 to undefined - and {@code function f(...args)} became
 * {@code function f(args)}, turning an array of the trailing arguments into a
 * single positional parameter and changing {@code f.length}. Both compressed to
 * output that parses cleanly, on syntax Rhino parses happily.
 *
 * <p>The residue is a destructuring pattern carrying a default,
 * {@code function f({b}={})}: Rhino's {@code getDefaultParams()} records
 * nothing for it, so its "={}" cannot be recovered and the compressor throws
 * rather than emit a parameter list that is quietly missing it.
 */
class ParameterListTest {

    private static final ErrorReporter SILENT = new ErrorReporter() {
        public void warning(String m, String s, int l, String ls, int lo) {
        }

        public void error(String m, String s, int l, String ls, int lo) {
        }

        public EvaluatorException runtimeError(String m, String s, int l, String ls, int lo) {
            return new EvaluatorException(m);
        }
    };

    private String compress(String source) throws IOException {
        StringWriter out = new StringWriter();
        new JavaScriptCompressor(new StringReader(source), SILENT).compress(out, -1, true, false, false, false);
        return out.toString().trim();
    }

    // --- rows that compress correctly ------------------------------------

    @Test
    void plainParameter() throws Exception {
        assertEquals("function f(b){return b;}", compress("function f(a){ return a; }"));
    }

    @Test
    void defaultParameter() throws Exception {
        assertEquals("function f(b=1){return b;}", compress("function f(a=1){ return a; }"));
    }

    @Test
    void restParameter() throws Exception {
        assertEquals("function f(...a){return a.length;}", compress("function f(...args){ return args.length; }"));
    }

    @Test
    void plainThenRestParameter() throws Exception {
        assertEquals("function f(b,...c){return c;}", compress("function f(a, ...rest){ return rest; }"));
    }

    @Test
    void destructuredArrayParameter() throws Exception {
        assertEquals("function f([d,c]){return d+c;}", compress("function f([a,b]){ return a+b; }"));
    }

    @Test
    void destructuredObjectParameter() throws Exception {
        // Not "{a}": in "{b}" the identifier is both the property key and the
        // binding, so munging it would read a property named "a" that the
        // caller never passed. The key must stay and only the binding is
        // munged. Caught by DifferentialExecutionTest, which ran it.
        assertEquals("function f({b:a}){return a;}", compress("function f({b}){ return b; }"));
    }

    @Test
    void defaultExpressionIsMunged() throws Exception {
        // The default expression is live code and its identifiers must be
        // munged with everything else. Rhino keeps it in a side list with no
        // parent link back to the function, so without repairing that link
        // every name in it resolves against the GLOBAL scope and this emits
        // "function f(b,a=alpha)" - alpha now being an undefined global.
        assertEquals("function f(b,a=b){return a;}", compress("function f(alpha, beta=alpha){ return beta; }"));
    }

    @Test
    void defaultExpressionSeesTheEnclosingScope() throws Exception {
        assertEquals("function outer(){var a=1;function f(b=a){return b;}return f();}",
                compress("function outer(){ var o1 = 1; function f(a=o1){ return a; } return f(); }"));
    }

    @Test
    void evalInsideADefaultExpressionPreventsMunging() throws Exception {
        // ScopeBuilder never traversed default expressions either, so an eval
        // hiding in one was invisible. It only became reachable once defaults
        // stopped being dropped, which is why it is pinned here.
        assertEquals("function f(secretName,b=eval(\"secretName\")){return b;}",
                compress("function f(secretName, b=eval(\"secretName\")){ return b; }"));
    }

    @Test
    void defaultParameterOnAnArrowFunctionKeepsItsParentheses() throws Exception {
        // The single-parameter arrow shortcut drops the parentheses, which
        // would have taken the "=1" with them.
        assertTrue(compress("var g = (a=1) => a;").startsWith("var g=(b=1)=>"),
                compress("var g = (a=1) => a;"));
    }

    @Test
    void defaultParameterOnAShorthandMethod() throws Exception {
        assertEquals("var o={m(b=1){return b;}};", compress("var o = { m(a=1){ return a; } };"));
    }

    // --- rows that fail loudly -------------------------------------------

    @Test
    void destructuredObjectParameterWithADefaultThrows() {
        assertUnreconstructable("function f({b}={}){ return b; }");
    }

    @Test
    void destructuredArrayParameterWithADefaultThrows() {
        assertUnreconstructable("function f([a,b]=[1,2]){ return a; }");
    }

    @Test
    void aMixedListThrowsIfAnyParameterIsUnreconstructable() {
        // The reported case. Note the other two parameters here ARE
        // reconstructable; a partial emission would have been the silent
        // truncation this exists to prevent.
        assertUnreconstructable("function f(a=1,{b}={},...c){ return a; }");
    }

    private void assertUnreconstructable(String source) {
        IOException failure = assertThrows(IOException.class, () -> compress(source));
        Throwable cause = failure.getCause();
        assertTrue(cause instanceof MungedCodeGenerator.UnsupportedSyntaxException,
                "expected UnsupportedSyntaxException, got " + cause);
        assertTrue(cause.getMessage().contains("destructuring parameter"),
                "the message must name what could not be reconstructed: " + cause.getMessage());
    }
}
