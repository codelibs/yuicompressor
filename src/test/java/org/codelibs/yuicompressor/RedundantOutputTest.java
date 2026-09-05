package org.codelibs.yuicompressor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringReader;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;

import com.yahoo.platform.yui.compressor.JavaScriptCompressor;

/**
 * Output that is correct but longer than it needs to be.
 *
 * <p>Two shapes the generator used to emit: a ";" immediately before a "}", and
 * parentheses around a conditional on an assignment's right-hand side. Both are
 * pinned here together with the neighbouring cases that must keep their
 * separator or their parentheses, because removing either one in the wrong place
 * changes what the program means.
 */
class RedundantOutputTest {

    private static final ErrorReporter SILENT = new ErrorReporter() {
        public void warning(String message, String sourceName, int line, String lineSource, int lineOffset) {
        }

        public void error(String message, String sourceName, int line, String lineSource, int lineOffset) {
        }

        public EvaluatorException runtimeError(String message, String sourceName, int line, String lineSource,
                int lineOffset) {
            return new EvaluatorException(message);
        }
    };

    private String compress(String source) throws Exception {
        StringWriter out = new StringWriter();
        new JavaScriptCompressor(new StringReader(source), SILENT).compress(out, -1, true, false, false, false);
        return out.toString();
    }

    // ------------------------------------------------------------------
    // No ";" immediately before a "}".
    // ------------------------------------------------------------------

    @Test
    void functionBodyDropsItsTrailingSemicolon() throws Exception {
        assertEquals("function f(){var b=1;return b}", compress("function f(){var a=1;return a;}"));
    }

    @Test
    void syntheticBracesAroundASingleStatementBodyCarryNoSemicolon() throws Exception {
        assertEquals("if(a){b()}else{c()}", compress("if(a)b();else c();"));
        assertEquals("while(a){b()}", compress("while(a)b();"));
        assertEquals("for(var i=0;i<3;i++){f(i)}", compress("for(var i=0;i<3;i++)f(i);"));
    }

    @Test
    void tryCatchFinallyBlocksDropTheirTrailingSemicolons() throws Exception {
        assertEquals("try{a()}catch(e){b()}finally{c()}",
                compress("try{a();}catch(e){b();}finally{c();}"));
    }

    @Test
    void separatorsBetweenStatementsAreKept() throws Exception {
        assertEquals("function f(){a();b();c()}", compress("function f(){a();b();c();}"));
    }

    /**
     * Only the last case's last statement is followed by the switch's "}"; every
     * other one is followed by "case", which needs the ";" to separate them.
     */
    @Test
    void onlyTheFinalSwitchCaseDropsItsSemicolon() throws Exception {
        assertEquals("switch(a){case 1:b();case 2:c()}", compress("switch(a){case 1:b();case 2:c();}"));
        assertEquals("switch(a){case 1:b();break;default:c()}",
                compress("switch(a){case 1:b();break;default:c();}"));
    }

    @Test
    void anEmptyLoopBodyKeepsItsSemicolon() throws Exception {
        assertEquals("for(;;);", compress("for(;;);"));
    }

    // ------------------------------------------------------------------
    // No parentheses around a conditional on an assignment's right-hand side.
    // ------------------------------------------------------------------

    @Test
    void assignmentRightHandSideConditionalNeedsNoParentheses() throws Exception {
        assertEquals("x=y?z:w;", compress("x=y?z:w;"));
    }

    @Test
    void compoundAssignmentRightHandSideConditionalNeedsNoParentheses() throws Exception {
        assertEquals("x+=y?z:w;", compress("x+=y?z:w;"));
    }

    /** "a+(b?c:d)" without the parentheses re-parses as "(a+b)?c:d". */
    @Test
    void conditionalUnderATighterOperatorKeepsItsParentheses() throws Exception {
        assertEquals("x=a+(b?c:d);", compress("x=a+(b?c:d);"));
        assertEquals("x=(a?b:c)+d;", compress("x=(a?b:c)+d;"));
    }

    @Test
    void aConditionalThatNeverHadParenthesesStillHasNone() throws Exception {
        assertEquals("var t=a?b:c;", compress("var t=a?b:c;"));
        assertEquals("f(a?b:c);", compress("f(a?b:c);"));
    }
}
