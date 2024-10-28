package org.enso.compiler.pass.analyse.types;

import java.util.List;
import org.enso.compiler.common_logic.MethodResolutionAlgorithm;
import org.enso.compiler.pass.analyse.types.scope.BuiltinsFallbackScope;
import org.enso.compiler.pass.analyse.types.scope.ModuleResolver;
import org.enso.compiler.pass.analyse.types.scope.StaticImportExportScope;
import org.enso.compiler.pass.analyse.types.scope.StaticModuleScope;
import org.enso.compiler.pass.analyse.types.scope.TypeHierarchy;
import org.enso.compiler.pass.analyse.types.scope.TypeScopeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A helper that deals with resolving types of method calls. */
class MethodTypeResolver {
  private static final Logger logger = LoggerFactory.getLogger(MethodTypeResolver.class);
  private final ModuleResolver moduleResolver;
  private final TypeHierarchy typeHierarchy = new TypeHierarchy();
  private final BuiltinsFallbackScope builtinsFallbackScope;
  private final StaticMethodResolution methodResolutionAlgorithm;

  MethodTypeResolver(
      ModuleResolver moduleResolver,
      StaticModuleScope currentModuleScope,
      BuiltinTypes builtinTypes) {
    this.moduleResolver = moduleResolver;
    this.builtinsFallbackScope = new BuiltinsFallbackScope(builtinTypes);
    this.methodResolutionAlgorithm = new StaticMethodResolution(currentModuleScope);
  }

  TypeRepresentation resolveMethod(TypeScopeReference type, String methodName) {
    var definition = methodResolutionAlgorithm.lookupMethodDefinition(type, methodName);
    if (definition != null) {
      return definition;
    }

    // If not found in current scope, try parents
    var parent = typeHierarchy.getParent(type);
    if (parent == null) {
      return null;
    }

    return resolveMethod(parent, methodName);
  }

  private final class StaticMethodResolution
      extends MethodResolutionAlgorithm<
          TypeRepresentation, TypeScopeReference, StaticImportExportScope, StaticModuleScope> {
    StaticMethodResolution(StaticModuleScope currentModuleScope) {
      super(currentModuleScope);
    }

    @Override
    protected StaticModuleScope findDefinitionScope(TypeScopeReference typeScopeReference) {
      var definitionModule = moduleResolver.findContainingModule(typeScopeReference);
      if (definitionModule != null) {
        return StaticModuleScope.forIR(definitionModule);
      } else {
        if (typeScopeReference.equals(TypeScopeReference.ANY)) {
          // We have special handling for ANY: it points to Standard.Base.Any.Any, but that may not
          // always be imported.
          // The runtime falls back to Standard.Builtins.Main, but that modules does not contain any
          // type information, so it is not useful for us.
          // Instead we fall back to the hardcoded definitions of the 5 builtins of Any.
          return builtinsFallbackScope.fallbackAnyScope();
        } else {
          logger.error("Could not find declaration module of type: {}", typeScopeReference);
          return null;
        }
      }
    }

    @Override
    protected TypeRepresentation getMethodForTypeFromScope(
        StaticImportExportScope scope, TypeScopeReference typeScopeReference, String methodName) {
      return scope.materialize(moduleResolver).getMethodForType(typeScopeReference, methodName);
    }

    @Override
    protected TypeRepresentation getExportedMethodFromScope(
        StaticImportExportScope scope, TypeScopeReference typeScopeReference, String methodName) {
      return findExportedMethodInModule(
          scope.materialize(moduleResolver).getReferredModuleScope(),
          typeScopeReference,
          methodName);
    }

    @Override
    protected TypeRepresentation onMultipleDefinitionsFromImports(
        String methodName,
        List<MethodFromImport<TypeRepresentation, StaticImportExportScope>> methodFromImports) {
      if (logger.isDebugEnabled()) {
        var foundImportNames = methodFromImports.stream().map(MethodFromImport::origin);
        logger.debug("Method {} is coming from multiple imports: {}", methodName, foundImportNames);
      }

      long foundTypesCount =
          methodFromImports.stream().map(MethodFromImport::resolutionResult).distinct().count();
      if (foundTypesCount > 1) {
        List<String> foundTypesWithOrigins =
            methodFromImports.stream()
                .distinct()
                .map(m -> m.resolutionResult() + " from " + m.origin())
                .toList();
        logger.error(
            "Method {} is coming from multiple imports with different types: {}",
            methodName,
            foundTypesWithOrigins);
        return null;
      } else {
        // If all types are the same, just return the first one
        return methodFromImports.get(0).resolutionResult();
      }
    }
  }
}
