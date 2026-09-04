package org.codelibs.yuicompressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;

import com.yahoo.platform.yui.compressor.CssCompressor;
import com.yahoo.platform.yui.compressor.JavaScriptCompressor;

/**
 * Locks down the externally visible behaviour of the compressor options so that
 * later refactoring cannot change them silently.
 */
class CliOptionTest {

    private static final ErrorReporter SILENT = new ErrorReporter() {
        public void warning(String m, String s, int l, String ls, int lo) {
        }

        public void error(String m, String s, int l, String ls, int lo) {
        }

        public EvaluatorException runtimeError(String m, String s, int l, String ls, int lo) {
            return new EvaluatorException(m);
        }
    };

    private String js(String source, int linebreak, boolean munge, boolean preserveSemi, boolean disableOpt)
            throws Exception {
        StringWriter out = new StringWriter();
        new JavaScriptCompressor(new StringReader(source), SILENT)
                .compress(out, linebreak, munge, false, preserveSemi, disableOpt);
        return out.toString();
    }

    private String css(String source, int linebreak) throws Exception {
        StringWriter out = new StringWriter();
        new CssCompressor(new StringReader(source)).compress(out, linebreak);
        return out.toString();
    }

    @Test
    void mungeRenamesLocalVariables() throws Exception {
        String result = js("function f(){ var longLocalName = 1; return longLocalName; }", -1, true, false, false);
        assertFalse(result.contains("longLocalName"), "local variable should be renamed: " + result);
    }

    @Test
    void nomungeKeepsLocalVariables() throws Exception {
        String result = js("function f(){ var longLocalName = 1; return longLocalName; }", -1, false, false, false);
        assertTrue(result.contains("longLocalName"), "local variable should be kept: " + result);
    }

    @Test
    void globalNamesAreNeverRenamed() throws Exception {
        String result = js("var globalName = 1; globalName++;", -1, true, false, false);
        assertTrue(result.contains("globalName"), "global should be kept: " + result);
    }

    @Test
    void bangCommentIsPreserved() throws Exception {
        String result = js("/*! keep me */\nvar a = 1; a++;", -1, true, false, false);
        assertTrue(result.contains("keep me"), "the /*! comment should survive: " + result);
    }

    @Test
    void normalCommentIsRemoved() throws Exception {
        String result = js("/* drop me */\nvar a = 1; a++;", -1, true, false, false);
        assertFalse(result.contains("drop me"), "an ordinary comment should be removed: " + result);
    }

    @Test
    void cssCompressesWithoutLineBreaks() throws Exception {
        assertEquals("body{color:red}", css("body { color: red; }", -1).trim());
    }

    @Test
    void cssBangCommentIsPreserved() throws Exception {
        String result = css("/*! license */\nbody { color: red; }", -1);
        assertTrue(result.contains("license"), "the /*! comment should survive: " + result);
    }
}
