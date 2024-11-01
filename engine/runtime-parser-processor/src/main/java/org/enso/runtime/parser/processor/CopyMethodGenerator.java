package org.enso.runtime.parser.processor;

import java.util.List;
import java.util.Objects;
import javax.lang.model.element.ExecutableElement;
import org.enso.runtime.parser.dsl.IRCopyMethod;

class CopyMethodGenerator {
  private final ExecutableElement copyMethod;
  private final List<Field> userDefinedFields;

  CopyMethodGenerator(ExecutableElement copyMethod, List<Field> userDefinedFields) {
    ensureIsAnnotated(copyMethod);
    this.copyMethod = Objects.requireNonNull(copyMethod);
    this.userDefinedFields = Objects.requireNonNull(userDefinedFields);
    for (var parameter : copyMethod.getParameters()) {
      throw new UnsupportedOperationException("unimplemented");
    }
  }

  private static void ensureIsAnnotated(ExecutableElement copyMethod) {
    if (copyMethod.getAnnotation(IRCopyMethod.class) == null) {
      throw new IllegalArgumentException("Copy method must be annotated with @IRCopyMethod");
    }
  }

  String generateCopyMethod() {
    throw new UnsupportedOperationException("unimplemented");
  }
}
