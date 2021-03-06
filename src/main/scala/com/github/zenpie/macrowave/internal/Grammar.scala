package com.github.zenpie.macrowave.internal

import com.github.zenpie.macrowave.internal.ids._
import com.github.zenpie.macrowave.internal.parser.{NonTerminalSymbol, TokenSymbol}

import scala.collection.mutable
import scala.reflect.macros.whitebox

final class Grammar(val c: whitebox.Context) {

  private[internal] type Tree = c.universe.Tree
  private[internal] type Position = c.universe.Position

  /* IDs */

  private[internal] val actionIdProvider = new IdProvider[ActionId](new ActionId(_))
  private[internal] val typeIdProvider = new IdProvider[TypeId](new TypeId(_))
  private[internal] val nonTerminalIdProvider = new IdProvider[NonTerminalId](new NonTerminalId(_))
  private[internal] val terminalIdProvider = new IdProvider[TerminalId](new TerminalId(_))
  private[internal] val scannerRuleIdProvider = new IdProvider[ScannerRuleId](new ScannerRuleId(_))

  /* positions */

  private[internal] val terminalPositions = mutable.Map[TerminalId, Position]()
  private[internal] val nonTerminalPositions = mutable.Map[NonTerminalId, Position]()

  /* actions */

  private[internal] val actions = mutable.Map[ActionId, Tree]()

  /* types of nonTerminals */

  private[internal] val types = mutable.Map[TypeId, Tree]()

  /* non-terminals */

  private[internal] val namedNonTerminals = mutable.Map[String, NonTerminalId]()
  private[internal] val nonTerminalNames  = mutable.Map[NonTerminalId, String]()
  private[internal] val nonTerminals = mutable.Map[NonTerminalId, parser.Rule]()

  private[internal] var startRule: NonTerminalId = _

  /* terminals */

  private[internal] val namedTerminals = mutable.Map[String, TerminalId]()
  private[internal] val terminalNames = mutable.Map[TerminalId, String]()
  private[internal] val terminals = mutable.Map[TerminalId, scanner.Rule]()

  private[internal] var whiteSpace = Option.empty[TerminalId]

  /* symbol strings */

  private[internal] val symbolStrings = mutable.Map[NonTerminalId, mutable.Set[parser.SymbolString]]()

  /* first and follows sets for each symbol / nullable symbols */

  private[internal] val firstSet  = mutable.Map[parser.Symbol, mutable.Set[parser.TerminalSymbol]]()
  private[internal] val followSet = mutable.Map[parser.Symbol, mutable.Set[parser.TerminalSymbol]]()
  private[internal] val nullable  = mutable.Set[parser.NonTerminalSymbol]()

  def dumpSymbolSets(): Unit = {
    for ((_, id) <- namedNonTerminals) {
      val name = nonTerminalNames(id)
      val symbol = NonTerminalSymbol(id)

      def symbolSetString(set: mutable.Set[parser.TerminalSymbol]): String =
        set.map {
          case TokenSymbol(id) => terminalNames(id)
          case x => x.toString
        }.mkString("{ ", ", ", " }")

      val followString = symbolSetString(followSet(symbol))
      val firstString  = symbolSetString(firstSet(symbol))

      val nullableString = if (nullable.contains(symbol)) "YES" else "NO"

      println(s"FOLLOW($name) = $followString")
      println(s"FIRST($name) = $firstString")
      println(s"NULLABLE($name) = $nullableString")
      println()
    }
  }

  /* auxiliary definitions */

  private[internal] val auxiliaryDefs = mutable.ArrayBuffer[Tree]()

}
