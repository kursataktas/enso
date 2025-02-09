package org.enso.compiler.pass

import org.slf4j.LoggerFactory
import org.enso.compiler.context.{InlineContext, ModuleContext}
import org.enso.compiler.core.ir.{Expression, Module}
import org.enso.compiler.core.CompilerError

// TODO [AA] In the future, the pass ordering should be _computed_ from the list
//  of available passes, rather than just verified.

/** A manager for compiler passes.
  *
  * It is responsible for verifying and executing passes (in groups) on the
  * compiler IR.
  *
  * @param passes the pass groups, must all be unique
  * @param passConfiguration the configuration for each pass in `passes`
  */
//noinspection DuplicatedCode
class PassManager(
  passes: List[PassGroup],
  passConfiguration: PassConfiguration
) {
  private val logger = LoggerFactory.getLogger(classOf[PassManager])
  val allPasses      = verifyPassOrdering(passes.flatMap(_.passes))

  /** Computes a valid pass ordering for the compiler.
    *
    * @param passes the input list of passes
    * @throws CompilerError if a valid pass ordering cannot be computed
    * @return a valid pass ordering for the compiler, based on `passes`
    */
  private def verifyPassOrdering(
    passes: List[IRProcessingPass]
  ): List[IRProcessingPass] = {
    var validPasses: Set[IRProcessingPass] = Set()

    passes.foreach(pass => {
      val prereqsSatisfied =
        pass.precursorPasses.forall(validPasses.contains(_))

      if (prereqsSatisfied) {
        validPasses += pass
      } else {
        val missingPrereqsStr =
          pass.precursorPasses.filterNot(validPasses.contains(_)).mkString(", ")

        throw new CompilerError(
          s"The pass ordering is invalid. $pass is missing valid results " +
          s"for: $missingPrereqsStr"
        )
      }

      pass.invalidatedPasses.foreach(p => validPasses -= p)
    })

    passes
  }

  /** Executes all pass groups on the [[Module]].
    *
    * @param ir the module to execute the compiler passes on
    * @param moduleContext the module context in which the passes are executed
    * @return the result of executing `passGroup` on `ir`
    */
  def runPassesOnModule(
    ir: Module,
    moduleContext: ModuleContext
  ): Module = {
    passes.foldLeft(ir)((ir, group) =>
      runPassesOnModule(ir, moduleContext, group)
    )
  }

  /** Executes the provided `passGroup` on the [[Module]].
    *
    * @param ir the module to execute the compiler passes on
    * @param moduleContext the module context in which the passes are executed
    * @param passGroup the group of passes being executed
    * @return the result of executing `passGroup` on `ir`
    */
  def runPassesOnModule(
    ir: Module,
    moduleContext: ModuleContext,
    passGroup: PassGroup
  ): Module = {
    if (!passes.contains(passGroup)) {
      throw new CompilerError("Cannot run an unvalidated pass group.")
    }

    val newContext =
      moduleContext.copy(passConfiguration = Some(passConfiguration))

    val passesWithIndex = passGroup.passes.zipWithIndex

    logger.debug(
      "runPassesOnModule[{}@{}]",
      moduleContext.getName(),
      moduleContext.module.getCompilationStage()
    )
    var pendingMiniPasses: List[MiniPassFactory] = List()
    def flushMiniPass(in: Module): Module = {
      if (pendingMiniPasses.nonEmpty) {
        val miniPasses = pendingMiniPasses.map(factory =>
          factory.createForModuleCompilation(newContext)
        )
        val combinedPass = miniPasses.fold(null)(MiniIRPass.combine)
        logger.trace("  flushing pending mini pass: {}", combinedPass)
        pendingMiniPasses = List()
        MiniIRPass.compile(classOf[Module], in, combinedPass)
      } else {
        in
      }
    }
    val res = passesWithIndex.foldLeft(ir) {
      case (intermediateIR, (pass, index)) => {
        pass match {
          case miniFactory: MiniPassFactory =>
            logger.trace(
              "  mini collected: {}",
              pass
            )
            val combiningPreventedBy = pendingMiniPasses.find { p =>
              p.invalidatedPasses.contains(miniFactory)
            }
            val irForRemainingMiniPasses = if (combiningPreventedBy.isDefined) {
              logger.trace(
                "  pass {} forces flush before {}",
                combiningPreventedBy.orNull,
                miniFactory
              )
              flushMiniPass(intermediateIR)
            } else {
              intermediateIR
            }
            pendingMiniPasses = pendingMiniPasses.appended(miniFactory)
            irForRemainingMiniPasses
          case megaPass: IRPass =>
            // TODO [AA, MK] This is a possible race condition.
            passConfiguration
              .get(megaPass)
              .foreach(c =>
                c.shouldWriteToContext = isLastRunOf(index, megaPass, passGroup)
              )
            val flushedIR = flushMiniPass(intermediateIR)
            logger.trace(
              "  mega running: {}",
              megaPass
            )
            megaPass.runModule(flushedIR, newContext)
        }
      }
    }
    flushMiniPass(res)
  }

  /** Executes all passes on the [[Expression]].
    *
    * @param ir the expression to execute the compiler passes on
    * @param inlineContext the inline context in which the passes are executed
    * @return the result of executing `passGroup` on `ir`
    */
  def runPassesInline(
    ir: Expression,
    inlineContext: InlineContext
  ): Expression = {
    passes.foldLeft(ir)((ir, group) =>
      runPassesInline(ir, inlineContext, group)
    )
  }

  /** Executes the provided `passGroup` on the [[Expression]].
    *
    * @param ir the expression to execute the compiler passes on
    * @param inlineContext the inline context in which the passes are executed
    * @param passGroup the group of passes being executed
    * @return the result of executing `passGroup` on `ir`
    */
  def runPassesInline(
    ir: Expression,
    inlineContext: InlineContext,
    passGroup: PassGroup
  ): Expression = {
    if (!passes.contains(passGroup)) {
      throw new CompilerError("Cannot run an unvalidated pass group.")
    }

    val newContext =
      inlineContext.copy(passConfiguration = Some(passConfiguration))

    val passesWithIndex = passGroup.passes.zipWithIndex

    passesWithIndex.foldLeft(ir) {
      case (intermediateIR, (pass, index)) => {

        pass match {
          case miniFactory: MiniPassFactory =>
            val miniPass = miniFactory.createForInlineCompilation(newContext)
            MiniIRPass.compile(classOf[Expression], intermediateIR, miniPass)
          case megaPass: IRPass =>
            // TODO [AA, MK] This is a possible race condition.
            passConfiguration
              .get(megaPass)
              .foreach(c =>
                c.shouldWriteToContext = isLastRunOf(index, megaPass, passGroup)
              )
            megaPass.runExpression(intermediateIR, newContext)
        }

      }
    }
  }

  /** Determines whether the run at index `indexOfPassInGroup` is the last run
    * of that pass in the overall pass ordering.
    *
    * @param indexOfPassInGroup the index of `pass` in `passGroup`
    * @param pass the pass to check for
    * @param passGroup the pass group being run
    * @return `true` if the condition holds, otherwise `false`
    */
  private def isLastRunOf(
    indexOfPassInGroup: Int,
    pass: IRPass,
    passGroup: PassGroup
  ): Boolean = {
    val ix          = allPasses.lastIndexOf(pass)
    val before      = passes.takeWhile(_ != passGroup)
    val totalLength = before.map(_.passes.length).sum

    ix - totalLength == indexOfPassInGroup
  }

  /** Updates the metadata in a copy of the IR when updating that metadata
    * requires global state.
    *
    * This is usually the case in the presence of structures that are shared
    * throughout the IR, and need to maintain that sharing for correctness. This
    * must be called with `copyOfIr` as the result of an `ir.duplicate` call.
    *
    * Additionally this method _must not_ alter the structure of the IR. It
    * should only update its metadata.
    *
    * @param sourceIr the IR being copied
    * @param copyOfIr a duplicate of `sourceIr`
    * @return the result of updating metadata in `copyOfIr` globally using
    *         information from `sourceIr`
    */
  def runMetadataUpdate(sourceIr: Module, copyOfIr: Module): Module = {
    allPasses.foldLeft(copyOfIr) {
      case (module, megaPass: IRPass) =>
        megaPass.updateMetadataInDuplicate(sourceIr, module)
      case (module, _) => module
    }
  }
}

/** A representation of a group of passes.
  *
  * @param passes the passes in the group
  */
class PassGroup(val passes: List[IRProcessingPass])
