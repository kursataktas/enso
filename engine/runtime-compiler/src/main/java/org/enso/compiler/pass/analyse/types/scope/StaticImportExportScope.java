package org.enso.compiler.pass.analyse.types.scope;

import org.enso.compiler.pass.analyse.types.TypeRepresentation;
import org.enso.pkg.QualifiedName;

/** The static counterpart of {@link org.enso.interpreter.runtime.scope.ImportExportScope}. */
public class StaticImportExportScope {
  // TODO add support for only/hiding once https://github.com/enso-org/enso/issues/10796 is fixed
  private final QualifiedName referredModuleName;

  public StaticImportExportScope(QualifiedName referredModuleName) {
    this.referredModuleName = referredModuleName;
  }

  private transient MaterializedImportExportScope cachedMaterializedScope = null;

  public MaterializedImportExportScope materialize(ModuleResolver moduleResolver) {
    if (cachedMaterializedScope != null) {
      return cachedMaterializedScope;
    }

    var module = moduleResolver.findModule(referredModuleName);
    if (module == null) {
      throw new IllegalStateException("Could not find module: " + referredModuleName);
    }
    var moduleScope = StaticModuleScope.forIR(module);
    var materialized = new MaterializedImportExportScope(moduleScope);
    cachedMaterializedScope = materialized;
    return materialized;
  }

  // I'm not yet sure about this, but for PoC
  public static class MaterializedImportExportScope {
    private final StaticModuleScope referredModuleScope;

    private MaterializedImportExportScope(StaticModuleScope moduleScope) {
      this.referredModuleScope = moduleScope;
    }

    public TypeRepresentation getMethodForType(TypeScopeReference type, String name) {
      // TODO filtering only/hiding (see above) - for now we just return everything
      return referredModuleScope.getMethodForType(type, name);
    }

    public StaticModuleScope getReferredModuleScope() {
      return referredModuleScope;
    }
  }

  public QualifiedName getReferredModuleName() {
    return referredModuleName;
  }

  @Override
  public String toString() {
    return "StaticImportExportScope{" + referredModuleName + "}";
  }

  @Override
  public int hashCode() {
    return referredModuleName.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof StaticImportExportScope other)) {
      return false;
    }

    // TODO once hiding (see above) is added, these filters need to be added too
    return referredModuleName.equals(other.referredModuleName);
  }
}
