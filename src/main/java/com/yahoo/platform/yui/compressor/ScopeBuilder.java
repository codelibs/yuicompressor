/*
 * YUI Compressor
 * http://developer.yahoo.com/yui/compressor/
 * Author: Julien Lecomte - http://www.julienlecomte.net/
 * Copyright (c) 2011 Yahoo! Inc. All rights reserved.
 * The copyrights embodied in the content of this file are licensed
 * by Yahoo! Inc. under the BSD (revised) open source license.
 */
package com.yahoo.platform.yui.compressor;

import org.mozilla.javascript.Node;
import org.mozilla.javascript.ast.*;
import java.util.*;

/**
 * Builds scope tree from Rhino 1.8.0 AST for variable obfuscation
 */
public class ScopeBuilder {

    private ScriptOrFnScope globalScope;
    private Map<AstNode, ScriptOrFnScope> scopeMap = new HashMap<>();

    public ScopeBuilder() {
        this.globalScope = new ScriptOrFnScope(0, null);
    }

    /**
     * Build scope tree from AST
     */
    public ScriptOrFnScope buildScopeTree(AstRoot root) {
        scopeMap.put(root, globalScope);
        visitNode(root, globalScope, 0);
        return globalScope;
    }

    private void visitNode(AstNode node, ScriptOrFnScope currentScope, int braceNesting) {
        if (node == null) {
            return;
        }

        // Handle function declarations and expressions
        if (node instanceof FunctionNode) {
            FunctionNode fn = (FunctionNode) node;

            // Create new scope for this function
            ScriptOrFnScope fnScope = new ScriptOrFnScope(braceNesting + 1, currentScope);
            scopeMap.put(fn, fnScope);

            // Declare function parameters as variables
            List<AstNode> params = fn.getParams();
            for (AstNode param : params) {
                if (param instanceof Name) {
                    String paramName = ((Name) param).getIdentifier();
                    fnScope.declareIdentifier(paramName);
                }
            }

            // Visit function body with new scope
            AstNode body = fn.getBody();
            if (body != null) {
                visitNode(body, fnScope, braceNesting + 1);
            }
            return;
        }

        // Handle variable declarations
        if (node instanceof VariableDeclaration) {
            VariableDeclaration varDecl = (VariableDeclaration) node;
            List<VariableInitializer> variables = varDecl.getVariables();

            for (VariableInitializer vi : variables) {
                AstNode target = vi.getTarget();
                if (target instanceof Name) {
                    String varName = ((Name) target).getIdentifier();
                    currentScope.declareIdentifier(varName);
                }

                // Visit initializer
                AstNode initializer = vi.getInitializer();
                if (initializer != null) {
                    visitNode(initializer, currentScope, braceNesting);
                }
            }
            return;
        }

        // Handle name references (variable usage)
        if (node instanceof Name) {
            Name name = (Name) node;
            String identifier = name.getIdentifier();

            // Don't mark property names as identifiers
            AstNode parent = name.getParent();
            if (parent instanceof PropertyGet) {
                PropertyGet pg = (PropertyGet) parent;
                if (pg.getProperty() == name) {
                    return; // This is a property access, not a variable reference
                }
            }

            // Mark the identifier as referenced
            JavaScriptIdentifier id = findIdentifier(identifier, currentScope);
            if (id != null) {
                id.incrementRefcount();
            }
            return;
        }

        // Visit all child nodes
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof AstNode) {
                visitNode((AstNode) child, currentScope, braceNesting);
            }
        }
    }

    /**
     * Find identifier in current scope or parent scopes
     */
    private JavaScriptIdentifier findIdentifier(String name, ScriptOrFnScope scope) {
        while (scope != null) {
            JavaScriptIdentifier id = scope.getIdentifier(name);
            if (id != null) {
                return id;
            }
            scope = scope.getParentScope();
        }
        return null;
    }

    /**
     * Get the scope for a given AST node
     */
    public ScriptOrFnScope getScopeForNode(AstNode node) {
        return scopeMap.get(node);
    }

    public ScriptOrFnScope getGlobalScope() {
        return globalScope;
    }
}
