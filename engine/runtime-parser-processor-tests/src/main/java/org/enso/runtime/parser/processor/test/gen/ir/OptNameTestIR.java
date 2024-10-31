package org.enso.runtime.parser.processor.test.gen.ir;

import org.enso.compiler.core.IR;
import org.enso.runtime.parser.dsl.IRChild;
import org.enso.runtime.parser.dsl.IRNode;

@IRNode
public interface OptNameTestIR extends IR {
  @IRChild(required = false)
  OptNameTestIR originalName();
}
