package org.enso.compiler.core.ir;

import org.enso.compiler.core.IR;
import org.enso.runtime.parser.dsl.IRChild;
import org.enso.runtime.parser.dsl.IRNode;

@IRNode
public interface JCallArgument extends IR {
  @IRChild(required = false)
  Name name();

  @IRChild
  Expression value();

  interface JSpecified extends JCallArgument {}
}
