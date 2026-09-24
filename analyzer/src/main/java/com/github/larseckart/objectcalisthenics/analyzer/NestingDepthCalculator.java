package com.github.larseckart.objectcalisthenics.analyzer;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.visitor.GenericVisitorAdapter;

/**
 * Computes the maximum nesting depth inside a method body.
 *
 * <p>The method body itself is depth 0. Each nested block (including lambda
 * bodies and local/anonymous classes) adds one level.</p>
 */
class NestingDepthCalculator {

  int compute(MethodDeclaration method) {
    return method.getBody()
        .map(body -> {
          NestingDepthVisitor visitor = new NestingDepthVisitor();
          visitor.visit(body, 0);
          return visitor.maxDepth;
        })
        .orElse(0);
  }

  private static class NestingDepthVisitor extends GenericVisitorAdapter<Void, Integer> {
    private int maxDepth = 0;

    @Override
    public Void visit(BlockStmt node, Integer depth) {
      int childDepth = depth;
      boolean isMethodBody = node.getParentNode()
          .map(parent -> parent instanceof MethodDeclaration)
          .orElse(false);
      if (!isMethodBody) {
        childDepth = depth + 1;
        maxDepth = Math.max(maxDepth, childDepth);
      }
      return super.visit(node, childDepth);
    }

    @Override
    public Void visit(LambdaExpr node, Integer depth) {
      int childDepth = depth + 1;
      maxDepth = Math.max(maxDepth, childDepth);
      return super.visit(node, childDepth);
    }

    @Override
    public Void visit(ClassOrInterfaceDeclaration node, Integer depth) {
      int childDepth = depth + 1;
      maxDepth = Math.max(maxDepth, childDepth);
      return super.visit(node, childDepth);
    }

    @Override
    public Void visit(RecordDeclaration node, Integer depth) {
      int childDepth = depth + 1;
      maxDepth = Math.max(maxDepth, childDepth);
      return super.visit(node, childDepth);
    }
  }
}
