/*
 * YUI Compressor
 * http://developer.yahoo.com/yui/compressor/
 * Author: Julien Lecomte - http://www.julienlecomte.net/
 * Copyright (c) 2011 Yahoo! Inc. All rights reserved.
 * The copyrights embodied in the content of this file are licensed
 * by Yahoo! Inc. under the BSD (revised) open source license.
 */
package com.yahoo.platform.yui.compressor;

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

            // Declare the function's own name. Which scope it lands in is the whole
            // difference between a declaration and a named function expression:
            // "function f(){}" binds f in the ENCLOSING scope, while
            // "var g = function f(){}" binds f only INSIDE f, where it shadows
            // anything of that name outside and is what a recursive self-call
            // resolves to.
            //
            // Declaring neither is why a local could be munged to the name of the
            // function sitting beside it: the munger takes its free symbols from the
            // identifiers a scope knows about, an undeclared name is not one of them,
            // and so "f" was handed out as if it were free - "function f(x){...}"
            // beside six locals produced "var f=1", and the call f(...) beside it then
            // read the variable. It also left a self-call resolving to the outer
            // variable instead of the function, so reassigning that variable changed
            // what the recursion called.
            //
            // The name is declared but not munged: MungedCodeGenerator.visitFunction
            // emits it verbatim, so munging it here would rename every reference and
            // leave the declaration behind. Reserving it is what fixes the collision;
            // renaming it would be a separate change to the generator.
            Name fnName = fn.getFunctionName();
            if (fnName != null && fnName.getIdentifier() != null
                    && fnName.getIdentifier().length() > 0) {
                ScriptOrFnScope nameScope =
                        fn.getFunctionType() == FunctionNode.FUNCTION_STATEMENT
                                ? currentScope
                                : fnScope;
                reserveFunctionName(nameScope, fnName.getIdentifier());
            }

            // Declare function parameters as variables
            List<AstNode> params = fn.getParams();
            for (AstNode param : params) {
                declareParameterIdentifiers(param, fnScope);
            }

            // Visit default parameter values with the new scope. Rhino keeps
            // them in a side list (getDefaultParams()) rather than as children
            // of the parameter nodes, so the body traversal below never reaches
            // them. They are live code that runs on every call with the
            // parameter omitted, so a name read there is a real use and an
            // "eval" or "with" there is a real reason not to munge - both were
            // invisible while MungedCodeGenerator dropped defaults entirely.
            List<Object> defaultParams = fn.getDefaultParams();
            if (defaultParams != null) {
                for (int i = 1; i < defaultParams.size(); i += 2) {
                    Object value = defaultParams.get(i);
                    if (value instanceof AstNode) {
                        visitNode((AstNode) value, fnScope, braceNesting + 1);
                    }
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

            // A bare reference to "eval" - called directly ("eval(...)") or
            // merely aliased ("var e = eval") - can run code with direct-eval
            // access to every local in the enclosing scope chain at this
            // point. Munging any of those locals would break a direct eval
            // that looks one up by its original name (see the README's
            // promise that "eval" stays safe, if not optimally compressed).
            // A property access like "obj.eval" doesn't reach this branch
            // (filtered out above), which is correct: invoking eval that way
            // is an indirect eval, and indirect eval always runs in global
            // scope, so it can't see these locals regardless.
            if ("eval".equals(identifier)) {
                preventMungingUpChain(currentScope);
            }
            return;
        }

        // Handle "with" statements. Identifier resolution inside the body
        // can dynamically bind to a property of the with object instead of
        // an enclosing variable of the same name. Renaming that variable
        // would silently change which binding the runtime picks, so every
        // scope visible from here - this one and each of its ancestors -
        // is marked unsafe to munge (see the README's promise that "with"
        // stays safe, if not optimally compressed). Functions declared
        // inside the body still get their own scope munged normally: a
        // function's own locals always shadow the with object within its
        // own body, so renaming them is unaffected by the enclosing with.
        if (node instanceof WithStatement) {
            WithStatement withStmt = (WithStatement) node;
            preventMungingUpChain(currentScope);
            visitNode(withStmt.getExpression(), currentScope, braceNesting);
            visitNode(withStmt.getStatement(), currentScope, braceNesting);
            return;
        }

        // Visit all child nodes.
        //
        // Rhino's low-level Node.getFirstChild()/getNext() chain is only
        // populated for list-style containers (Block, AstRoot, ...). AST
        // nodes that keep their children in typed fields instead - function
        // call arguments, object literal property values, array literal
        // elements, if/while/for bodies, infix expression operands, and so
        // on - report zero children through that chain, so it silently
        // skips them. AstNode.visit(NodeVisitor) is what Rhino itself uses
        // to enumerate a node's children correctly regardless of how they
        // are stored, so it is used here instead. Returning true only for
        // the first callback (the node itself) descends one level; false
        // afterward stops Rhino from recursing further, since visitNode()
        // below continues the traversal itself with the correct scope and
        // brace nesting for each child.
        node.visit(new ChildVisitor(currentScope, braceNesting));
    }

    /**
     * Visits exactly the direct children of the node it is handed,
     * delegating each to {@link #visitNode}, which carries the traversal
     * deeper. See the comment at the call site for why this is needed
     * instead of the low-level Node child chain.
     */
    private class ChildVisitor implements NodeVisitor {
        private final ScriptOrFnScope scope;
        private final int braceNesting;
        private boolean isRoot = true;

        ChildVisitor(ScriptOrFnScope scope, int braceNesting) {
            this.scope = scope;
            this.braceNesting = braceNesting;
        }

        public boolean visit(AstNode node) {
            if (isRoot) {
                // First callback is always the node passed to visit(); descend into its children.
                isRoot = false;
                return true;
            }
            visitNode(node, scope, braceNesting);
            return false;
        }
    }

    /**
     * Reserves a function's own name in the scope that binds it and in every
     * enclosing scope that gets munged.
     *
     * <p>Reserving only the binding scope is not enough, because the name is
     * emitted verbatim while the variables around it are renamed. A scope picks
     * its free symbols by excluding what its own and its ANCESTORS' scopes use -
     * it never looks down - so an outer variable could still be munged to this
     * function's name, and inside the function's body that name resolves to the
     * function, not to the variable. "function outer(){ var longName=5; var g =
     * function a(){ return longName; }; }" munged longName to "a" and the body
     * then returned the function itself.
     *
     * <p>The global scope is reserved only when the function is itself global.
     * It is never munged, so nothing there can collide; but every scope in the
     * file walks up to it, so a reservation there would take the name out of
     * every unrelated scope as well.
     */
    private static void reserveFunctionName(ScriptOrFnScope owner, String name) {
        for (ScriptOrFnScope scope = owner; scope != null; scope = scope.getParentScope()) {
            if (scope != owner && scope.getParentScope() == null) {
                break;
            }
            scope.declareIdentifier(name).preventMunging();
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
                // Skip null or empty elements (e.g., [a, , b])
                if (element != null && !(element instanceof EmptyExpression)) {
                    declareParameterIdentifiers(element, scope);
                }
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
     * Marks {@code scope} and every scope enclosing it as unsafe to munge.
     * Used for "eval" and "with", both of which can bind to a local
     * declared in any scope visible from the point of use, not just the
     * innermost one. Walking all the way to the global scope is harmless:
     * {@link ScriptOrFnScope#preventMunging()} is already a no-op there,
     * since global symbols are never munged in the first place.
     */
    private void preventMungingUpChain(ScriptOrFnScope scope) {
        while (scope != null) {
            scope.preventMunging();
            scope = scope.getParentScope();
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
