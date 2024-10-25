package org.enso.compiler.common_logic;

import org.enso.compiler.core.ir.Module;
import org.enso.compiler.core.ir.module.scope.Definition;
import org.enso.compiler.core.ir.module.scope.definition.Method;
import org.enso.compiler.core.ir.module.scope.imports.Polyglot;
import org.enso.compiler.data.BindingsMap;
import scala.jdk.javaapi.CollectionConverters;

/**
 * Gathers the common logic for building the ModuleScope.
 * <p>
 * This is done in two places:
 * - in the compiler, gathering just the types to build StaticModuleScope,
 * - in the runtime, building Truffle nodes for the interpreter.
 * <p>
 * The interpreter does much more than the compiler, so currently this only gather the general shape of the process to try to ensure that they stay in sync.
 * In future iterations, we may try to move more of the logic to this common place.
 */
public abstract class BuildScopeFromModuleAlgorithm<FunctionType, TypeScopeReferenceType, ImportExportScopeType, ModuleScopeType extends CommonModuleScopeShape<FunctionType, TypeScopeReferenceType, ImportExportScopeType>> {
  // TODO how do we pass the builder?
  CommonModuleScopeShape.Builder<FunctionType, TypeScopeReferenceType, ImportExportScopeType, ModuleScopeType> scopeBuilder;

  public void processModule(Module moduleIr, BindingsMap bindingsMap) {
    // TODO common logic for generateReexportBindings?

    processModuleExports(bindingsMap);
    processModuleImports(bindingsMap);
    processPolyglotImports(moduleIr);

    processBindings(moduleIr);
  }

  private void processModuleExports(BindingsMap bindingsMap) {
    for (var exportedMod : CollectionConverters.asJavaCollection(bindingsMap.getDirectlyExportedModules())) {
      ImportExportScopeType exportScope = buildExportScoe(exportedMod);
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
          // TODO
        } else {
          throw new IllegalStateException("Unsupported polyglot import entity: " + polyglotImport.entity());
        }
      }
    }
  }

  private void processBindings(Module module) {
    for (var binding : CollectionConverters.asJavaCollection(module.bindings())) {
      switch (binding) {
        case Definition.Type typ -> processType(typ);
        case Method.Explicit method -> processMethod(method);
        case Method.Conversion conversion -> processConversion(conversion);
        default -> System.out.println(
            "Unexpected binding type: " + binding.getClass().getCanonicalName());
      }
    }
  }

  private void processConversion(Method.Conversion conversion) {

  }

  private void processMethod(Method.Explicit method) {

  }

  private void processType(Definition.Type typ) {
    // TODO
  }

  protected abstract ImportExportScopeType buildExportScoe(BindingsMap.ExportedModule exportedModule);

  protected abstract ImportExportScopeType buildImportScope(BindingsMap.ResolvedImport resolvedImport, BindingsMap.ResolvedModule resolvedModule);
}
