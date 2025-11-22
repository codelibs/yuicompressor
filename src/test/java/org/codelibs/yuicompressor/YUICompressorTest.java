package org.codelibs.yuicompressor;

import static org.junit.Assert.*;

import java.io.StringReader;
import java.io.StringWriter;

import org.junit.Test;

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
        assertTrue("Should compress JavaScript", result.length() > 0);
        assertTrue("Should contain var", result.contains("var"));
    }

    @Test
    public void testCssCompression() throws Exception {
        String input = "body { color: red; }";
        StringWriter output = new StringWriter();
        
        CssCompressor compressor = new CssCompressor(new StringReader(input));
        compressor.compress(output, -1);
        
        String result = output.toString();
        assertTrue("Should compress CSS", result.length() > 0);
        assertTrue("Should contain body", result.contains("body"));
    }

    @Test
    public void testJavaScriptWithNoMunge() throws Exception {
        String input = "function test() { var myVariable = 1; return myVariable; }";
        StringWriter output = new StringWriter();
        
        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, false, false, false, false);
        
        String result = output.toString();
        assertTrue("Should preserve variable names with nomunge", result.contains("myVariable"));
    }

    @Test
    public void testJavaScriptWithMunge() throws Exception {
        String input = "function test() { var myVariable = 1; return myVariable; }";
        StringWriter output = new StringWriter();
        
        JavaScriptCompressor compressor = new JavaScriptCompressor(
            new StringReader(input), null);
        compressor.compress(output, -1, true, false, false, false);
        
        String result = output.toString();
        assertFalse("Should obfuscate variable names with munge", result.contains("myVariable"));
    }
}
