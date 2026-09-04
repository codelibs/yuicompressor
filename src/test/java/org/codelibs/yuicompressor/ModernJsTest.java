package org.codelibs.yuicompressor;

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
}
