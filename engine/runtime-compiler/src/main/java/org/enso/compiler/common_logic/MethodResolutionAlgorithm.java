package org.enso.compiler.common_logic;

import java.util.List;
import java.util.stream.Stream;

/**
 * Encapsulates the logic for resolving a method call on a type/module.
 *
 * <p>The same logic is needed in two places:
 *
 * <ol>
 *   <li>in the runtime ({@link
 *       org.enso.interpreter.runtime.scope.ModuleScope#lookupMethodDefinition}),
 *   <li>in the type checker ({@link org.enso.compiler.pass.analyse.types.MethodTypeResolver}).
 * </ol>
 *
 * <p>To ensure that all usages stay consistent, they should all rely on the logic implemented in
 * this class, customizing it to the specific needs of the context in which it is used.
 *
 * @param <ModuleScopeType> the type of the module scope that the algorithm will be working with
 * @see CommonModuleScopeShape for the explanation of other type parameters
 */
public abstract class MethodResolutionAlgorithm<
    FunctionType,
    TypeScopeReferenceType,
    ImportExportScopeType,
    ModuleScopeType extends
        CommonModuleScopeShape<FunctionType, TypeScopeReferenceType, ImportExportScopeType>> {
  private final ModuleScopeType currentModuleScope;

  protected MethodResolutionAlgorithm(ModuleScopeType currentModuleScope) {
    this.currentModuleScope = currentModuleScope;
  }

  /**
   * Looks up a method definition as seen in the current module.
   *
   * <p>This takes into consideration all definitions local to the module and everything that has
   * been imported.
   *
   * <p>The algorithm is as follows:
   *
   * <ol>
   *   <li>Methods defined in the same module as the type which is being called have the highest
   *       precedence.
   *   <li>Next, methods defined in the current module are considered.
   *   <li>Finally, methods imported from other modules.
   * </ol>
   */
  public FunctionType lookupMethodDefinition(TypeScopeReferenceType type, String methodName) {
    var definitionScope = findDefinitionScope(type);
    if (definitionScope != null) {
      var definedWithAtom = definitionScope.getMethodForType(type, methodName);
      if (definedWithAtom != null) {
        return definedWithAtom;
      }
    }

    var definedHere = currentModuleScope.getMethodForType(type, methodName);
    if (definedHere != null) {
      return definedHere;
    }

    return findInImports(type, methodName);
  }

  private FunctionType findInImports(TypeScopeReferenceType type, String methodName) {
    var found =
        currentModuleScope.getImports().stream()
            .flatMap(
                (importExportScope) -> {
                  var exportedMethod =
                      findExportedMethodInImportScope(importExportScope, type, methodName);
                  if (exportedMethod != null) {
                    return Stream.of(new MethodFromImport<>(exportedMethod, importExportScope));
                  } else {
                    return Stream.empty();
                  }
                })
            .toList();

    if (found.size() == 1) {
      return found.get(0).resolutionResult;
    } else if (found.size() > 1) {
      return onMultipleDefinitionsFromImports(methodName, found);
    } else {
      return null;
    }
  }

  /** Locates the module scope in which the provided type was defined. */
  protected abstract ModuleScopeType findDefinitionScope(TypeScopeReferenceType type);

  /**
   * Checks if an import scope brings in the requested method, and if so, returns it.
   *
   * <p>The method may be brought by the import scope either if the imported module defined it, or
   * if it re-exported it from some other module.
   */
  protected abstract FunctionType findExportedMethodInImportScope(
      ImportExportScopeType importExportScope, TypeScopeReferenceType type, String methodName);

  /**
   * Defines the behaviour when a method resolving to distinct results is found in multiple imports.
   */
  protected abstract FunctionType onMultipleDefinitionsFromImports(
      String methodName, List<MethodFromImport<FunctionType, ImportExportScopeType>> imports);

  /**
   * Represents a method found in an import scope.
   *
   * @param resolutionResult the result of the resolution
   * @param origin the scope in which it was found
   */
  protected record MethodFromImport<FunctionType, ImportExportScopeType>(
      FunctionType resolutionResult, ImportExportScopeType origin) {}
}
