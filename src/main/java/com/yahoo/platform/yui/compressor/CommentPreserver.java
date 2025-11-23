/*
 * YUI Compressor
 * http://developer.yahoo.com/yui/compressor/
 * Author: Julien Lecomte - http://www.julienlecomte.net/
 * Copyright (c) 2011 Yahoo! Inc. All rights reserved.
 * The copyrights embodied in the content of this file are licensed
 * by Yahoo! Inc. under the BSD (revised) open source license.
 */
package com.yahoo.platform.yui.compressor;

import org.mozilla.javascript.ast.Comment;
import java.util.*;

/**
 * Handles preservation of special comments during JavaScript compression.
 *
 * Preserves two types of comments:
 * 1. KEEP comments: /*! ... * / - Important comments like licenses
 * 2. CONDITIONAL comments: /*@cc_on ... @ * / - IE conditional compilation
 */
public class CommentPreserver {

    /**
     * Type of special comment
     */
    public enum CommentType {
        /**
         * Keep comment: /*! ... * /
         * Used for license headers and other important comments
         */
        KEEP,

        /**
         * Conditional comment: /*@cc_on ... @ * /
         * Used for IE conditional compilation
         */
        CONDITIONAL,

        /**
         * Regular comment (not preserved)
         */
        REGULAR
    }

    /**
     * Represents a comment that should be preserved in the output
     */
    public static class PreservedComment implements Comparable<PreservedComment> {
        private final int position;
        private final String text;
        private final CommentType type;

        public PreservedComment(int position, String text, CommentType type) {
            this.position = position;
            this.text = text;
            this.type = type;
        }

        public int getPosition() {
            return position;
        }

        public String getText() {
            return text;
        }

        public CommentType getType() {
            return type;
        }

        @Override
        public int compareTo(PreservedComment other) {
            return Integer.compare(this.position, other.position);
        }
    }

    private final List<PreservedComment> preservedComments = new ArrayList<>();

    /**
     * Analyzes a set of comments and identifies which ones should be preserved
     *
     * @param comments Set of comments from the AST
     */
    public void analyzeComments(Set<Comment> comments) {
        if (comments == null) {
            return;
        }

        for (Comment comment : comments) {
            CommentType type = identifyCommentType(comment);

            if (type != CommentType.REGULAR) {
                String text = comment.getValue();
                int position = comment.getAbsolutePosition();

                // Normalize the comment text
                String normalized = normalizeComment(text, type);

                preservedComments.add(new PreservedComment(position, normalized, type));
            }
        }

        // Sort by position for easier insertion
        Collections.sort(preservedComments);
    }

    /**
     * Identifies the type of a comment
     *
     * @param comment Comment to analyze
     * @return Type of the comment
     */
    private CommentType identifyCommentType(Comment comment) {
        String value = comment.getValue();

        // Check for KEEP comment: /*! ... * /
        if (value.startsWith("!")) {
            return CommentType.KEEP;
        }

        // Check for CONDITIONAL comment: /*@cc_on ... @ * /
        // Also check for @if, @elif, @else, @end patterns
        if (value.startsWith("@cc_on") ||
            value.matches("(?s).*@(if|elif|else|end|set|_).*")) {
            return CommentType.CONDITIONAL;
        }

        return CommentType.REGULAR;
    }

    /**
     * Normalizes a comment for output
     *
     * @param text Original comment text (without /* and * /)
     * @param type Type of comment
     * @return Normalized comment text
     */
    private String normalizeComment(String text, CommentType type) {
        // Remove the leading ! for KEEP comments
        if (type == CommentType.KEEP && text.startsWith("!")) {
            text = text.substring(1);
        }

        // Trim whitespace
        text = text.trim();

        // For KEEP comments, add back the ! and wrap
        if (type == CommentType.KEEP) {
            return "/*!" + text + "*/";
        }

        // For CONDITIONAL comments, wrap
        if (type == CommentType.CONDITIONAL) {
            return "/*" + text + "*/";
        }

        return text;
    }

    /**
     * Returns the list of preserved comments
     *
     * @return List of preserved comments sorted by position
     */
    public List<PreservedComment> getPreservedComments() {
        return Collections.unmodifiableList(preservedComments);
    }

    /**
     * Inserts preserved comments back into the compressed output
     *
     * This method places comments at the beginning of the output since
     * we cannot reliably map AST positions to compressed output positions.
     *
     * @param compressed The compressed JavaScript code
     * @return Compressed code with preserved comments
     */
    public String insertComments(String compressed) {
        if (preservedComments.isEmpty()) {
            return compressed;
        }

        StringBuilder result = new StringBuilder();

        // Insert all preserved comments at the beginning
        for (PreservedComment comment : preservedComments) {
            result.append(comment.getText());
            result.append("\n");
        }

        result.append(compressed);

        return result.toString();
    }

    /**
     * Clears all preserved comments
     */
    public void clear() {
        preservedComments.clear();
    }
}
