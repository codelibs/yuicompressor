package org.codelibs.yuicompressor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;

import com.yahoo.platform.yui.compressor.JavaScriptCompressor;
import com.yahoo.platform.yui.compressor.CssCompressor;

/**
 * Test cases for YUICompressor components without using main() method
 * to avoid System.exit() issues in tests
 */
public class YUICompressorTest {

    @Test
    public void testJavaScriptCompression() throws Exception {
        String input = "var x = 1; var y = 2;";
        StringWriter output = new StringWriter();
        
        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, false, false, false, false);
        
        String result = output.toString();
        assertTrue(result.length() > 0, "Should compress JavaScript");
        assertTrue(result.contains("var"), "Should contain var");
    }

    @Test
    public void testCssCompression() throws Exception {
        String input = "body { color: red; }";
        StringWriter output = new StringWriter();
        
        CssCompressor compressor = new CssCompressor(new StringReader(input));
        compressor.compress(output, -1);
        
        String result = output.toString();
        assertTrue(result.length() > 0, "Should compress CSS");
        assertTrue(result.contains("body"), "Should contain body");
    }

    @Test
    public void testJavaScriptWithNoMunge() throws Exception {
        String input = "function test() { var myVariable = 1; return myVariable; }";
        StringWriter output = new StringWriter();
        
        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, false, false, false, false);
        
        String result = output.toString();
        assertTrue(result.contains("myVariable"), "Should preserve variable names with nomunge");
    }

    @Test
    public void testJavaScriptWithMunge() throws Exception {
        String input = "function test() { var myVariable = 1; return myVariable; }";
        StringWriter output = new StringWriter();
        
        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);
        
        String result = output.toString();
        assertFalse(result.contains("myVariable"), "Should obfuscate variable names with munge");
    }
}
