package org.codelibs.yuicompressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;

import com.yahoo.platform.yui.compressor.JavaScriptCompressor;

/** Regression tests for modern JavaScript syntax. */
class ModernJsTest {

    static final ErrorReporter SILENT = new ErrorReporter() {
        public void warning(String m, String s, int l, String ls, int lo) {
        }

        public void error(String m, String s, int l, String ls, int lo) {
        }

        public EvaluatorException runtimeError(String m, String s, int l, String ls, int lo) {
            return new EvaluatorException(m);
        }
    };

    private String compress(String source) throws Exception {
        StringWriter out = new StringWriter();
        new JavaScriptCompressor(new StringReader(source), SILENT)
                .compress(out, -1, true, false, false, false);
        return out.toString().trim();
    }

    private String compressNoMunge(String source) throws Exception {
        StringWriter out = new StringWriter();
        new JavaScriptCompressor(new StringReader(source), SILENT)
                .compress(out, -1, false, false, false, false);
        return out.toString().trim();
    }

    @Test
    void optionalChainingOnPropertyIsPreserved() throws Exception {
        String result = compress("var v = a?.b?.c;");
        assertTrue(result.contains("?."),
                "dropping '?.' turns a safe undefined into a TypeError: " + result);
    }

    @Test
    void optionalChainingOnElementIsPreserved() throws Exception {
        String result = compress("var w = obj?.[key];");
        assertTrue(result.contains("?."), "optional element access must be kept: " + result);
    }

    @Test
    void optionalCallIsPreserved() throws Exception {
        String result = compress("foo?.(1);");
        assertTrue(result.contains("?."), "an optional call must be kept: " + result);
    }

    @Test
    void catchWithoutBindingStaysValid() throws Exception {
        String result = compress("try { f(); } catch { g(); }");
        assertFalse(result.contains("catch()"),
                "'catch()' is a syntax error; the binding is optional: " + result);
    }

    @Test
    void shorthandMethodStaysShorthand() throws Exception {
        String result = compress("var o = { m(){ return 1; } }; o.m();");
        assertFalse(result.contains("function"),
                "a shorthand method should not expand to a function expression: " + result);
    }

    // Rhino marks every link of an optional chain with the same QUESTION_DOT
    // type (and FunctionCall.isOptionalCall() over-reports the same way), so a
    // naive "type says QUESTION_DOT => emit '?.'" fix widens mixed chains: it
    // adds a "?." to links that were never optional in the source. That is its
    // own silent behaviour change - e.g. "a?.b.c" throws on a null "a" while
    // "a?.b?.c" quietly evaluates to undefined. These assert the exact output
    // so a chain is reproduced link-for-link, not merely "contains '?.'
    // somewhere".

    @Test
    void optionalChainDoesNotWidenToTrailingPlainProperty() throws Exception {
        String result = compressNoMunge("var v = a?.b.c;");
        assertEquals("var v=a?.b.c;", result,
                "only 'a?.b' is optional; 'a?.b?.c' would turn a TypeError into undefined: " + result);
    }

    @Test
    void optionalChainDoesNotWidenToLeadingOrTrailingPlainProperty() throws Exception {
        String result = compressNoMunge("var v = a.b?.c.d;");
        assertEquals("var v=a.b?.c.d;", result,
                "only 'c' is reached optionally; neither 'a.b' nor '.d' may gain '?.': " + result);
    }

    @Test
    void optionalElementChainDoesNotWidenToTrailingPlainProperty() throws Exception {
        String result = compressNoMunge("var v = a?.[0].c;");
        assertEquals("var v=a?.[0].c;", result,
                "the trailing '.c' is not optional and must not gain '?.': " + result);
    }

    @Test
    void optionalCallChainDoesNotWidenToTrailingPlainProperty() throws Exception {
        String result = compressNoMunge("var v = a?.b().c;");
        assertEquals("var v=a?.b().c;", result,
                "the call and the trailing '.c' are not themselves optional: " + result);
    }
}
