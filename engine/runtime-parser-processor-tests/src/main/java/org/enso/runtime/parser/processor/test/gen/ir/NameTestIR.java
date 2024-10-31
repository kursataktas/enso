package org.enso.runtime.parser.processor.test.gen.ir;

import org.enso.compiler.core.IR;
import org.enso.runtime.parser.dsl.IRNode;

@IRNode
public interface NameTestIR extends IR {
  String name();
}
