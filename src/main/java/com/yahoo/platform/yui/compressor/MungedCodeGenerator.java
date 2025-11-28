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
 * Generates minified JavaScript code with munged variable names.
 * Supports ES6+ syntax including arrow functions, template literals, and classes.
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
            // Core structure
            case Token.SCRIPT:
                visitScript((AstRoot) node);
                break;
            case Token.FUNCTION:
                visitFunction((FunctionNode) node);
                break;
            case Token.BLOCK:
                visitBlock((Block) node);
                break;

            // Variables and identifiers
            case Token.NAME:
                visitName((Name) node);
                break;
            case Token.VAR:
            case Token.LET:
            case Token.CONST:
                visitVariableDeclaration((VariableDeclaration) node);
                break;

            // Statements
            case Token.EXPR_RESULT:
            case Token.EXPR_VOID:
                visitExpressionStatement((ExpressionStatement) node);
                break;
            case Token.RETURN:
                visitReturnStatement((ReturnStatement) node);
                break;
            case Token.IF:
                visitIfStatement((IfStatement) node);
                break;
            case Token.FOR:
                visitForLoop((ForLoop) node);
                break;
            case Token.WHILE:
                visitWhileLoop((WhileLoop) node);
                break;
            case Token.DO:
                visitDoLoop((DoLoop) node);
                break;
            case Token.SWITCH:
                visitSwitchStatement((SwitchStatement) node);
                break;
            case Token.CASE:
                visitSwitchCase((SwitchCase) node);
                break;
            case Token.BREAK:
                visitBreakStatement((BreakStatement) node);
                break;
            case Token.CONTINUE:
                visitContinueStatement((ContinueStatement) node);
                break;
            case Token.THROW:
                visitThrowStatement((ThrowStatement) node);
                break;
            case Token.TRY:
                visitTryStatement((TryStatement) node);
                break;
            case Token.EMPTY:
                // Empty statement, nothing to output
                break;
            case Token.LABEL:
                visitLabeledStatement((LabeledStatement) node);
                break;
            case Token.WITH:
                visitWithStatement((WithStatement) node);
                break;

            // Literals
            case Token.NUMBER:
                output.append(((NumberLiteral) node).getValue());
                break;
            case Token.STRING:
                visitStringLiteral((StringLiteral) node);
                break;
            case Token.TRUE:
                output.append("true");
                break;
            case Token.FALSE:
                output.append("false");
                break;
            case Token.NULL:
                output.append("null");
                break;
            case Token.THIS:
                output.append("this");
                break;
            case Token.REGEXP:
                visitRegExpLiteral((RegExpLiteral) node);
                break;
            case Token.OBJECTLIT:
                visitObjectLiteral((ObjectLiteral) node);
                break;
            case Token.ARRAYLIT:
                visitArrayLiteral((ArrayLiteral) node);
                break;

            // Assignment operators
            case Token.ASSIGN:
                visitInfixExpression((InfixExpression) node, "=");
                break;
            case Token.ASSIGN_ADD:
                visitInfixExpression((InfixExpression) node, "+=");
                break;
            case Token.ASSIGN_SUB:
                visitInfixExpression((InfixExpression) node, "-=");
                break;
            case Token.ASSIGN_MUL:
                visitInfixExpression((InfixExpression) node, "*=");
                break;
            case Token.ASSIGN_DIV:
                visitInfixExpression((InfixExpression) node, "/=");
                break;
            case Token.ASSIGN_MOD:
                visitInfixExpression((InfixExpression) node, "%=");
                break;
            case Token.ASSIGN_BITAND:
                visitInfixExpression((InfixExpression) node, "&=");
                break;
            case Token.ASSIGN_BITOR:
                visitInfixExpression((InfixExpression) node, "|=");
                break;
            case Token.ASSIGN_BITXOR:
                visitInfixExpression((InfixExpression) node, "^=");
                break;
            case Token.ASSIGN_LSH:
                visitInfixExpression((InfixExpression) node, "<<=");
                break;
            case Token.ASSIGN_RSH:
                visitInfixExpression((InfixExpression) node, ">>=");
                break;
            case Token.ASSIGN_URSH:
                visitInfixExpression((InfixExpression) node, ">>>=");
                break;

            // Arithmetic operators
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
            case Token.MOD:
                visitInfixExpression((InfixExpression) node, "%");
                break;
            case Token.EXP:
                visitInfixExpression((InfixExpression) node, "**");
                break;

            // Comparison operators
            case Token.EQ:
                visitInfixExpression((InfixExpression) node, "==");
                break;
            case Token.NE:
                visitInfixExpression((InfixExpression) node, "!=");
                break;
            case Token.SHEQ:
                visitInfixExpression((InfixExpression) node, "===");
                break;
            case Token.SHNE:
                visitInfixExpression((InfixExpression) node, "!==");
                break;
            case Token.LT:
                visitInfixExpression((InfixExpression) node, "<");
                break;
            case Token.LE:
                visitInfixExpression((InfixExpression) node, "<=");
                break;
            case Token.GT:
                visitInfixExpression((InfixExpression) node, ">");
                break;
            case Token.GE:
                visitInfixExpression((InfixExpression) node, ">=");
                break;

            // Logical operators
            case Token.AND:
                visitInfixExpression((InfixExpression) node, "&&");
                break;
            case Token.OR:
                visitInfixExpression((InfixExpression) node, "||");
                break;
            case Token.NOT:
                visitUnaryExpression((UnaryExpression) node, "!");
                break;

            // Bitwise operators
            case Token.BITAND:
                visitInfixExpression((InfixExpression) node, "&");
                break;
            case Token.BITOR:
                visitInfixExpression((InfixExpression) node, "|");
                break;
            case Token.BITXOR:
                visitInfixExpression((InfixExpression) node, "^");
                break;
            case Token.BITNOT:
                visitUnaryExpression((UnaryExpression) node, "~");
                break;
            case Token.LSH:
                visitInfixExpression((InfixExpression) node, "<<");
                break;
            case Token.RSH:
                visitInfixExpression((InfixExpression) node, ">>");
                break;
            case Token.URSH:
                visitInfixExpression((InfixExpression) node, ">>>");
                break;

            // Unary operators
            case Token.POS:
                visitUnaryExpression((UnaryExpression) node, "+");
                break;
            case Token.NEG:
                visitUnaryExpression((UnaryExpression) node, "-");
                break;
            case Token.TYPEOF:
                visitKeywordUnary((UnaryExpression) node, "typeof ");
                break;
            case Token.VOID:
                visitKeywordUnary((UnaryExpression) node, "void ");
                break;
            case Token.DELPROP:
                visitKeywordUnary((UnaryExpression) node, "delete ");
                break;

            // Increment/Decrement
            case Token.INC:
            case Token.DEC:
                visitUpdateExpression((UpdateExpression) node);
                break;

            // Other operators
            case Token.COMMA:
                visitInfixExpression((InfixExpression) node, ",");
                break;
            case Token.HOOK:
                visitConditionalExpression((ConditionalExpression) node);
                break;
            case Token.IN:
                visitInfixExpression((InfixExpression) node, " in ");
                break;
            case Token.INSTANCEOF:
                visitInfixExpression((InfixExpression) node, " instanceof ");
                break;

            // Member access
            case Token.CALL:
                visitFunctionCall((FunctionCall) node);
                break;
            case Token.NEW:
                visitNewExpression((NewExpression) node);
                break;
            case Token.GETPROP:
                visitPropertyGet((PropertyGet) node);
                break;
            case Token.GETELEM:
                visitElementGet((ElementGet) node);
                break;

            // ES6+ features
            case Token.ARROW:
                visitArrowFunction((FunctionNode) node);
                break;
            case Token.TEMPLATE_LITERAL:
                visitTemplateLiteral((TemplateLiteral) node);
                break;
            case Token.TAGGED_TEMPLATE_LITERAL:
                visitTaggedTemplateLiteral((TaggedTemplateLiteral) node);
                break;

            // Parenthesized expression
            case Token.LP:
                visitParenthesizedExpression((ParenthesizedExpression) node);
                break;

            // For-in and For-of loops
            case 162: // Token.FOR_IN (may vary by Rhino version)
                visitForInLoop((ForInLoop) node);
                break;

            // Spread and yield
            case Token.YIELD:
                visitYield((Yield) node);
                break;

            default:
                // Fallback: use toSource() for unsupported nodes
                if (System.getProperty("yuicompressor.debug") != null) {
                    System.err.println("Warning: Using toSource() for unsupported node type: " +
                        type + " (" + node.getClass().getSimpleName() + ")");
                }
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
        // Check if this is an arrow function
        if (fn.getFunctionType() == FunctionNode.ARROW_FUNCTION) {
            visitArrowFunction(fn);
            return;
        }

        // Generator function
        if (fn.isGenerator()) {
            output.append("function*");
        } else {
            output.append("function");
        }

        // Function name
        Name fnName = fn.getFunctionName();
        if (fnName != null) {
            output.append(" ");
            output.append(fnName.getIdentifier());
        }

        output.append("(");

        // Parameters
        List<AstNode> params = fn.getParams();
        visitParameterList(params, fn);

        output.append(")");

        // Body
        AstNode body = fn.getBody();
        if (body != null) {
            visitNode(body);
        }
    }

    private void visitArrowFunction(FunctionNode arrow) {
        List<AstNode> params = arrow.getParams();

        // Parameters
        if (params.size() == 1 && params.get(0) instanceof Name) {
            // Single parameter without parentheses (may need them for munging consistency)
            String paramName = ((Name) params.get(0)).getIdentifier();
            output.append(getMungedName(paramName, arrow));
        } else {
            output.append("(");
            visitParameterList(params, arrow);
            output.append(")");
        }

        output.append("=>");

        // Body
        AstNode body = arrow.getBody();
        if (body != null) {
            // Check if body is a block or a single expression
            if (body instanceof Block) {
                visitNode(body);
            } else {
                // Single expression - no braces needed
                visitNode(body);
            }
        }
    }

    private void visitParameterList(List<AstNode> params, FunctionNode fn) {
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) output.append(",");
            AstNode param = params.get(i);
            if (param instanceof Name) {
                String paramName = ((Name) param).getIdentifier();
                output.append(getMungedName(paramName, fn));
            } else {
                // Complex parameter (destructuring, default value, rest)
                visitNode(param);
            }
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

        // Check if this is an object property key
        if (parent instanceof ObjectProperty) {
            ObjectProperty prop = (ObjectProperty) parent;
            if (prop.getLeft() == name && !prop.isShorthand()) {
                // This is a property key, don't munge it
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
            } else {
                // Destructuring pattern
                visitNode(target);
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

    private void visitIfStatement(IfStatement ifStmt) {
        output.append("if(");
        visitNode(ifStmt.getCondition());
        output.append(")");

        AstNode thenPart = ifStmt.getThenPart();
        boolean needsBraces = !(thenPart instanceof Block);
        if (needsBraces) output.append("{");
        visitNode(thenPart);
        if (needsBraces && needsSemicolon(thenPart)) output.append(";");
        if (needsBraces) output.append("}");

        AstNode elsePart = ifStmt.getElsePart();
        if (elsePart != null) {
            output.append("else");
            if (elsePart instanceof IfStatement) {
                output.append(" ");
                visitNode(elsePart);
            } else {
                boolean elseNeedsBraces = !(elsePart instanceof Block);
                if (elseNeedsBraces) output.append("{");
                visitNode(elsePart);
                if (elseNeedsBraces && needsSemicolon(elsePart)) output.append(";");
                if (elseNeedsBraces) output.append("}");
            }
        }
    }

    private void visitForLoop(ForLoop forLoop) {
        output.append("for(");

        AstNode initializer = forLoop.getInitializer();
        if (initializer != null) {
            visitNode(initializer);
        }
        output.append(";");

        AstNode condition = forLoop.getCondition();
        if (condition != null) {
            visitNode(condition);
        }
        output.append(";");

        AstNode increment = forLoop.getIncrement();
        if (increment != null) {
            visitNode(increment);
        }
        output.append(")");

        AstNode body = forLoop.getBody();
        visitLoopBody(body);
    }

    private void visitForInLoop(ForInLoop forIn) {
        output.append("for(");
        visitNode(forIn.getIterator());
        if (forIn.isForOf()) {
            output.append(" of ");
        } else if (forIn.isForEach()) {
            output.append(" each in ");
        } else {
            output.append(" in ");
        }
        visitNode(forIn.getIteratedObject());
        output.append(")");

        AstNode body = forIn.getBody();
        visitLoopBody(body);
    }

    private void visitWhileLoop(WhileLoop whileLoop) {
        output.append("while(");
        visitNode(whileLoop.getCondition());
        output.append(")");

        AstNode body = whileLoop.getBody();
        visitLoopBody(body);
    }

    private void visitDoLoop(DoLoop doLoop) {
        output.append("do");

        AstNode body = doLoop.getBody();
        if (body instanceof Block) {
            visitNode(body);
        } else {
            output.append("{");
            visitNode(body);
            if (needsSemicolon(body)) output.append(";");
            output.append("}");
        }

        output.append("while(");
        visitNode(doLoop.getCondition());
        output.append(")");
    }

    private void visitLoopBody(AstNode body) {
        if (body instanceof Block) {
            visitNode(body);
        } else if (body instanceof EmptyStatement) {
            output.append(";");
        } else {
            output.append("{");
            visitNode(body);
            if (needsSemicolon(body)) output.append(";");
            output.append("}");
        }
    }

    private void visitSwitchStatement(SwitchStatement switchStmt) {
        output.append("switch(");
        visitNode(switchStmt.getExpression());
        output.append("){");

        for (SwitchCase caseNode : switchStmt.getCases()) {
            visitSwitchCase(caseNode);
        }

        output.append("}");
    }

    private void visitSwitchCase(SwitchCase caseNode) {
        AstNode expression = caseNode.getExpression();
        if (expression == null) {
            output.append("default:");
        } else {
            output.append("case ");
            visitNode(expression);
            output.append(":");
        }

        List<AstNode> statements = caseNode.getStatements();
        if (statements != null) {
            for (AstNode stmt : statements) {
                visitNode(stmt);
                if (needsSemicolon(stmt)) {
                    output.append(";");
                }
            }
        }
    }

    private void visitBreakStatement(BreakStatement breakStmt) {
        output.append("break");
        Name label = breakStmt.getBreakLabel();
        if (label != null) {
            output.append(" ");
            output.append(label.getIdentifier());
        }
    }

    private void visitContinueStatement(ContinueStatement contStmt) {
        output.append("continue");
        Name label = contStmt.getLabel();
        if (label != null) {
            output.append(" ");
            output.append(label.getIdentifier());
        }
    }

    private void visitThrowStatement(ThrowStatement throwStmt) {
        output.append("throw ");
        visitNode(throwStmt.getExpression());
    }

    private void visitTryStatement(TryStatement tryStmt) {
        output.append("try");
        visitNode(tryStmt.getTryBlock());

        for (CatchClause clause : tryStmt.getCatchClauses()) {
            output.append("catch(");
            Name varName = clause.getVarName();
            if (varName != null) {
                output.append(getMungedName(varName.getIdentifier(), clause));
            }
            output.append(")");
            visitNode(clause.getBody());
        }

        AstNode finallyBlock = tryStmt.getFinallyBlock();
        if (finallyBlock != null) {
            output.append("finally");
            visitNode(finallyBlock);
        }
    }

    private void visitLabeledStatement(LabeledStatement labeled) {
        for (Label label : labeled.getLabels()) {
            output.append(label.getName());
            output.append(":");
        }
        visitNode(labeled.getStatement());
    }

    private void visitWithStatement(WithStatement withStmt) {
        output.append("with(");
        visitNode(withStmt.getExpression());
        output.append(")");
        visitNode(withStmt.getStatement());
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
        String value = str.getValue();
        char quoteChar = str.getQuoteCharacter();

        if (quoteChar != '"' && quoteChar != '\'') {
            quoteChar = '"';
        }

        output.append(quoteChar);

        if (value != null) {
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '\\':
                        output.append("\\\\");
                        break;
                    case '\n':
                        output.append("\\n");
                        break;
                    case '\r':
                        output.append("\\r");
                        break;
                    case '\t':
                        output.append("\\t");
                        break;
                    case '\b':
                        output.append("\\b");
                        break;
                    case '\f':
                        output.append("\\f");
                        break;
                    case '"':
                        if (quoteChar == '"') {
                            output.append("\\\"");
                        } else {
                            output.append(c);
                        }
                        break;
                    case '\'':
                        if (quoteChar == '\'') {
                            output.append("\\'");
                        } else {
                            output.append(c);
                        }
                        break;
                    default:
                        output.append(c);
                        break;
                }
            }
        }

        output.append(quoteChar);
    }

    private void visitRegExpLiteral(RegExpLiteral regexp) {
        output.append("/");
        output.append(regexp.getValue());
        output.append("/");
        String flags = regexp.getFlags();
        if (flags != null) {
            output.append(flags);
        }
    }

    private void visitTemplateLiteral(TemplateLiteral template) {
        output.append('`');
        List<AstNode> elements = template.getElements();
        for (AstNode element : elements) {
            if (element instanceof TemplateCharacters) {
                output.append(((TemplateCharacters) element).getValue());
            } else {
                output.append("${");
                visitNode(element);
                output.append("}");
            }
        }
        output.append('`');
    }

    private void visitTaggedTemplateLiteral(TaggedTemplateLiteral tagged) {
        visitNode(tagged.getTarget());
        visitNode(tagged.getTemplateLiteral());
    }

    private void visitInfixExpression(InfixExpression expr, String operator) {
        AstNode left = expr.getLeft();
        AstNode right = expr.getRight();

        // Add parentheses if needed for precedence
        boolean needsLeftParen = needsParentheses(left, expr, true);
        boolean needsRightParen = needsParentheses(right, expr, false);

        if (needsLeftParen) output.append("(");
        visitNode(left);
        if (needsLeftParen) output.append(")");

        output.append(operator);

        if (needsRightParen) output.append("(");
        visitNode(right);
        if (needsRightParen) output.append(")");
    }

    private void visitUnaryExpression(UnaryExpression expr, String operator) {
        output.append(operator);
        AstNode operand = expr.getOperand();
        boolean needsParen = operand instanceof InfixExpression ||
                            operand instanceof ConditionalExpression;
        if (needsParen) output.append("(");
        visitNode(operand);
        if (needsParen) output.append(")");
    }

    private void visitKeywordUnary(UnaryExpression expr, String keyword) {
        output.append(keyword);
        visitNode(expr.getOperand());
    }

    private void visitUpdateExpression(UpdateExpression expr) {
        String operator = expr.getType() == Token.INC ? "++" : "--";
        AstNode operand = expr.getOperand();

        if (expr.isPrefix()) {
            output.append(operator);
            visitNode(operand);
        } else {
            visitNode(operand);
            output.append(operator);
        }
    }

    private void visitConditionalExpression(ConditionalExpression expr) {
        visitNode(expr.getTestExpression());
        output.append("?");
        visitNode(expr.getTrueExpression());
        output.append(":");
        visitNode(expr.getFalseExpression());
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

    private void visitNewExpression(NewExpression newExpr) {
        output.append("new ");
        visitNode(newExpr.getTarget());

        List<AstNode> args = newExpr.getArguments();
        if (args != null && !args.isEmpty()) {
            output.append("(");
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) output.append(",");
                visitNode(args.get(i));
            }
            output.append(")");
        } else if (newExpr.getInitializer() == null) {
            // Include empty parentheses for clarity
            output.append("()");
        }

        AstNode initializer = newExpr.getInitializer();
        if (initializer != null) {
            visitNode(initializer);
        }
    }

    private void visitPropertyGet(PropertyGet pg) {
        visitNode(pg.getTarget());
        output.append(".");
        AstNode property = pg.getProperty();
        if (property instanceof Name) {
            output.append(((Name) property).getIdentifier());
        } else {
            output.append(property.toSource());
        }
    }

    private void visitElementGet(ElementGet eg) {
        visitNode(eg.getTarget());
        output.append("[");
        visitNode(eg.getElement());
        output.append("]");
    }

    private void visitObjectLiteral(ObjectLiteral obj) {
        output.append("{");
        List<ObjectProperty> props = obj.getElements();
        for (int i = 0; i < props.size(); i++) {
            if (i > 0) output.append(",");
            ObjectProperty prop = props.get(i);

            // Check for shorthand property
            if (prop.isShorthand()) {
                AstNode left = prop.getLeft();
                if (left instanceof Name) {
                    output.append(getMungedName(((Name) left).getIdentifier(), prop));
                } else {
                    visitNode(left);
                }
            } else if (prop.isGetter()) {
                output.append("get ");
                visitNode(prop.getLeft());
                AstNode right = prop.getRight();
                if (right instanceof FunctionNode) {
                    FunctionNode fn = (FunctionNode) right;
                    output.append("(");
                    visitParameterList(fn.getParams(), fn);
                    output.append(")");
                    visitNode(fn.getBody());
                }
            } else if (prop.isSetter()) {
                output.append("set ");
                visitNode(prop.getLeft());
                AstNode right = prop.getRight();
                if (right instanceof FunctionNode) {
                    FunctionNode fn = (FunctionNode) right;
                    output.append("(");
                    visitParameterList(fn.getParams(), fn);
                    output.append(")");
                    visitNode(fn.getBody());
                }
            } else {
                // Regular property
                AstNode key = prop.getLeft();
                if (key instanceof ComputedPropertyKey) {
                    output.append("[");
                    visitNode(((ComputedPropertyKey) key).getExpression());
                    output.append("]");
                } else {
                    visitNode(key);
                }
                output.append(":");
                visitNode(prop.getRight());
            }
        }
        output.append("}");
    }

    private void visitArrayLiteral(ArrayLiteral arr) {
        output.append("[");
        List<AstNode> elements = arr.getElements();
        for (int i = 0; i < elements.size(); i++) {
            if (i > 0) output.append(",");
            AstNode element = elements.get(i);
            if (element instanceof EmptyExpression) {
                // Elision - just leave empty
            } else {
                visitNode(element);
            }
        }
        output.append("]");
    }

    private void visitParenthesizedExpression(ParenthesizedExpression paren) {
        output.append("(");
        visitNode(paren.getExpression());
        output.append(")");
    }

    private void visitYield(Yield yield) {
        if (yield.getValue() != null) {
            output.append("yield ");
            visitNode(yield.getValue());
        } else {
            output.append("yield");
        }
    }

    private boolean needsSemicolon(AstNode node) {
        int type = node.getType();
        return type != Token.FUNCTION &&
               type != Token.BLOCK &&
               type != Token.IF &&
               type != Token.FOR &&
               type != Token.WHILE &&
               type != Token.DO &&
               type != Token.SWITCH &&
               type != Token.TRY &&
               type != Token.WITH &&
               type != Token.LABEL;
    }

    private boolean needsParentheses(AstNode child, InfixExpression parent, boolean isLeft) {
        // Simple heuristic: if child is also an infix expression with lower precedence
        if (child instanceof ConditionalExpression) {
            return true;
        }
        if (child instanceof InfixExpression) {
            int childType = child.getType();
            int parentType = parent.getType();
            // Comma operator always needs parens when nested
            if (childType == Token.COMMA && parentType != Token.COMMA) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the munged name for a variable, or the original name if munging is disabled
     */
    private String getMungedName(String originalName, AstNode context) {
        if (!munge) {
            return originalName;
        }

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
        AstNode current = context;
        while (current != null) {
            if (current instanceof FunctionNode || current instanceof AstRoot) {
                ScriptOrFnScope scope = scopeBuilder.getScopeForNode(current);
                if (scope != null) {
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
