package org.enso.interpreter.node.controlflow;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.NodeInfo;
import java.util.Objects;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.EnsoContext;
import org.enso.interpreter.runtime.error.PanicException;

@NodeInfo(
    shortName = "if_then_else",
    description = "The runtime representation of a if then else expression.")
public final class IfThenElseNode extends ExpressionNode {
  private @Child ExpressionNode cond;
  private @Child ExpressionNode trueBranch;
  private @Child ExpressionNode falseBranch;

  private IfThenElseNode(
      ExpressionNode cond, ExpressionNode trueBranch, ExpressionNode falseBranch) {
    this.cond = Objects.requireNonNull(cond);
    this.trueBranch = Objects.requireNonNull(trueBranch);
    this.falseBranch = falseBranch;
  }

  public static IfThenElseNode build(
      ExpressionNode cond, ExpressionNode trueBranch, ExpressionNode falseBranch) {
    return new IfThenElseNode(cond, trueBranch, falseBranch);
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    var condResult = cond.executeGeneric(frame);
    var ctx = EnsoContext.get(this);
    if (condResult instanceof Boolean trueOrFalse) {
      if (trueOrFalse) {
        return trueBranch.executeGeneric(frame);
      } else {
        if (falseBranch == null) {
          return ctx.getBuiltins().nothing();
        } else {
          return falseBranch.executeGeneric(frame);
        }
      }
    } else {
      throw expectingBoolean(ctx, condResult);
    }
  }

  @CompilerDirectives.TruffleBoundary
  private PanicException expectingBoolean(EnsoContext ctx, Object condResult)
      throws PanicException {
    throw ctx.raiseAssertionPanic(this, "Expecting boolean but got " + condResult, null);
  }
}
