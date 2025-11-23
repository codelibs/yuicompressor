/*
 * YUI Compressor
 * http://developer.yahoo.com/yui/compressor/
 * Author: Julien Lecomte - http://www.julienlecomte.net/
 * Copyright (c) 2011 Yahoo! Inc. All rights reserved.
 * The copyrights embodied in the content of this file are licensed
 * by Yahoo! Inc. under the BSD (revised) open source license.
 */
package com.yahoo.platform.yui.compressor;

import org.mozilla.javascript.*;
import org.mozilla.javascript.ast.*;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.*;

/**
 * Simplified JavaScript compressor using Rhino 1.8.0 API
 *
 * This is a minimal implementation to verify compatibility with Rhino 1.8.0.
 * Full functionality will be added incrementally.
 */
public class JavaScriptCompressor {

    private final CompilerEnvirons compilerEnv;
    private final ErrorReporter errorReporter;
    private final CommentPreserver commentPreserver;
    private AstRoot ast;

    public JavaScriptCompressor(Reader in, ErrorReporter reporter)
            throws IOException, EvaluatorException {

        this.errorReporter = reporter;
        this.commentPreserver = new CommentPreserver();

        // Setup compiler environment
        this.compilerEnv = new CompilerEnvirons();
        this.compilerEnv.setRecordingComments(true);
        this.compilerEnv.setRecordingLocalJsDocComments(true);
        this.compilerEnv.setLanguageVersion(Context.VERSION_ES6);
        this.compilerEnv.setGenerateDebugInfo(false);
        this.compilerEnv.setErrorReporter(reporter);

        // Parse the JavaScript
        Parser parser = new Parser(this.compilerEnv);
        try {
            this.ast = parser.parse(in, null, 1);

            // Analyze comments for preservation
            Set<Comment> comments = this.ast.getComments();
            this.commentPreserver.analyzeComments(comments);

        } catch (Exception e) {
            throw new EvaluatorException("Error parsing JavaScript: " + e.getMessage());
        }
    }

    public void compress(Writer out, int linebreakpos,
                        boolean munge, boolean verbose,
                        boolean preserveAllSemiColons, boolean disableOptimizations)
            throws IOException {

        try {
            // For now, just output the parsed AST
            // This maintains the basic structure without optimization
            String compressed;

            if (this.ast != null) {
                // Use Rhino's built-in toSource() for now
                compressed = this.ast.toSource();

                // Remove extra whitespace
                compressed = compressed.replaceAll("\\s+", " ");
                compressed = compressed.replaceAll(" \\{", "{");
                compressed = compressed.replaceAll("\\{ ", "{");
                compressed = compressed.replaceAll(" \\}", "}");
                compressed = compressed.replaceAll("\\} ", "}");
                compressed = compressed.replaceAll(" \\(", "(");
                compressed = compressed.replaceAll("\\( ", "(");
                compressed = compressed.replaceAll(" \\)", ")");
                compressed = compressed.replaceAll("\\) ", ")");
                compressed = compressed.replaceAll(" ;", ";");
                compressed = compressed.replaceAll("; ", ";");
                compressed = compressed.replaceAll(" ,", ",");
                compressed = compressed.replaceAll(", ", ",");

                // Insert preserved comments
                compressed = commentPreserver.insertComments(compressed);

                // Add line breaks if requested
                if (linebreakpos > 0) {
                    compressed = addLineBreaks(compressed, linebreakpos);
                }

                out.write(compressed);
            }

        } catch (Exception e) {
            throw new IOException("Error compressing JavaScript: " + e.getMessage(), e);
        }
    }

    private String addLineBreaks(String code, int linebreakpos) {
        if (linebreakpos <= 0 || code.length() <= linebreakpos) {
            return code;
        }

        StringBuilder result = new StringBuilder();
        int length = code.length();

        for (int i = 0; i < length; i += linebreakpos) {
            int end = Math.min(i + linebreakpos, length);
            result.append(code, i, end);
            if (end < length) {
                result.append('\n');
            }
        }

        return result.toString();
    }
}
