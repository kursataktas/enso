package org.enso.runtime.parser.processor.test.gen.ir;

import org.enso.compiler.core.IR;
import org.enso.runtime.parser.dsl.IRChild;
import org.enso.runtime.parser.dsl.IRCopyMethod;
import org.enso.runtime.parser.dsl.IRNode;

@IRNode
public interface CopyNameTestIR extends IR {
  @IRChild
  NameTestIR name();

  /**
   * Should generate implementation that will produce the exact same copy with a different name
   * field.
   */
  @IRCopyMethod
  CopyNameTestIR copy(NameTestIR name);
}
