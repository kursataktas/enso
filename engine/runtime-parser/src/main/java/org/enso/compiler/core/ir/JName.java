package org.enso.compiler.core.ir;

import java.util.List;
import java.util.stream.Collectors;
import org.enso.compiler.core.ir.module.scope.JDefinition;
import org.enso.runtime.parser.dsl.IRChild;
import org.enso.runtime.parser.dsl.IRNode;

@IRNode
public interface JName extends JExpression {
  String name();

  boolean isMethod();

  interface JBlank extends JName {}

  interface JLiteral extends JName {
    @IRChild(required = false)
    JName originalName();
  }

  interface JQualified extends JName {
    @IRChild
    List<JName> parts();

    @Override
    default String name() {
      return parts().stream().map(JName::name).collect(Collectors.joining("."));
    }
  }

  interface JSelf extends JName {
    boolean synthetic();
  }

  interface JAnnotation extends JName, JDefinition {}

  interface JGenericAnnotation extends JAnnotation {
    @IRChild
    JExpression expression();
  }
}
