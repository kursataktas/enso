package org.enso.interpreter.node.expression.builtin.runtime;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import org.enso.interpreter.dsl.BuiltinMethod;
import org.enso.interpreter.dsl.Suspend;
import org.enso.interpreter.node.BaseNode;
import org.enso.interpreter.node.callable.thunk.ThunkExecutorNode;
import org.enso.interpreter.node.expression.builtin.text.util.ExpectStringNode;
import org.enso.interpreter.runtime.EnsoContext;
import org.enso.interpreter.runtime.data.atom.Atom;
import org.enso.interpreter.runtime.state.ExecutionEnvironment;
import org.enso.interpreter.runtime.state.State;

@BuiltinMethod(
    type = "Runtime",
    name = "with_enabled_context_builtin",
    description = "Allows context in the specified scope.",
    autoRegister = false,
    inlineable = true)
public class RuntimeWithEnabledContextNode extends Node {
  private @Child ThunkExecutorNode thunkExecutorNode = ThunkExecutorNode.build();
  private @Child ExpectStringNode expectStringNode = ExpectStringNode.build();

  Object execute(
      VirtualFrame frame, State state, Atom context, Object env_name, @Suspend Object action) {
    String envName = expectStringNode.execute(env_name);
    ExecutionEnvironment original =
        EnsoContext.get(this).enableExecutionEnvironment(context, envName);
    try {
      return thunkExecutorNode.executeThunk(frame, action, state, BaseNode.TailStatus.NOT_TAIL);
    } finally {
      EnsoContext.get(this).setExecutionEnvironment(original);
    }
  }
}
