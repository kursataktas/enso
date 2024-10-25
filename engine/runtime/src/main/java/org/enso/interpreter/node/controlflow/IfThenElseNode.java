package org.enso.interpreter.node.controlflow;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.InlinedCountingConditionProfile;
import java.util.Objects;
import org.enso.interpreter.node.ExpressionNode;
import org.enso.interpreter.runtime.EnsoContext;
import org.enso.interpreter.runtime.data.EnsoObject;
import org.enso.interpreter.runtime.error.DataflowError;
import org.enso.interpreter.runtime.error.PanicException;
import org.enso.interpreter.runtime.error.PanicSentinel;

@NodeInfo(
    shortName = "if_then_else",
    description = "The runtime representation of a if then else expression.")
@NodeChild(value = "cond", type = ExpressionNode.class)
public abstract class IfThenElseNode extends ExpressionNode {
  private @Child ExpressionNode trueBranch;
  private @Child ExpressionNode falseBranch;

  IfThenElseNode(ExpressionNode t, ExpressionNode fOrNull) {
    this.trueBranch = Objects.requireNonNull(t);
    this.falseBranch = fOrNull;
  }

  public static IfThenElseNode build(ExpressionNode c, ExpressionNode t, ExpressionNode fOrNull) {
    return IfThenElseNodeGen.create(t, fOrNull, c);
  }

  @Specialization
  final Object doBoolean(
      VirtualFrame frame,
      boolean cond,
      @Shared("profile") @Cached InlinedCountingConditionProfile profile) {
    if (cond) {
      profile.wasTrue(this);
      return trueBranch.executeGeneric(frame);
    } else {
      profile.wasFalse(this);
      if (falseBranch == null) {
        var ctx = EnsoContext.get(this);
        return ctx.getBuiltins().nothing();
      } else {
        return falseBranch.executeGeneric(frame);
      }
    }
  }

  static boolean notEnsoObject(Object o) {
    return !(o instanceof EnsoObject);
  }

  @Specialization(
      limit = "3",
      guards = {"notEnsoObject(foreignObj)", "iop.isBoolean(foreignObj)"})
  final Object doTruffleBoolean(
      VirtualFrame frame,
      TruffleObject foreignObj,
      @CachedLibrary("foreignObj") InteropLibrary iop,
      @Shared("profile") @Cached InlinedCountingConditionProfile profile) {
    try {
      var cond = iop.asBoolean(foreignObj);
      return doBoolean(frame, cond, profile);
    } catch (UnsupportedMessageException ex) {
      var ctx = EnsoContext.get(this);
      throw ctx.raiseAssertionPanic(this, "Expecting boolean", ex);
    }
  }

  @Specialization
  final Object doError(VirtualFrame frame, DataflowError error) {
    return error;
  }

  @Specialization
  final Object doPanicSentinel(VirtualFrame frame, PanicSentinel sentinel) {
    CompilerDirectives.transferToInterpreter();
    throw sentinel;
  }

  @Fallback
  @CompilerDirectives.TruffleBoundary
  final PanicException doOther(Object cond) throws PanicException {
    var ctx = EnsoContext.get(this);
    throw ctx.raiseAssertionPanic(this, "Expecting boolean but got " + cond, null);
  }
}
