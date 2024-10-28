package org.enso.compiler.pass.analyse.types.scope;

import static org.enso.compiler.MetadataInteropHelpers.getMetadata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.enso.compiler.MetadataInteropHelpers;
import org.enso.compiler.common_logic.BuildScopeFromModuleAlgorithm;
import org.enso.compiler.context.InlineContext;
import org.enso.compiler.context.ModuleContext;
import org.enso.compiler.core.IR;
import org.enso.compiler.core.ir.Expression;
import org.enso.compiler.core.ir.Module;
import org.enso.compiler.core.ir.module.scope.Definition;
import org.enso.compiler.core.ir.module.scope.definition.Method;
import org.enso.compiler.data.BindingsMap;
import org.enso.compiler.pass.IRPass;
import org.enso.compiler.pass.IRProcessingPass;
import org.enso.compiler.pass.analyse.BindingAnalysis$;
import org.enso.compiler.pass.analyse.types.InferredType;
import org.enso.compiler.pass.analyse.types.TypeInferenceSignatures;
import org.enso.compiler.pass.analyse.types.TypeRepresentation;
import org.enso.compiler.pass.analyse.types.TypeResolver;
import org.enso.compiler.pass.resolve.FullyQualifiedNames$;
import org.enso.compiler.pass.resolve.GlobalNames$;
import org.enso.compiler.pass.resolve.TypeNames$;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.collection.immutable.Seq;
import scala.jdk.javaapi.CollectionConverters;
import scala.jdk.javaapi.CollectionConverters$;

public class StaticModuleScopeAnalysis implements IRPass {
  public static final StaticModuleScopeAnalysis INSTANCE = new StaticModuleScopeAnalysis();
  private final Logger logger = LoggerFactory.getLogger(StaticModuleScopeAnalysis.class);

  private final TypeResolver typeResolver = new TypeResolver();

  private StaticModuleScopeAnalysis() {}

  @Override
  public String toString() {
    return "StaticModuleScopeAnalysis";
  }

  @Override
  public Seq<IRProcessingPass> precursorPasses() {
    List<IRProcessingPass> passes =
        List.of(
            GlobalNames$.MODULE$,
            BindingAnalysis$.MODULE$,
            FullyQualifiedNames$.MODULE$,
            TypeNames$.MODULE$,
            TypeInferenceSignatures.INSTANCE);
    return CollectionConverters.asScala(passes).toList();
  }

  @Override
  public Seq<IRProcessingPass> invalidatedPasses() {
    List<IRProcessingPass> passes = List.of();
    return CollectionConverters.asScala(passes).toList();
  }

  @Override
  public Module runModule(Module ir, ModuleContext moduleContext) {
    // This has a lot in common with IrToTruffle::processModule - we may want to extract some common
    // parts if it will make sense.
    StaticModuleScope.Builder scopeBuilder = new StaticModuleScope.Builder(moduleContext.getName());
    BindingsMap bindingsMap = getMetadata(ir, BindingAnalysis$.MODULE$, BindingsMap.class);

    BuildStaticModuleScope buildScopeAlgorithm = new BuildStaticModuleScope(scopeBuilder);
    buildScopeAlgorithm.processModule(ir, bindingsMap);
    StaticModuleScope scope = scopeBuilder.build();
    ir.passData().update(INSTANCE, scope);
    return ir;
  }

  @Override
  public Expression runExpression(Expression ir, InlineContext inlineContext) {
    // Nothing to do - this pass only works on module-level.
    return ir;
  }

  private final class BuildStaticModuleScope
      extends BuildScopeFromModuleAlgorithm<
          TypeRepresentation,
          TypeScopeReference,
          StaticImportExportScope,
          StaticModuleScope,
          StaticModuleScope.Builder> {
    private BuildStaticModuleScope(StaticModuleScope.Builder scope) {
      super(scope);
    }

    @Override
    protected void processPolyglotJavaImport(String visibleName, String javaClassName) {
      // Currently nothing to do here, as we don't resolve methods on Java types. Assigning them
      // with Any should be good enough.
      // TODO: we may want a test making sure that we don't do any false positive warnings
    }

    @Override
    protected void processConversion(Method.Conversion conversion) {
      // TODO conversion handling is not implemented yet in the type checker
    }

    @Override
    protected void processMethodDefinition(Method.Explicit method) {
      var typeScope = getTypeAssociatedWithMethod(method);
      if (typeScope == null) {
        logger.warn(
            "Failed to process method {}, because its type scope could not be resolved.",
            method.methodReference().showCode());
        return;
      }
      var typeFromSignature =
          MetadataInteropHelpers.getMetadataOrNull(
              method, TypeInferenceSignatures.INSTANCE, InferredType.class);
      var type = typeFromSignature != null ? typeFromSignature.type() : TypeRepresentation.UNKNOWN;
      var name = method.methodReference().methodName().name();
      scopeBuilder.registerMethod(typeScope, name, type);
    }

    @Override
    protected void processTypeDefinition(Definition.Type typ) {
      List<AtomType.Constructor> constructors =
          CollectionConverters$.MODULE$.asJava(typ.members()).stream()
              .map(
                  constructorDef ->
                      new AtomType.Constructor(
                          constructorDef.name().name(), constructorDef.isPrivate()))
              .toList();

      AtomType atomType = new AtomType(typ.name().name(), constructors);
      var qualifiedName = scopeBuilder.getModuleName().createChild(typ.name().name());
      var atomTypeScope = TypeScopeReference.atomType(qualifiedName);
      scopeBuilder.registerType(atomType);
      registerFieldGetters(scopeBuilder, atomTypeScope, typ);
    }

    @Override
    protected TypeScopeReference associatedTypeFromResolvedModule(
        BindingsMap.ResolvedModule module) {
      return TypeScopeReference.moduleAssociatedType(module.qualifiedName());
    }

    @Override
    protected TypeScopeReference associatedTypeFromResolvedType(
        BindingsMap.ResolvedType type, boolean isStatic) {
      return TypeScopeReference.atomType(type.qualifiedName(), isStatic);
    }

    @Override
    protected StaticImportExportScope buildExportScope(BindingsMap.ExportedModule exportedModule) {
      return new StaticImportExportScope(exportedModule.module().qualifiedName());
    }

    @Override
    protected StaticImportExportScope buildImportScope(
        BindingsMap.ResolvedImport resolvedImport, BindingsMap.ResolvedModule resolvedModule) {
      return new StaticImportExportScope(resolvedModule.qualifiedName());
    }
  }

  @Override
  public <T extends IR> T updateMetadataInDuplicate(T sourceIr, T copyOfIr) {
    return IRPass.super.updateMetadataInDuplicate(sourceIr, copyOfIr);
  }

  /**
   * Registers getters for fields of the given type.
   *
   * <p>This should be consistent with logic with AtomConstructor.collectFieldAccessors.
   */
  private void registerFieldGetters(
      StaticModuleScope.Builder scope,
      TypeScopeReference typeScope,
      Definition.Type typeDefinition) {
    HashMap<String, List<TypeRepresentation>> fieldTypes = new HashMap<>();
    for (var constructorDef : CollectionConverters$.MODULE$.asJava(typeDefinition.members())) {
      for (var argumentDef : CollectionConverters$.MODULE$.asJava(constructorDef.arguments())) {
        String fieldName = argumentDef.name().name();
        TypeRepresentation fieldType =
            argumentDef
                .ascribedType()
                .map(typeResolver::resolveTypeExpression)
                .getOrElse(() -> TypeRepresentation.UNKNOWN);
        fieldTypes.computeIfAbsent(fieldName, k -> new ArrayList<>()).add(fieldType);
      }
    }

    for (var entry : fieldTypes.entrySet()) {
      String fieldName = entry.getKey();
      TypeRepresentation mergedType = TypeRepresentation.buildSimplifiedSumType(entry.getValue());
      scope.registerMethod(typeScope, fieldName, mergedType);
    }
  }
}
