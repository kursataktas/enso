package org.enso.runtime.parser.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeKind;

/**
 * Code generator for {@code org.enso.compiler.core.ir.IR#duplicate} method or any of its override.
 */
class DuplicateMethodGenerator {
  private final ExecutableElement duplicateMethod;
  private final List<Field> fields;
  private final String className;
  private static final List<Parameter> parameters =
      List.of(
          new Parameter("boolean", "keepLocations"),
          new Parameter("boolean", "keepMetadata"),
          new Parameter("boolean", "keepDiagnostics"),
          new Parameter("boolean", "keepIdentifiers"));

  /**
   * @param duplicateMethod ExecutableElement representing the duplicate method (or its override).
   * @param fields List of the fields of the generated class
   * @param className Name of the generated class
   */
  DuplicateMethodGenerator(
      ExecutableElement duplicateMethod, List<Field> fields, String className) {
    ensureDuplicateMethodHasExpectedSignature(duplicateMethod);
    this.duplicateMethod = Objects.requireNonNull(duplicateMethod);
    this.fields = Objects.requireNonNull(fields);
    this.className = Objects.requireNonNull(className);
  }

  private static void ensureDuplicateMethodHasExpectedSignature(ExecutableElement duplicateMethod) {
    var dupMethodParameters = duplicateMethod.getParameters();
    if (dupMethodParameters.size() != parameters.size()) {
      throw new IllegalArgumentException(
          "Duplicate method must have " + parameters.size() + " parameters");
    }
    var allParamsAreBooleans =
        dupMethodParameters.stream().allMatch(par -> par.asType().getKind() == TypeKind.BOOLEAN);
    if (!allParamsAreBooleans) {
      throw new IllegalArgumentException(
          "All parameters of the duplicate method must be of type boolean");
    }
  }

  String generateDuplicateMethodCode() {
    var sb = new StringBuilder();
    sb.append("@Override").append(System.lineSeparator());
    sb.append("public ")
        .append(dupMethodRetType())
        .append(" duplicate(")
        .append(parameters.stream().map(Parameter::toString).collect(Collectors.joining(", ")))
        .append(") {")
        .append(System.lineSeparator());
    var duplicatedVars = new ArrayList<DuplicateVar>();
    for (var field : fields) {
      if (field.isChild()) {
        if (field.isNullable()) {
          sb.append(Utils.indent(nullableChildCode(field), 2));
          sb.append(System.lineSeparator());
          duplicatedVars.add(
              new DuplicateVar(field.getSimpleTypeName(), dupFieldName(field), true));
        } else if (!field.isNullable() && !field.isList()) {
          sb.append(Utils.indent(notNullableChildCode(field), 2));
          sb.append(System.lineSeparator());
          duplicatedVars.add(
              new DuplicateVar(field.getSimpleTypeName(), dupFieldName(field), true));
        } else if (field.isList()) {
          sb.append(Utils.indent(listChildCode(field), 2));
          sb.append(System.lineSeparator());
          duplicatedVars.add(new DuplicateVar(null, dupFieldName(field), false));
        }
      }
      // TODO: Duplicate non-child fields?
    }
    sb.append(Utils.indent(returnStatement(duplicatedVars), 2));
    sb.append(System.lineSeparator());
    sb.append("}");
    sb.append(System.lineSeparator());
    return sb.toString();
  }

  private static String dupFieldName(Field field) {
    return field.getName() + "Duplicated";
  }

  private static String nullableChildCode(Field nullableChild) {
    assert nullableChild.isNullable() && nullableChild.isChild();
    return """
          IR $dupName = null;
            if ($childName != null) {
              $dupName = $childName.duplicate($parameterNames);
              if (!($dupName instanceof $childType)) {
                throw new IllegalStateException("Duplicated child is not of the expected type: " + $dupName);
              }
          }
        """
        .replace("$childType", nullableChild.getSimpleTypeName())
        .replace("$childName", nullableChild.getName())
        .replace("$dupName", dupFieldName(nullableChild))
        .replace("$parameterNames", String.join(", ", parameterNames()));
  }

  private static String notNullableChildCode(Field child) {
    assert child.isChild() && !child.isNullable() && !child.isList();
    return """
          IR $dupName = $childName.duplicate($parameterNames);
          if (!($dupName instanceof $childType)) {
            throw new IllegalStateException("Duplicated child is not of the expected type: " + $dupName);
          }
          """
        .replace("$childType", child.getSimpleTypeName())
        .replace("$childName", child.getName())
        .replace("$dupName", dupFieldName(child))
        .replace("$parameterNames", String.join(", ", parameterNames()));
  }

  private static String listChildCode(Field listChild) {
    assert listChild.isChild() && listChild.isList();
    return """
          $childListType $dupName =
            $childName.map(child -> {
              IR dupChild = child.duplicate($parameterNames);
              if (!(dupChild instanceof $childType)) {
                throw new IllegalStateException("Duplicated child is not of the expected type: " + dupChild);
              }
              return ($childType) dupChild;
            });
          """
        .replace("$childListType", listChild.getSimpleTypeName())
        .replace("$childType", listChild.getTypeParameter())
        .replace("$childName", listChild.getName())
        .replace("$dupName", dupFieldName(listChild))
        .replace("$parameterNames", String.join(", ", parameterNames()));
  }

  private static List<String> parameterNames() {
    return parameters.stream().map(Parameter::name).collect(Collectors.toList());
  }

  private String returnStatement(List<DuplicateVar> duplicatedVars) {
    var argList =
        duplicatedVars.stream()
            .map(
                var -> {
                  if (var.needsCast) {
                    return "(" + var.type + ") " + var.name;
                  } else {
                    return var.name;
                  }
                })
            .collect(Collectors.joining(", "));
    return "return new " + className + "(" + argList + ");";
  }

  private String dupMethodRetType() {
    return duplicateMethod.getReturnType().toString();
  }

  private static String stripWhitespaces(String s) {
    return s.replaceAll("\\s+", "");
  }

  /**
   * @param type Nullable
   * @param name
   * @param needsCast If the duplicated variable needs to be casted to its type in the return
   *     statement.
   */
  private record DuplicateVar(String type, String name, boolean needsCast) {}

  /**
   * Parameter for the duplicate method
   *
   * @param type
   * @param name
   */
  private record Parameter(String type, String name) {

    @Override
    public String toString() {
      return type + " " + name;
    }
  }
}
