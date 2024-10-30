package org.enso.runtime.parser.processor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import org.enso.runtime.parser.dsl.IRChild;
import org.enso.runtime.parser.dsl.IRNode;

/**
 * Generates code for interfaces annotated with {@link org.enso.runtime.parser.dsl.IRNode}.
 * Technically, the interface does not have to be annotated with {@link
 * org.enso.runtime.parser.dsl.IRNode}, it can just be enclosed by another interface with that
 * annotation.
 *
 * <p>It is expected that the interface (passed as {@link javax.lang.model.element.TypeElement} in
 * this class) extends {@link org.enso.compiler.core.IR}, either directly or via a hierarchy of
 * other super interfaces.
 *
 * <p>Every parameterless abstract method defined by the interface (or any super interface) is
 * treated as a field of the IR node. If the parameterless method is annotated with {@link
 * org.enso.runtime.parser.dsl.IRChild}, it is treated as a <emph>child</emph> and will get into the
 * generated code for, e.g., methods like {@link org.enso.compiler.core.IR#children()}.
 */
final class IRNodeClassGenerator {
  private final ProcessingEnvironment processingEnv;
  private final TypeElement interfaceType;

  /** Name of the class that is being generated */
  private final String className;

  /** User defined fields - all the abstract parameterless methods, including the inherited ones. */
  private final List<Field> fields;

  /**
   * {@link org.enso.compiler.core.IR#duplicate(boolean, boolean, boolean, boolean) duplicate}
   * method element. We need to know if there is any override with a different return type in the
   * interface hierarchy. If not, this is just a reference to the method from IR.
   */
  private final ExecutableElement duplicateMethod;

  private static final Set<String> defaultImportedTypes =
      Set.of(
          "java.util.UUID",
          "java.util.ArrayList",
          "java.util.function.Function",
          "org.enso.compiler.core.Identifier",
          "org.enso.compiler.core.IR",
          "org.enso.compiler.core.ir.DiagnosticStorage",
          "org.enso.compiler.core.ir.DiagnosticStorage$",
          "org.enso.compiler.core.ir.Expression",
          "org.enso.compiler.core.ir.IdentifiedLocation",
          "org.enso.compiler.core.ir.MetadataStorage",
          "scala.Option");

  /**
   * @param interfaceType Type of the interface for which we are generating code. It is expected
   *     that the interface does not contain any nested interfaces or classes, just methods.
   * @param className Name of the generated class. Non qualified.
   */
  IRNodeClassGenerator(
      ProcessingEnvironment processingEnv, TypeElement interfaceType, String className) {
    assert !className.contains(".") : "Class name should be simple, not qualified";
    this.processingEnv = processingEnv;
    this.interfaceType = interfaceType;
    this.className = className;
    this.fields = getAllFields(interfaceType);
    this.duplicateMethod = Utils.findDuplicateMethod(interfaceType, processingEnv);
    var nestedTypes =
        interfaceType.getEnclosedElements().stream()
            .filter(
                elem ->
                    elem.getKind() == ElementKind.INTERFACE || elem.getKind() == ElementKind.CLASS)
            .toList();
    if (!nestedTypes.isEmpty()) {
      throw new RuntimeException("Nested types must be handled separately: " + nestedTypes);
    }
  }

  /** Returns simple name of the generated class. */
  String getClassName() {
    return className;
  }

  /**
   * Returns the simple name of the interface for which an implementing class is being generated.
   */
  String getInterfaceName() {
    return interfaceType.getSimpleName().toString();
  }

  /** Returns set of import statements that should be included in the generated class. */
  Set<String> imports() {
    var importsForFields =
        fields.stream()
            .flatMap(field -> field.getImportedTypes().stream())
            .collect(Collectors.toUnmodifiableSet());
    var allImports = new HashSet<String>();
    allImports.addAll(defaultImportedTypes);
    allImports.addAll(importsForFields);
    return allImports.stream()
        .map(importedType -> "import " + importedType + ";")
        .collect(Collectors.toUnmodifiableSet());
  }

  /** Generates the body of the class - fields, field setters, method overrides, builder, etc. */
  String classBody() {
    return """
        $fields

        $constructor

        public static Builder builder() {
          return new Builder();
        }

        $overrideUserDefinedMethods

        $overrideIRMethods

        $builder
        """
        .replace("$fields", fieldsCode())
        .replace("$constructor", constructor())
        .replace("$overrideUserDefinedMethods", overrideUserDefinedMethods())
        .replace("$overrideIRMethods", overrideIRMethods())
        .replace("$builder", builder());
  }

  /**
   * Collects all abstract methods (with no parameters) from this interface and all the interfaces
   * that are extended by this interface. Every abstract method corresponds to a single field in the
   * newly generated record. Abstract methods annotated with {@link IRChild} are considered IR
   * children.
   *
   * @param irNodeInterface Type element of the interface annotated with {@link IRNode}.
   * @return List of fields
   */
  private List<Field> getAllFields(TypeElement irNodeInterface) {
    var fieldCollector = new FieldCollector(processingEnv, irNodeInterface);
    return fieldCollector.collectFields();
  }

  /**
   * Returns string representation of the class fields. Meant to be at the beginning of the class
   * body.
   */
  private String fieldsCode() {
    var userDefinedFields =
        fields.stream()
            .map(field -> "private final " + field.getSimpleTypeName() + " " + field.getName())
            .collect(Collectors.joining(";" + System.lineSeparator()));
    var code =
        """
        $userDefinedFields;
        // Not final on purpose
        private DiagnosticStorage diagnostics;
        private MetadataStorage passData;
        private IdentifiedLocation location;
        private UUID id;
        """
            .replace("$userDefinedFields", userDefinedFields);
    return indent(code, 2);
  }

  /**
   * Returns string representation of the package-private constructor of the generated class. Note
   * that the constructor is meant to be invoked only by the internal Builder class.
   */
  private String constructor() {
    var sb = new StringBuilder();
    sb.append("private ").append(className).append("(");
    var inParens =
        fields.stream()
            .map(
                field ->
                    "$fieldType $fieldName"
                        .replace("$fieldType", field.getSimpleTypeName())
                        .replace("$fieldName", field.getName()))
            .collect(Collectors.joining(", "));
    sb.append(inParens).append(") {").append(System.lineSeparator());
    var ctorBody =
        fields.stream()
            .map(field -> "  this.$fieldName = $fieldName;".replace("$fieldName", field.getName()))
            .collect(Collectors.joining(System.lineSeparator()));
    sb.append(indent(ctorBody, 2));
    sb.append(System.lineSeparator());
    sb.append("}").append(System.lineSeparator());
    return indent(sb.toString(), 2);
  }

  private String childrenMethodBody() {
    var sb = new StringBuilder();
    var nl = System.lineSeparator();
    sb.append("var list = new ArrayList<IR>();").append(nl);
    fields.stream()
        .filter(Field::isChild)
        .forEach(
            childField -> {
              String addToListCode;
              if (!childField.isList()) {
                addToListCode = "list.add(" + childField.getName() + ");";
              } else {
                addToListCode =
                    """
                    $childName.foreach(list::add);
                    """
                        .replace("$childName", childField.getName());
              }
              var childName = childField.getName();
              if (childField.isNullable()) {
                sb.append(
                    """
                if ($childName != null) {
                  $addToListCode
                }
                """
                        .replace("$childName", childName)
                        .replace("$addToListCode", addToListCode));
              } else {
                sb.append(addToListCode);
              }
            });
    sb.append("return scala.jdk.javaapi.CollectionConverters.asScala(list).toList();").append(nl);
    return indent(sb.toString(), 2);
  }

  private String duplicateMethodBody() {
    var nl = System.lineSeparator();
    var nullableChildrenCode =
        fields.stream()
            .filter(field -> field.isChild() && field.isNullable())
            .map(
                field ->
                    """
            IR $childNameDup = null;
            if ($childName != null) {
              $childNameDup = $childName.duplicate(keepLocations, keepMetadata, keepDiagnostics, keepIdentifiers);
              if (!($childNameDup instanceof $childType)) {
                throw new IllegalStateException("Duplicated child is not of the expected type: " + $childNameDup);
              }
            }
            """
                        .replace("$childType", field.getSimpleTypeName())
                        .replace("$childName", field.getName())
                        .replace("$childNameDup", field.getName() + "Duplicated"))
            .collect(Collectors.joining(nl));

    var notNullableChildrenCode =
        fields.stream()
            .filter(field -> field.isChild() && !field.isNullable() && !field.isList())
            .map(
                field ->
                    """
            IR $childNameDup =
              $childName.duplicate(keepLocations, keepMetadata, keepDiagnostics, keepIdentifiers);
            if (!($childNameDup instanceof $childType)) {
              throw new IllegalStateException("Duplicated child is not of the expected type: " + $childNameDup);
            }
            """
                        .replace("$childType", field.getSimpleTypeName())
                        .replace("$childName", field.getName())
                        .replace("$childNameDup", field.getName() + "Duplicated"))
            .collect(Collectors.joining(nl));

    var listChildrenCode =
        fields.stream()
            .filter(field -> field.isChild() && field.isList())
            .map(
                field ->
                    """
            $childListType $childNameDup =
              $childName.map(child -> {
                IR dupChild = child.duplicate(keepLocations, keepMetadata, keepDiagnostics, keepIdentifiers);
                if (!(dupChild instanceof $childType)) {
                  throw new IllegalStateException("Duplicated child is not of the expected type: " + dupChild);
                }
                return ($childType) dupChild;
              });
            """
                        .replace("$childListType", field.getSimpleTypeName())
                        .replace("$childType", field.getTypeParameter())
                        .replace("$childName", field.getName())
                        .replace("$childNameDup", field.getName() + "Duplicated"))
            .collect(Collectors.joining(nl));

    var code = nullableChildrenCode + nl + notNullableChildrenCode + nl + listChildrenCode;
    if (stripWhitespaces(code).isEmpty()) {
      code = "return new " + className + "();";
    }
    return indent(code, 2);
  }

  private static String stripWhitespaces(String s) {
    return s.replaceAll("\\s+", "");
  }

  /**
   * Returns a String representing all the overriden methods from {@link org.enso.compiler.core.IR}.
   * Meant to be inside the generated record definition.
   */
  private String overrideIRMethods() {
    var duplicateMethodRetType = duplicateMethod.getReturnType().toString();
    var code =
        """

        @Override
        public MetadataStorage passData() {
          if (passData == null) {
            passData = new MetadataStorage();
          }
          return passData;
        }

        @Override
        public Option<IdentifiedLocation> location() {
          if (location == null) {
            return scala.Option.empty();
          } else {
            return scala.Option.apply(location);
          }
        }

        @Override
        public IR setLocation(Option<IdentifiedLocation> location) {
          throw new UnsupportedOperationException("unimplemented");
        }

        @Override
        public IR mapExpressions(Function<Expression, Expression> fn) {
          throw new UnsupportedOperationException("unimplemented");
        }

        @Override
        public scala.collection.immutable.List<IR> children() {
        $childrenMethodBody
        }

        @Override
        public @Identifier UUID getId() {
          if (id == null) {
            id = UUID.randomUUID();
          }
          return id;
        }

        @Override
        public DiagnosticStorage diagnostics() {
          return diagnostics;
        }

        @Override
        public DiagnosticStorage getDiagnostics() {
          if (diagnostics == null) {
            diagnostics = DiagnosticStorage$.MODULE$.empty();
          }
          return diagnostics;
        }

        @Override
        public $duplicateMethodRetType duplicate(
          boolean keepLocations,
          boolean keepMetadata,
          boolean keepDiagnostics,
          boolean keepIdentifiers
        ) {
          $duplicateMethodBody
        }

        @Override
        public String showCode(int indent) {
          throw new UnsupportedOperationException("unimplemented");
        }
        """
            .replace("$childrenMethodBody", childrenMethodBody())
            .replace("$duplicateMethodRetType", duplicateMethodRetType)
            .replace("$duplicateMethodBody", duplicateMethodBody());
    return indent(code, 2);
  }

  /**
   * Returns string representation of all parameterless abstract methods from the interface
   * annotated with {@link IRNode}.
   *
   * @return Code of the overriden methods
   */
  private String overrideUserDefinedMethods() {
    var code =
        fields.stream()
            .map(
                field ->
                    """
            @Override
            public $returnType $fieldName() {
              return $fieldName;
            }
            """
                        .replace("$returnType", field.getSimpleTypeName())
                        .replace("$fieldName", field.getName()))
            .collect(Collectors.joining(System.lineSeparator()));
    return indent(code, 2);
  }

  /**
   * Returns string representation of the code for the builder - that is a nested class that allows
   * to build the record.
   *
   * @return Code of the builder
   */
  private String builder() {
    var fieldDeclarations =
        fields.stream()
            .map(
                field ->
                    """
            private $fieldType $fieldName;
            """
                        .replace("$fieldName", field.getName())
                        .replace("$fieldType", field.getSimpleTypeName()))
            .collect(Collectors.joining(System.lineSeparator()));

    var fieldSetters =
        fields.stream()
            .map(
                field ->
                    """
        public Builder $fieldName($fieldType $fieldName) {
          this.$fieldName = $fieldName;
          return this;
        }
        """
                        .replace("$fieldName", field.getName())
                        .replace("$fieldType", field.getSimpleTypeName()))
            .collect(Collectors.joining(System.lineSeparator()));

    // Validation code for all non-nullable fields
    var validationCode =
        fields.stream()
            .filter(field -> !field.isNullable() && !field.isPrimitive())
            .map(
                field ->
                    """
            if (this.$fieldName == null) {
              throw new IllegalArgumentException("$fieldName is required");
            }
            """
                        .replace("$fieldName", field.getName()))
            .collect(Collectors.joining(System.lineSeparator()));

    var fieldList = fields.stream().map(Field::getName).collect(Collectors.joining(", "));

    var code =
        """
        public static final class Builder {
          $fieldDeclarations

          $fieldSetters

          public $className build() {
            validate();
            return new $className($fieldList);
          }

          private void validate() {
            $validationCode
          }
        }
        """
            .replace("$fieldDeclarations", fieldDeclarations)
            .replace("$fieldSetters", fieldSetters)
            .replace("$className", className)
            .replace("$fieldList", fieldList)
            .replace("$validationCode", indent(validationCode, 2));
    return indent(code, 2);
  }

  private static String indent(String code, int indentation) {
    return code.lines()
        .map(line -> " ".repeat(indentation) + line)
        .collect(Collectors.joining(System.lineSeparator()));
  }

  private void ensureIsSubtypeOfIR(TypeElement typeElem) {
    if (!Utils.isSubtypeOfIR(typeElem, processingEnv)) {
      Utils.printError(
          "Method annotated with @IRChild must return a subtype of IR interface",
          typeElem,
          processingEnv.getMessager());
    }
  }
}
