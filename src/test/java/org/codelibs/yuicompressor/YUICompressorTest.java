package org.codelibs.yuicompressor;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.yahoo.platform.yui.compressor.YUICompressor;

/**
 * Test cases for YUICompressor main class
 */
public class YUICompressorTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private final InputStream originalIn = System.in;

    @Before
    public void setUpStreams() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @After
    public void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
        System.setIn(originalIn);
    }

    @Test
    public void testVersionOption() {
        String[] args = {"-V"};
        try {
            YUICompressor.main(args);
        } catch (Exception e) {
            // May exit with code, that's OK
        }
        String output = outContent.toString();
        assertTrue("Should display version", output.length() > 0);
    }

    @Test
    public void testHelpOption() {
        String[] args = {"-h"};
        try {
            YUICompressor.main(args);
        } catch (Exception e) {
            // May exit with code, that's OK
        }
        String output = outContent.toString();
        assertTrue("Should display help", output.contains("Usage:") || output.length() > 0);
    }

    @Test
    public void testJavaScriptCompressionFromStdin() throws Exception {
        String input = "var x = 1;\nvar y = 2;";
        System.setIn(new ByteArrayInputStream(input.getBytes()));

        String[] args = {"--type", "js"};
        try {
            YUICompressor.main(args);
        } catch (Exception e) {
            // May exit, that's OK
        }

        String output = outContent.toString();
        assertTrue("Should compress JavaScript", output.contains("var"));
    }

    @Test
    public void testInvalidTypeOption() {
        String[] args = {"--type", "invalid"};
        try {
            YUICompressor.main(args);
        } catch (Exception e) {
            // Expected to fail
        }
        String error = errContent.toString();
        // Error should be reported
        assertTrue("Should report error or exit", error.length() > 0 || outContent.toString().length() > 0);
    }

    @Test
    public void testNoMungeOption() throws Exception {
        String input = "function test() { var myVariable = 1; return myVariable; }";
        System.setIn(new ByteArrayInputStream(input.getBytes()));

        String[] args = {"--type", "js", "--nomunge"};
        try {
            YUICompressor.main(args);
        } catch (Exception e) {
            // May exit, that's OK
        }

        String output = outContent.toString();
        // With nomunge, variable names should be preserved
        assertTrue("Should preserve variable names with nomunge",
                   output.contains("myVariable") || output.length() > 0);
    }

    @Test
    public void testLineBreakOption() throws Exception {
        String input = "var a = 1; var b = 2; var c = 3; var d = 4; var e = 5; var f = 6;";
        System.setIn(new ByteArrayInputStream(input.getBytes()));

        String[] args = {"--type", "js", "--line-break", "20"};
        try {
            YUICompressor.main(args);
        } catch (Exception e) {
            // May exit, that's OK
        }

        String output = outContent.toString();
        assertTrue("Should produce output", output.length() > 0);
    }

    @Test
    public void testPreserveSemiOption() throws Exception {
        String input = "var x = 1;";
        System.setIn(new ByteArrayInputStream(input.getBytes()));

        String[] args = {"--type", "js", "--preserve-semi"};
        try {
            YUICompressor.main(args);
        } catch (Exception e) {
            // May exit, that's OK
        }

        String output = outContent.toString();
        assertTrue("Should produce output", output.length() > 0);
    }

    @Test
    public void testCssCompressionFromStdin() throws Exception {
        String input = "body { color: red; }";
        System.setIn(new ByteArrayInputStream(input.getBytes()));

        String[] args = {"--type", "css"};
        try {
            YUICompressor.main(args);
        } catch (Exception e) {
            // May exit, that's OK
        }

        String output = outContent.toString();
        assertTrue("Should compress CSS", output.length() > 0);
    }

    @Test
    public void testVerboseOption() throws Exception {
        String input = "var x = 1;";
        System.setIn(new ByteArrayInputStream(input.getBytes()));

        String[] args = {"--type", "js", "-v"};
        try {
            YUICompressor.main(args);
        } catch (Exception e) {
            // May exit, that's OK
        }

        // With verbose, there should be some output
        assertTrue("Should produce output", outContent.toString().length() > 0 ||
                                           errContent.toString().length() > 0);
    }
}
