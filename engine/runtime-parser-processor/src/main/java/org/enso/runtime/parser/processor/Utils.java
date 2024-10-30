package org.enso.runtime.parser.processor;

import java.util.ArrayDeque;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;

final class Utils {
  private Utils() {}

  /** Returns true if the given {@code type} is a subtype of {@code org.enso.compiler.core.IR}. */
  static boolean isSubtypeOfIR(TypeElement type, ProcessingEnvironment processingEnv) {
    boolean irEncountered[] = {false};
    iterateSuperInterfaces(
        type,
        processingEnv,
        superInterface -> {
          // current.getQualifiedName().toString() returns only "IR" as well, so we can't use it.
          // This is because runtime-parser-processor project does not depend on runtime-parser and
          // so the org.enso.compiler.core.IR interface is not available in the classpath.
          if (superInterface.getSimpleName().toString().equals("IR")) {
            irEncountered[0] = true;
          }
        });
    return irEncountered[0];
  }

  /** Returns true if the given {@code type} is an {@code org.enso.compiler.core.IR} interface. */
  static boolean isIRInterface(TypeMirror type, ProcessingEnvironment processingEnv) {
    var elem = processingEnv.getTypeUtils().asElement(type);
    return elem.getKind() == ElementKind.INTERFACE && elem.getSimpleName().toString().equals("IR");
  }

  /** Returns true if the given type extends {@link org.enso.compiler.core.ir.Expression} */
  static boolean isSubtypeOfExpression(TypeMirror type, ProcessingEnvironment processingEnv) {
    var expressionType =
        processingEnv
            .getElementUtils()
            .getTypeElement("org.enso.compiler.core.ir.Expression")
            .asType();
    return processingEnv.getTypeUtils().isAssignable(type, expressionType);
  }

  static void printError(String msg, Element elem, Messager messager) {
    messager.printMessage(Kind.ERROR, msg, elem);
  }

  static String indent(String code, int indentation) {
    return code.lines()
        .map(line -> " ".repeat(indentation) + line)
        .collect(Collectors.joining(System.lineSeparator()));
  }

  static boolean isScalaList(TypeElement type, ProcessingEnvironment procEnv) {
    var scalaListType = procEnv.getElementUtils().getTypeElement("scala.collection.immutable.List");
    return procEnv.getTypeUtils().isAssignable(type.asType(), scalaListType.asType());
  }

  /**
   * Returns true if the method has a default implementation in some of the super interfaces.
   *
   * @param method the method to check
   * @param interfaceType the interface that declares the method
   * @param procEnv
   * @return
   */
  static boolean hasDefaultImplementation(
      ExecutableElement method, TypeElement interfaceType, ProcessingEnvironment procEnv) {
    boolean defaultMethodEncountered[] = {false};
    iterateSuperInterfaces(
        interfaceType,
        procEnv,
        superInterface -> {
          for (var enclosedElem : superInterface.getEnclosedElements()) {
            if (enclosedElem instanceof ExecutableElement executableElem) {
              if (executableElem.getSimpleName().equals(method.getSimpleName())
                  && executableElem.isDefault()) {
                defaultMethodEncountered[0] = true;
              }
            }
          }
        });
    return defaultMethodEncountered[0];
  }

  /**
   * Find any override of {@link org.enso.compiler.core.IR#duplicate(boolean, boolean, boolean, boolean) duplicate method}.
   * Or the duplicate method on the interface itself.
   * Note that there can be an override with a different return type in a sub interface.
   * @param interfaceType Interface from where the search is started.
   *                      All super interfaces are searched transitively.
   * @return not null.
   */
  static ExecutableElement findDuplicateMethod(
      TypeElement interfaceType, ProcessingEnvironment procEnv) {
    ExecutableElement[] duplicateMethod = {null};
    iterateSuperInterfaces(
        interfaceType,
        procEnv,
        superInterface -> {
          for (var enclosedElem : superInterface.getEnclosedElements()) {
            if (enclosedElem instanceof ExecutableElement execElem) {
              if (isDuplicateMethod(execElem)) {
                if (duplicateMethod[0] == null) {
                  duplicateMethod[0] = execElem;
                }
              }
            }
          }
        }
    );
    assert duplicateMethod[0] != null : "Interface " + interfaceType.getQualifiedName() + " must implement IR, so it must declare duplicate method";
    return duplicateMethod[0];
  }

  private static boolean isDuplicateMethod(ExecutableElement executableElement) {
    return executableElement.getSimpleName().toString().equals("duplicate") &&
        executableElement.getParameters().size() == 4;
  }

  private static void iterateSuperInterfaces(
      TypeElement type, ProcessingEnvironment processingEnv, Consumer<TypeElement> consumer) {
    var interfacesToProcess = new ArrayDeque<TypeElement>();
    interfacesToProcess.add(type);
    while (!interfacesToProcess.isEmpty()) {
      var current = interfacesToProcess.pop();
      consumer.accept(current);
      // Add all super interfaces to the queue
      for (var superInterface : current.getInterfaces()) {
        var superInterfaceElem = processingEnv.getTypeUtils().asElement(superInterface);
        if (superInterfaceElem instanceof TypeElement superInterfaceTypeElem) {
          interfacesToProcess.add(superInterfaceTypeElem);
        }
      }
    }
  }
}
