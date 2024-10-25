package org.enso.compiler.context;

import java.util.Collection;

public interface CommonModuleScopeShape<
    FunctionType, TypeScopeReferenceType, ImportExportScopeType> {
  FunctionType getMethodForType(TypeScopeReferenceType type, String methodName);

  Collection<ImportExportScopeType> getImports();
}
