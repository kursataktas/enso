package org.enso.compiler.pass.resolve

import org.enso.compiler.context.{InlineContext, ModuleContext}
import org.enso.compiler.core.Implicits.AsMetadata
import org.enso.compiler.core.ir.{Expression, Module}
import org.enso.compiler.core.ir.Name
import org.enso.compiler.core.ir.MetadataStorage.MetadataPair
import org.enso.compiler.core.ir.expression.Application
import org.enso.compiler.data.BindingsMap
import org.enso.compiler.data.BindingsMap.{
  Resolution,
  ResolvedModule,
  ResolvedModuleMethod
}
import org.enso.compiler.pass.IRPass
import org.enso.compiler.pass.analyse.BindingAnalysis

/** Resolves calls to methods defined on modules, called on direct (resolved)
  * module references.
  */
object MethodCalls extends IRPass {

  override type Metadata = BindingsMap.Resolution
  override type Config   = IRPass.Configuration.Default

  override lazy val precursorPasses: Seq[IRPass] =
    Seq(BindingAnalysis, GlobalNames)
  override lazy val invalidatedPasses: Seq[IRPass] = Seq()

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
    ir.mapExpressions(doExpression)
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
  ): Expression = {
    doExpression(ir)
  }

  private def doExpression(
    expr: Expression
  ): Expression = {
    expr.transformExpressions { case app: Application.Prefix =>
      def fallback = app.mapExpressions(doExpression(_))
      app.function match {
        case name: Name if name.isMethod =>
          app.arguments match {
            case selfArgument :: _ =>
              val targetBindings =
                selfArgument.value.getMetadata(GlobalNames) match {
                  case Some(Resolution(ResolvedModule(module))) =>
                    val moduleIr = module.unsafeAsModule().getIr
                    Option
                      .when(moduleIr != null)(
                        moduleIr.getMetadata(BindingAnalysis)
                      )
                      .flatten
                  case _ => None
                }
              targetBindings match {
                case Some(bindings) =>
                  val resolutionsOpt =
                    bindings.exportedSymbols.get(name.name)
                  val resolvedModuleMethodOpt = resolutionsOpt match {
                    case Some(resolutions) =>
                      resolutions.collectFirst { case x: ResolvedModuleMethod =>
                        x
                      }
                    case None => None
                  }
                  resolvedModuleMethodOpt match {
                    case Some(resolvedModuleMethod) =>
                      val newName =
                        name.updateMetadata(
                          new MetadataPair(
                            this,
                            Resolution(resolvedModuleMethod)
                          )
                        )
                      val newArgs =
                        app.arguments.map(
                          _.mapExpressions(doExpression(_))
                        )
                      app.copy(function = newName, arguments = newArgs)
                    case _ => fallback
                  }
                case _ => fallback
              }
            case _ => fallback
          }
        case _ => fallback
      }
    }
  }
}
