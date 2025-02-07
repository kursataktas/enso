package org.enso.compiler.pass.analyse

import org.enso.compiler.context.{InlineContext, ModuleContext}
import org.enso.compiler.core.Implicits.AsMetadata
import org.enso.compiler.core.ir.{Expression, Module, Name}
import org.enso.compiler.core.ir.module.scope.Definition
import org.enso.compiler.core.ir.module.scope.definition
import org.enso.compiler.core.ir.module.scope.imports
import org.enso.compiler.core.ir.MetadataStorage.MetadataPair
import org.enso.compiler.data.BindingsMap
import org.enso.compiler.data.BindingsMap.{
  ConversionMethod,
  ExtensionMethod,
  ModuleMethod
}
import org.enso.compiler.pass.IRPass
import org.enso.compiler.pass.desugar.{
  ComplexType,
  FunctionBinding,
  GenerateMethodBodies
}
import org.enso.compiler.pass.resolve.{
  MethodDefinitions,
  ModuleAnnotations,
  Patterns
}

/** Recognizes all defined bindings in the current module and constructs
  * a mapping data structure that can later be used for symbol resolution.
  */
case object BindingAnalysis extends IRPass {

  override type Metadata = BindingsMap

  /** The type of configuration for the pass. */
  override type Config = IRPass.Configuration.Default

  /** The passes that this pass depends _directly_ on to run. */
  override lazy val precursorPasses: Seq[IRPass] =
    Seq(ComplexType, FunctionBinding, GenerateMethodBodies)

  /** The passes that are invalidated by running this pass. */
  override lazy val invalidatedPasses: Seq[IRPass] =
    Seq(MethodDefinitions, Patterns)

  /** Executes the pass on the provided `ir`, and returns a possibly transformed
    * or annotated version of `ir`.
    *
    * @param ir            the Enso IR to process
    * @param moduleContext a context object that contains the information needed
    *                      to process a module
    * @return `ir`, possibly having made transformations or annotations to that
    *         IR.
    */
  override def runModule(
    ir: Module,
    moduleContext: ModuleContext
  ): Module = {
    val definedSumTypes = ir.bindings.collect { case sumType: Definition.Type =>
      val isBuiltinType = sumType
        .getMetadata(ModuleAnnotations)
        .exists(_.annotations.exists(_.name == "@Builtin_Type"))
      BindingsMap.Type.fromIr(sumType, isBuiltinType)
    }
    val importedPolyglot = ir.imports.collect { case poly: imports.Polyglot =>
      BindingsMap.PolyglotSymbol(poly.getVisibleName)
    }
    val staticMethods: List[BindingsMap.Method] = ir.bindings.collect {
      case method: definition.Method.Explicit =>
        val ref = method.methodReference
        ref.typePointer match {
          case Some(Name.Qualified(List(), _, _)) =>
            Some(ModuleMethod(ref.methodName.name))
          case Some(Name.Qualified(List(n), _, _)) =>
            val shadowed = definedSumTypes.exists(_.name == n.name)
            if (!shadowed && n.name == moduleContext.getName().item)
              Some(ModuleMethod(ref.methodName.name))
            else {
              Some(
                ExtensionMethod(ref.methodName.name, n.name)
              )
            }
          case Some(literal: Name.Literal) =>
            val shadowed = definedSumTypes.exists(_.name == literal.name)
            if (!shadowed && literal.name == moduleContext.getName().item)
              Some(ModuleMethod(ref.methodName.name))
            else {
              Some(
                ExtensionMethod(ref.methodName.name, literal.name)
              )
            }
          case None => Some(ModuleMethod(ref.methodName.name))
          case _    => None
        }
      case conversion: definition.Method.Conversion =>
        val targetTpNameOpt = conversion.typeName match {
          case Some(targetTpName) =>
            Some(targetTpName.name)
          case None => None
        }
        val sourceTpNameOpt = conversion.sourceTypeName match {
          case name: Name =>
            Some(name.name)
          case _ => None
        }
        (sourceTpNameOpt, targetTpNameOpt) match {
          case (Some(sourceTpName), Some(targetTpName)) =>
            Some(
              ConversionMethod(
                conversion.methodName.name,
                sourceTpName,
                targetTpName
              )
            )
          case _ => None
        }
    }.flatten
    ir.updateMetadata(
      new MetadataPair(
        this,
        BindingsMap(
          definedSumTypes ++ importedPolyglot ++ staticMethods,
          moduleContext.moduleReference()
        )
      )
    )
  }

  /** Executes the pass on the provided `ir`, and returns a possibly transformed
    * or annotated version of `ir` in an inline context.
    *
    * @param ir            the Enso IR to process
    * @param inlineContext a context object that contains the information needed
    *                      for inline evaluation
    * @return `ir`, possibly having made transformations or annotations to that
    *         IR.
    */
  override def runExpression(
    ir: Expression,
    inlineContext: InlineContext
  ): Expression = ir
}
