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
import org.mozilla.javascript.Token;
import org.mozilla.javascript.ast.*;
import java.util.*;

/**
 * Generates minified JavaScript code with munged variable names
 */
public class MungedCodeGenerator {

    private ScopeBuilder scopeBuilder;
    private boolean munge;
    private StringBuilder output;

    public MungedCodeGenerator(ScopeBuilder scopeBuilder, boolean munge) {
        this.scopeBuilder = scopeBuilder;
        this.munge = munge;
        this.output = new StringBuilder();
    }

    public String generate(AstRoot root) {
        output.setLength(0);
        visitNode(root);
        return output.toString();
    }

    private void visitNode(AstNode node) {
        if (node == null) {
            return;
        }

        int type = node.getType();

        switch (type) {
            case Token.SCRIPT:
                visitScript((AstRoot) node);
                break;
            case Token.FUNCTION:
                visitFunction((FunctionNode) node);
                break;
            case Token.NAME:
                visitName((Name) node);
                break;
            case Token.VAR:
            case Token.LET:
            case Token.CONST:
                visitVariableDeclaration((VariableDeclaration) node);
                break;
            case Token.EXPR_RESULT:
            case Token.EXPR_VOID:
                visitExpressionStatement((ExpressionStatement) node);
                break;
            case Token.RETURN:
                visitReturnStatement((ReturnStatement) node);
                break;
            case Token.BLOCK:
                visitBlock((Block) node);
                break;
            case Token.NUMBER:
                output.append(((NumberLiteral) node).getValue());
                break;
            case Token.STRING:
                visitStringLiteral((StringLiteral) node);
                break;
            case Token.ASSIGN:
                visitInfixExpression((InfixExpression) node, "=");
                break;
            case Token.ADD:
                visitInfixExpression((InfixExpression) node, "+");
                break;
            case Token.SUB:
                visitInfixExpression((InfixExpression) node, "-");
                break;
            case Token.MUL:
                visitInfixExpression((InfixExpression) node, "*");
                break;
            case Token.DIV:
                visitInfixExpression((InfixExpression) node, "/");
                break;
            case Token.CALL:
                visitFunctionCall((FunctionCall) node);
                break;
            case Token.GETPROP:
                visitPropertyGet((PropertyGet) node);
                break;
            case Token.OBJECTLIT:
                visitObjectLiteral((ObjectLiteral) node);
                break;
            case Token.ARRAYLIT:
                visitArrayLiteral((ArrayLiteral) node);
                break;
            default:
                // Fallback: use toSource() for unsupported nodes
                output.append(node.toSource());
                break;
        }
    }

    private void visitScript(AstRoot script) {
        for (Node child : script) {
            if (child instanceof AstNode) {
                visitNode((AstNode) child);
                if (needsSemicolon((AstNode) child)) {
                    output.append(";");
                }
            }
        }
    }

    private void visitFunction(FunctionNode fn) {
        output.append("function");

        // Function name
        Name fnName = fn.getFunctionName();
        if (fnName != null) {
            output.append(" ");
            output.append(fnName.getIdentifier());
        }

        output.append("(");

        // Parameters
        List<AstNode> params = fn.getParams();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) output.append(",");
            AstNode param = params.get(i);
            if (param instanceof Name) {
                String paramName = ((Name) param).getIdentifier();
                output.append(getMungedName(paramName, fn));
            }
        }

        output.append(")");

        // Body
        AstNode body = fn.getBody();
        if (body != null) {
            visitNode(body);
        }
    }

    private void visitName(Name name) {
        String identifier = name.getIdentifier();

        // Check if this is a property access
        AstNode parent = name.getParent();
        if (parent instanceof PropertyGet) {
            PropertyGet pg = (PropertyGet) parent;
            if (pg.getProperty() == name) {
                // This is a property name, don't munge it
                output.append(identifier);
                return;
            }
        }

        // Munge the variable name
        output.append(getMungedName(identifier, name));
    }

    private void visitVariableDeclaration(VariableDeclaration varDecl) {
        int declType = varDecl.getType();
        if (declType == Token.LET) {
            output.append("let ");
        } else if (declType == Token.CONST) {
            output.append("const ");
        } else {
            output.append("var ");
        }

        List<VariableInitializer> variables = varDecl.getVariables();
        for (int i = 0; i < variables.size(); i++) {
            if (i > 0) output.append(",");

            VariableInitializer vi = variables.get(i);
            AstNode target = vi.getTarget();

            if (target instanceof Name) {
                String varName = ((Name) target).getIdentifier();
                output.append(getMungedName(varName, varDecl));
            }

            AstNode initializer = vi.getInitializer();
            if (initializer != null) {
                output.append("=");
                visitNode(initializer);
            }
        }
    }

    private void visitExpressionStatement(ExpressionStatement stmt) {
        visitNode(stmt.getExpression());
    }

    private void visitReturnStatement(ReturnStatement ret) {
        output.append("return");
        AstNode value = ret.getReturnValue();
        if (value != null) {
            output.append(" ");
            visitNode(value);
        }
    }

    private void visitBlock(Block block) {
        output.append("{");
        for (Node child : block) {
            if (child instanceof AstNode) {
                visitNode((AstNode) child);
                if (needsSemicolon((AstNode) child)) {
                    output.append(";");
                }
            }
        }
        output.append("}");
    }

    private void visitStringLiteral(StringLiteral str) {
        output.append(str.toSource()); // Keep quotes intact
    }

    private void visitInfixExpression(InfixExpression expr, String operator) {
        visitNode(expr.getLeft());
        output.append(operator);
        visitNode(expr.getRight());
    }

    private void visitFunctionCall(FunctionCall call) {
        visitNode(call.getTarget());
        output.append("(");

        List<AstNode> args = call.getArguments();
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) output.append(",");
            visitNode(args.get(i));
        }

        output.append(")");
    }

    private void visitPropertyGet(PropertyGet pg) {
        visitNode(pg.getTarget());
        output.append(".");
        output.append(pg.getProperty().toSource());
    }

    private void visitObjectLiteral(ObjectLiteral obj) {
        output.append("{");
        List<ObjectProperty> props = obj.getElements();
        for (int i = 0; i < props.size(); i++) {
            if (i > 0) output.append(",");
            ObjectProperty prop = props.get(i);
            visitNode(prop.getLeft());
            output.append(":");
            visitNode(prop.getRight());
        }
        output.append("}");
    }

    private void visitArrayLiteral(ArrayLiteral arr) {
        output.append("[");
        List<AstNode> elements = arr.getElements();
        for (int i = 0; i < elements.size(); i++) {
            if (i > 0) output.append(",");
            visitNode(elements.get(i));
        }
        output.append("]");
    }

    private boolean needsSemicolon(AstNode node) {
        int type = node.getType();
        return type != Token.FUNCTION && type != Token.BLOCK;
    }

    /**
     * Get the munged name for a variable, or the original name if munging is disabled
     */
    private String getMungedName(String originalName, AstNode context) {
        if (!munge) {
            return originalName;
        }

        // Find the scope containing this variable
        ScriptOrFnScope scope = findScopeForVariable(originalName, context);
        if (scope != null) {
            JavaScriptIdentifier id = scope.getIdentifier(originalName);
            if (id != null) {
                String mungedValue = id.getMungedValue();
                if (mungedValue != null) {
                    return mungedValue;
                }
            }
        }

        return originalName;
    }

    /**
     * Find the scope that declares a given variable
     */
    private ScriptOrFnScope findScopeForVariable(String name, AstNode context) {
        // Walk up the AST to find the enclosing function or script
        AstNode current = context;
        while (current != null) {
            if (current instanceof FunctionNode || current instanceof AstRoot) {
                ScriptOrFnScope scope = scopeBuilder.getScopeForNode(current);
                if (scope != null) {
                    // Search this scope and parent scopes
                    ScriptOrFnScope searchScope = scope;
                    while (searchScope != null) {
                        if (searchScope.getIdentifier(name) != null) {
                            return searchScope;
                        }
                        searchScope = searchScope.getParentScope();
                    }
                }
            }
            current = current.getParent();
        }
        return scopeBuilder.getGlobalScope();
    }
}
