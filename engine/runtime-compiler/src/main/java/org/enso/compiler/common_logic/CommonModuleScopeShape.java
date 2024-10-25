package org.enso.compiler.common_logic;

import java.util.Collection;

public interface CommonModuleScopeShape<
    FunctionType, TypeScopeReferenceType, ImportExportScopeType> {
  FunctionType getMethodForType(TypeScopeReferenceType type, String methodName);

  Collection<ImportExportScopeType> getImports();

  interface Builder<FunctionType, TypeScopeReferenceType, ImportExportScopeType, ModuleScopeType extends CommonModuleScopeShape<FunctionType, TypeScopeReferenceType, ImportExportScopeType>> {
    ModuleScopeType build();

    void addExport(ImportExportScopeType exportScope);
    void addImport(ImportExportScopeType importScope);
  }
}
