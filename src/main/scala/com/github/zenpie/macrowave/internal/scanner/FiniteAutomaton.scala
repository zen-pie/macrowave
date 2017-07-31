package com.github.zenpie.macrowave.internal.scanner

import com.github.zenpie.macrowave.internal.{Grammar, IntSet}
import com.github.zenpie.macrowave.internal.ids.{ScannerRuleId, Table, TerminalId}

import scala.collection._
import scala.collection.mutable.ArrayBuffer

case class FiniteAutomaton(
  table: Array[Array[Int]],
  start: Int,
  finals: java.util.BitSet
)

object FiniteAutomaton {

  private sealed trait Action
  private case object NoAction extends Action
  private case class TokenAction(id: TerminalId) extends Action

  private final type RuleTable[T] = Table[ScannerRuleId, T]

  private def time[T](msg: String)(f: => T): T = {
    val s = System.nanoTime()
    val r = f
    printf("took " + ((System.nanoTime() - s) / 10e+6) + "ms for '" + msg + "'\n")
    r
  }

  def generate(grammar: Grammar): FiniteAutomaton = {
    val actions = grammar.scannerRuleIdProvider.dataTable[Action](_ => NoAction)

    for ((id, rule) <- grammar.terminals) {
      if (!grammar.whiteSpace.contains(id)) {
        actions(rule.id) = TokenAction(id)
      }
    }

    val combined = grammar.terminals.values.reduceLeft(Alternate(grammar.scannerRuleIdProvider.next(), _, _))
    println(combined)

    val (dataSets, follow, lookup) = collect(grammar, combined)
    val (states, finals, sigma) = construct(combined, dataSets, follow, lookup)

    minimize(states, finals, sigma, actions)
  }

  private def minimize(states: Seq[IntSet],
                       finals: IntSet,
                       sigma: mutable.Map[IntSet, mutable.Map[IntRange, IntSet]],
                       actions: RuleTable[Action]): FiniteAutomaton = {
    val size = states.size
    def gaussianSum(n: Int) = ((n * n) + n) >> 1
    val pairs = new java.util.BitSet(gaussianSum(size - 1))

    def set(p: Int, q: Int): Unit =
      if (p != q) pairs.set(Math.max(p, q) + Math.min(p, q) * size)

    def get(p: Int, q: Int): Boolean =
      p != q && pairs.get(Math.max(p, q) + Math.min(p, q) * size)

    var x, y = 0
    while (x < size) {
      y = 0
      while (y < x) {
        if (finals.contains(x) != finals.contains(y)) {
          set(x, y)
        } /* TODO: if states(x) and states(y) have an action => mark them */
        y += 1
      }
      x += 1
    }

    var changed = false
    do {
      changed = false
      x = 0
      while (x < size) {
        y = 0
        while (y < x) {
          if (!get(x, y)) {
            val xt = sigma.getOrElseUpdate(states(x), mutable.Map())
            val yt = sigma.getOrElseUpdate(states(y), mutable.Map())
            if (xt.size != yt.size) {
              changed = true
              set(x, y)
            } else {
              for ((xc, xdst) <- xt) {
                if (yt.contains(xc)) {
                  val yi = states.indexOf(yt(xc))
                  val xi = states.indexOf(xdst)
                  if (get(xi, yi)) {
                    changed = true
                    set(x, y)
                  }
                } else {
                  changed = true
                  set(x, y)
                }
              }
            }
          }
          y += 1
        }
        x += 1
      }
    } while (changed)

    val equivClassMapping = new Array[mutable.Set[IntSet]](size)
    val equivClasses = ArrayBuffer[mutable.Set[IntSet]]()
    x = 0
    while (x < size) {
      y = 0
      while (y < x) {
        if (!get(x, y)) {
          /* states x and y may be merged together */
          if (equivClassMapping(y) ne null) {
            equivClassMapping(x) = equivClassMapping(y)
            equivClassMapping(y) += states(x)
          }
        }
        y += 1
      }
      if (equivClassMapping(x) eq null) {
        equivClassMapping(x) = mutable.Set(states(x))
        equivClasses += equivClassMapping(x)
      }
      x += 1
    }

    /* construct table */
    val table = Array.fill(equivClasses.size)(Array.fill(Char.MaxValue + 1)(-1))
    var i = 0
    while (i < equivClasses.size) {
      val ct = sigma(equivClasses(i).head)
      for ((r, dst) <- ct) {
        for (c <- r.from to r.to) {
          table(i)(c) = equivClasses.indexWhere(_.contains(dst))
        }
      }
      i += 1
    }

    val accepting = new java.util.BitSet(equivClasses.size)
    for (i <- equivClasses.indices) {
      if (finals.contains(i)) {
        accepting.set(i)
      }
    }

    FiniteAutomaton(table, 0, accepting)
  }

  private def printASCIITable(states: Seq[IntSet], sigma: mutable.Map[IntSet, mutable.Map[IntRange, IntSet]]): Unit = {
    print("  ")
    for (c <- 32 until 127) {
      print(c.toChar + " ")
    }
    println()
    sigma.foreach { case (src, edges) =>
      print(states.indexOf(src) + " ")
      for (c <- 32 until 127) {
        edges.find { case (key, value) => c >= key.from && c <= key.to } match {
          case Some((_, state)) => print(states.indexOf(state) + " ")
          case None => print("  ")
        }
      }
      println()
    }
  }

  private case class IntRange(from: Int, to: Int)
  private case class DataSet(var nullable: Boolean, firstPos: IntSet, lastPos: IntSet)

  private def construct(rule: Rule, dataSets: RuleTable[DataSet], follow: mutable.Map[Int, IntSet], lookup: mutable.Map[Int, IntRange]):
    (Seq[IntSet], IntSet, mutable.Map[IntSet, mutable.Map[IntRange, IntSet]]) = {
    val unmarked = mutable.Queue[IntSet](dataSets(rule.id).firstPos)
    val marked   = ArrayBuffer[IntSet]()
    val finals   = IntSet.empty
    val sigma    = mutable.Map[IntSet, mutable.Map[IntRange, IntSet]]()

    while (unmarked.nonEmpty) {
      val state = unmarked.dequeue()
      marked += state
      val edges = sigma.getOrElseUpdate(state, mutable.Map())

      for (position <- state) {
        val range = lookup(position)
        if (range.from == -1 && range.to == -1) {
          /* final state */
          finals += (marked.size - 1)
        } else {
          edges.getOrElseUpdate(range, IntSet.empty) ++= follow.getOrElseUpdate(position, IntSet.empty)
        }
      }

      for ((_, nstate) <- edges) {
        if (!unmarked.contains(nstate) && !marked.contains(nstate)) {
          unmarked.enqueue(nstate)
        }
      }
    }

    (marked, finals, sigma)
  }

  private def collect(grammar: Grammar, rule: Rule): (RuleTable[DataSet], mutable.Map[Int, IntSet], mutable.Map[Int, IntRange]) = {
    val dataSets = grammar.scannerRuleIdProvider.dataTable[DataSet](_ => DataSet(nullable = false, IntSet.empty, IntSet.empty))
    val follow = mutable.Map[Int, IntSet]()
    val lookup = mutable.Map[Int, IntRange]()
    var position = 0

    def helper(rule: Rule): Unit = rule match {
      case Alternate(id, l, r) =>
        helper(l); helper(r)
        val a = dataSets(id)
        val b = dataSets(l.id)
        val c = dataSets(r.id)

        a.nullable = b.nullable || c.nullable
        a.firstPos ++= b.firstPos
        a.firstPos ++= c.firstPos
        a.lastPos  ++= b.lastPos
        a.lastPos  ++= c.lastPos

      case Concatenate(id, l, r) =>
        helper(l); helper(r)
        val a = dataSets(id)
        val b = dataSets(l.id)
        val c = dataSets(r.id)

        a.nullable = b.nullable && c.nullable
        a.firstPos ++= b.firstPos
        if (b.nullable) {
          a.firstPos ++= c.firstPos
        }
        a.lastPos ++= c.lastPos
        if (c.nullable) {
          a.lastPos ++= b.lastPos
        }

        for (position <- b.lastPos) {
          follow.getOrElseUpdate(position, IntSet.empty) ++= c.firstPos
        }
      case r: Range =>
        val a = dataSets(r.id)

        a.nullable = false
        a.firstPos += position
        a.lastPos  += position

        lookup(position) = IntRange(r.from, r.to)
        position += 1

      case Kleene(id, r) =>
        helper(r)
        val a = dataSets(id)
        val b = dataSets(r.id)
        a.nullable = true
        a.firstPos ++= b.firstPos
        a.lastPos ++= b.lastPos

        for (i <- a.lastPos) {
          follow.getOrElseUpdate(i, IntSet.empty) ++= a.firstPos
        }

      case EmptyString(id) =>
        val a = dataSets(id)
        a.nullable = true
    }

    /* insert end-marker to be able to detect final states */
    val p = position
    lookup(p) = IntRange(-1, -1)
    position += 1
    helper(rule)
    for (i <- dataSets(rule.id).lastPos) {
      follow.getOrElseUpdate(i, IntSet.empty) += p
    }

    (dataSets, follow, lookup)
  }

}
