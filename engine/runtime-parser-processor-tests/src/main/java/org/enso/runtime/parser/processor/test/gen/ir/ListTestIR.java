package org.enso.runtime.parser.processor.test.gen.ir;

import org.enso.compiler.core.IR;
import org.enso.runtime.parser.dsl.IRChild;
import org.enso.runtime.parser.dsl.IRNode;
import scala.collection.immutable.List;

@IRNode
public interface ListTestIR extends IR {
  @IRChild
  List<NameTestIR> names();
}
