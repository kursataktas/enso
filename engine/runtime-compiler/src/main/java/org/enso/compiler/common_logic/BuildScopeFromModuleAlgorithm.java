package org.enso.compiler.common_logic;

import org.enso.compiler.MetadataInteropHelpers;
import org.enso.compiler.core.ir.Module;
import org.enso.compiler.core.ir.module.scope.Definition;
import org.enso.compiler.core.ir.module.scope.definition.Method;
import org.enso.compiler.core.ir.module.scope.imports.Polyglot;
import org.enso.compiler.data.BindingsMap;
import org.enso.compiler.pass.resolve.MethodDefinitions$;
import scala.jdk.javaapi.CollectionConverters;

/**
 * Gathers the common logic for building the ModuleScope.
 *
 * <p>This is done in two places: - in the compiler, gathering just the types to build
 * StaticModuleScope, - in the runtime, building Truffle nodes for the interpreter.
 *
 * <p>The interpreter does much more than the compiler, so currently this only gather the general
 * shape of the process to try to ensure that they stay in sync. In future iterations, we may try to
 * move more of the logic to this common place.
 */
public abstract class BuildScopeFromModuleAlgorithm<
    FunctionType,
    TypeScopeReferenceType,
    ImportExportScopeType,
    ModuleScopeType extends
        CommonModuleScopeShape<FunctionType, TypeScopeReferenceType, ImportExportScopeType>,
    ModuleScopeBuilderType extends
        CommonModuleScopeShape.Builder<
                FunctionType, TypeScopeReferenceType, ImportExportScopeType, ModuleScopeType>> {
  protected final ModuleScopeBuilderType scopeBuilder;

  protected BuildScopeFromModuleAlgorithm(ModuleScopeBuilderType scopeBuilder) {
    this.scopeBuilder = scopeBuilder;
  }

  public void processModule(Module moduleIr, BindingsMap bindingsMap) {
    // TODO common logic for generateReexportBindings?

    processModuleExports(bindingsMap);
    processModuleImports(bindingsMap);
    processPolyglotImports(moduleIr);

    processBindings(moduleIr);
  }

  private void processModuleExports(BindingsMap bindingsMap) {
    for (var exportedMod :
        CollectionConverters.asJavaCollection(bindingsMap.getDirectlyExportedModules())) {
      ImportExportScopeType exportScope = buildExportScope(exportedMod);
      scopeBuilder.addExport(exportScope);
    }
  }

  private void processModuleImports(BindingsMap bindingsMap) {
    for (var imp : CollectionConverters.asJavaCollection(bindingsMap.resolvedImports())) {
      for (var target : CollectionConverters.asJavaCollection(imp.targets())) {
        if (target instanceof BindingsMap.ResolvedModule resolvedModule) {
          var importScope = buildImportScope(imp, resolvedModule);
          scopeBuilder.addImport(importScope);
        }
      }
    }
  }

  private void processPolyglotImports(Module moduleIr) {
    for (var imp : CollectionConverters.asJavaCollection(moduleIr.imports())) {
      if (imp instanceof Polyglot polyglotImport) {
        if (polyglotImport.entity() instanceof Polyglot.Java javaEntity) {
          processPolyglotJavaImport(polyglotImport.getVisibleName(), javaEntity.getJavaName());
        } else {
          throw new IllegalStateException(
              "Unsupported polyglot import entity: " + polyglotImport.entity());
        }
      }
    }
  }

  protected abstract void processPolyglotJavaImport(String visibleName, String javaClassName);

  private void processBindings(Module module) {
    for (var binding : CollectionConverters.asJavaCollection(module.bindings())) {
      switch (binding) {
        case Definition.Type typ -> processTypeDefinition(typ);
        case Method.Explicit method -> processMethodDefinition(method);
        case Method.Conversion conversion -> processConversion(conversion);
        default -> System.out.println(
            "Unexpected binding type: " + binding.getClass().getCanonicalName());
      }
    }
  }

  // In the future we may want to extract some common logic from this, but for now we allow the
  // implementation to specify this.
  protected abstract void processConversion(Method.Conversion conversion);

  protected abstract void processMethodDefinition(Method.Explicit method);

  // The type registration (registering constructors, getters) is really complex, ideally we'd also
  // like to extract some common logic from it. But the differences are very large, so setting that
  // for later.
  protected abstract void processTypeDefinition(Definition.Type typ);

  protected final TypeScopeReferenceType getTypeAssociatedWithMethod(Method.Explicit method) {
    var typePointerOpt = method.methodReference().typePointer();
    if (typePointerOpt.isEmpty()) {
      return scopeBuilder.getAssociatedType();
    } else {
      var metadata =
          MetadataInteropHelpers.getMetadataOrNull(
              typePointerOpt.get(), MethodDefinitions$.MODULE$, BindingsMap.Resolution.class);
      if (metadata == null) {
        // return null?
        throw new IllegalStateException(
            "Failed to resolve type pointer for method: " + method.methodReference().showCode());
      }

      boolean isStatic = method.isStatic();
      return switch (metadata.target()) {
        case BindingsMap.ResolvedType resolvedType -> associatedTypeFromResolvedType(
            resolvedType, isStatic);
        case BindingsMap.ResolvedModule resolvedModule -> {
          assert !isStatic;
          yield associatedTypeFromResolvedModule(resolvedModule);
        }
        default -> throw new IllegalStateException(
            "Unexpected target type: " + metadata.target().getClass().getCanonicalName());
      };
    }
  }

  protected abstract TypeScopeReferenceType associatedTypeFromResolvedModule(
      BindingsMap.ResolvedModule module);

  protected abstract TypeScopeReferenceType associatedTypeFromResolvedType(
      BindingsMap.ResolvedType type, boolean isStatic);

  protected abstract ImportExportScopeType buildExportScope(
      BindingsMap.ExportedModule exportedModule);

  protected abstract ImportExportScopeType buildImportScope(
      BindingsMap.ResolvedImport resolvedImport, BindingsMap.ResolvedModule resolvedModule);
}
