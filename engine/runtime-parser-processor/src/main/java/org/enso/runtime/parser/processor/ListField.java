package org.enso.runtime.parser.processor;

import java.util.List;
import javax.lang.model.element.TypeElement;

/** Represents a {@code scala.collection.immutable.List} field in the IR node. */
final class ListField implements Field {
  private final String name;
  private final TypeElement typeArgElement;

  /**
   * @param name Name of the field
   * @param typeArgElement TypeElement of the type argument. Must be subtype of IR.
   */
  ListField(String name, TypeElement typeArgElement) {
    this.name = name;
    this.typeArgElement = typeArgElement;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getSimpleTypeName() {
    var typeArg = typeArgElement.getSimpleName().toString();
    return "List<" + typeArg + ">";
  }

  @Override
  public List<String> getImportedTypes() {
    var typePar = typeArgElement.getQualifiedName().toString();
    return List.of("scala.collection.immutable.List", typePar);
  }

  @Override
  public boolean isList() {
    return true;
  }

  @Override
  public boolean isChild() {
    return true;
  }

  @Override
  public boolean isNullable() {
    return false;
  }

  @Override
  public boolean isPrimitive() {
    return false;
  }

  @Override
  public boolean isExpression() {
    return false;
  }
}
