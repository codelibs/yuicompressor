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
 * Builds scope tree from Rhino 1.8.0 AST for variable obfuscation.
 * Supports ES6+ features including arrow functions, for-of loops, and destructuring.
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

        // Handle function declarations and expressions (including arrow functions)
        if (node instanceof FunctionNode) {
            FunctionNode fn = (FunctionNode) node;

            // Create new scope for this function
            ScriptOrFnScope fnScope = new ScriptOrFnScope(braceNesting + 1, currentScope);
            scopeMap.put(fn, fnScope);

            // Declare function parameters as variables
            List<AstNode> params = fn.getParams();
            for (AstNode param : params) {
                declareParameterIdentifiers(param, fnScope);
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
                // Handle both simple names and destructuring patterns
                declareVariableIdentifiers(target, currentScope);

                // Visit initializer
                AstNode initializer = vi.getInitializer();
                if (initializer != null) {
                    visitNode(initializer, currentScope, braceNesting);
                }
            }
            return;
        }

        // Handle for-in and for-of loops
        if (node instanceof ForInLoop) {
            ForInLoop forIn = (ForInLoop) node;

            // The iterator may declare variables (e.g., "for (let x of arr)")
            AstNode iterator = forIn.getIterator();
            if (iterator instanceof VariableDeclaration) {
                VariableDeclaration varDecl = (VariableDeclaration) iterator;
                for (VariableInitializer vi : varDecl.getVariables()) {
                    declareVariableIdentifiers(vi.getTarget(), currentScope);
                }
            } else {
                visitNode(iterator, currentScope, braceNesting);
            }

            // Visit the iterated object
            visitNode(forIn.getIteratedObject(), currentScope, braceNesting);

            // Visit the loop body
            visitNode(forIn.getBody(), currentScope, braceNesting);
            return;
        }

        // Handle try-catch blocks (catch parameter creates a new binding)
        if (node instanceof TryStatement) {
            TryStatement tryStmt = (TryStatement) node;

            // Visit try block
            visitNode(tryStmt.getTryBlock(), currentScope, braceNesting);

            // Handle catch clauses - catch variable is scoped to catch block
            for (CatchClause clause : tryStmt.getCatchClauses()) {
                Name varName = clause.getVarName();
                if (varName != null) {
                    // Declare catch variable in current scope for munging
                    currentScope.declareIdentifier(varName.getIdentifier());
                }
                visitNode(clause.getBody(), currentScope, braceNesting);
            }

            // Visit finally block
            AstNode finallyBlock = tryStmt.getFinallyBlock();
            if (finallyBlock != null) {
                visitNode(finallyBlock, currentScope, braceNesting);
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
                AstNode property = pg.getProperty();
                if (property instanceof Name &&
                    ((Name) property).getIdentifier().equals(identifier) &&
                    property.getAbsolutePosition() == name.getAbsolutePosition()) {
                    return; // This is a property access, not a variable reference
                }
            }

            // Don't mark object property keys as variable references
            if (parent instanceof ObjectProperty) {
                ObjectProperty prop = (ObjectProperty) parent;
                if (prop.getLeft() == name && !prop.isShorthand()) {
                    return; // This is a property key, not a variable reference
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
     * Declare identifiers from a parameter (handles destructuring)
     */
    private void declareParameterIdentifiers(AstNode param, ScriptOrFnScope scope) {
        if (param instanceof Name) {
            scope.declareIdentifier(((Name) param).getIdentifier());
        } else if (param instanceof ArrayLiteral) {
            // Array destructuring pattern
            ArrayLiteral arr = (ArrayLiteral) param;
            for (AstNode element : arr.getElements()) {
                declareParameterIdentifiers(element, scope);
            }
        } else if (param instanceof ObjectLiteral) {
            // Object destructuring pattern
            ObjectLiteral obj = (ObjectLiteral) param;
            for (ObjectProperty prop : obj.getElements()) {
                // The value (right side) contains the binding
                declareParameterIdentifiers(prop.getRight(), scope);
            }
        } else if (param instanceof Assignment) {
            // Default parameter value
            Assignment assign = (Assignment) param;
            declareParameterIdentifiers(assign.getLeft(), scope);
        }
        // Note: Rest parameters (...args) are handled as Name nodes
    }

    /**
     * Declare identifiers from a variable declaration target (handles destructuring)
     */
    private void declareVariableIdentifiers(AstNode target, ScriptOrFnScope scope) {
        if (target instanceof Name) {
            scope.declareIdentifier(((Name) target).getIdentifier());
        } else if (target instanceof ArrayLiteral) {
            // Array destructuring: const [a, b] = arr
            ArrayLiteral arr = (ArrayLiteral) target;
            for (AstNode element : arr.getElements()) {
                if (element != null && !(element instanceof EmptyExpression)) {
                    declareVariableIdentifiers(element, scope);
                }
            }
        } else if (target instanceof ObjectLiteral) {
            // Object destructuring: const {a, b} = obj
            ObjectLiteral obj = (ObjectLiteral) target;
            for (ObjectProperty prop : obj.getElements()) {
                if (prop.isShorthand()) {
                    // Shorthand: {a} is both key and binding
                    if (prop.getLeft() instanceof Name) {
                        scope.declareIdentifier(((Name) prop.getLeft()).getIdentifier());
                    }
                } else {
                    // Regular: {a: b} - b is the binding
                    declareVariableIdentifiers(prop.getRight(), scope);
                }
            }
        } else if (target instanceof Assignment) {
            // Default value: const [a = 1] = arr
            Assignment assign = (Assignment) target;
            declareVariableIdentifiers(assign.getLeft(), scope);
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
