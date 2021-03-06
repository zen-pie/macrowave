package com.github.zenpie.macrowave.internal.scanner

import java.util.Random

import scala.collection.mutable.ArrayBuffer

case class DecomposedSparseMatrix(
  X     : Array[Int],
  Y     : Array[Int],
  value : Array[Int],
  column: Array[Int],
  row   : Array[Int]
)

object DecomposedSparseMatrix {

  def compress(automaton: FiniteAutomaton): DecomposedSparseMatrix = {
    val (x, y, r)            = decomposeMatrix(automaton.table)
    val (value, column, row) = compressSparseTable(r)

    DecomposedSparseMatrix(x, y, value, column, row)
  }

  /**
    * @see http://www.netlib.org/utk/people/JackDongarra/etemplates/node373.html
    */
  private def compressSparseTable(sparse: Array[Array[Int]]): (Array[Int], Array[Int], Array[Int]) = {
    val value  = ArrayBuffer[Int]()
    val column = ArrayBuffer[Int]()
    val row    = ArrayBuffer[Int]()

    var y = 0
    while (y < sparse.length) {

      var x = 0
      var found = false
      var start = 0
      while (x < sparse(y).length) {

        if (sparse(y)(x) != 0) {
          value += sparse(y)(x)
          column += x
          if (!found) {
            start = column.length - 1
            found = true
          }
        }

        x += 1
      }

      row += (if (found) start else -1)
      y   += 1
    }

    (value.toArray, column.toArray, row.toArray)
  }

  /**
    * @see http://sourcedb.ict.cas.cn/cn/ictthesis/201103/P020110314767959510274.pdf
    */
  private def decomposeMatrix(A: Array[Array[Int]]): (Array[Int], Array[Int], Array[Array[Int]]) = {
    val m   = A.length
    val n   = Char.MaxValue
    val rnd = new Random()
    val X   = Array.fill[Int](m)(rnd.nextInt())
    val Y   = Array.fill[Int](n)(rnd.nextInt())

    var changed = false

    var i  = 0
    var j  = 0
    val Di = new Array[Int](n)
    val Dj = new Array[Int](m)

    do {
      changed = false

      i = 0
      while (i < m) {

        j = 0
        while (j < n) {

          Di(j) = A(i)(j) - Y(j)
          j += 1
        }

        /* find most frequent element in Di */
        val x = getMostFrequentElement(Di)
        if (occurrences(x, Di) > occurrences(X(i), Di)) {
          X(i) = x
          changed = true
        }

        i += 1
      }

      j = 0
      while (j < n) {

        i = 0
        while (i < m) {

          Dj(i) = A(i)(j) - X(i)
          i += 1
        }

        /* find most frequent element in Dj */
        val y = getMostFrequentElement(Dj)
        if (occurrences(y, Dj) > occurrences(Y(j), Dj)) {
          Y(j) = y
          changed = true
        }

        j += 1
      }
    } while (changed)

    val R = Array.tabulate[Array[Int]](A.length) { i =>
      Array.tabulate(A(i).length) { j =>
        A(i)(j) - X(i) - Y(j)
      }
    }

    (X, Y, R)
  }

  private def getMostFrequentElement(array: Array[Int]): Int = {
    val map = scala.collection.mutable.Map.empty[Int, Int]
    var s   = array(0)

    var i = 0
    while (i < array.length) {
      val ele  = array(i)
      val cnt  = map.getOrElseUpdate(ele, 0)
      map(ele) = cnt + 1

      if (map(s) < map(ele)) {
        s = ele
      }
      i += 1
    }

    s
  }

  private def occurrences(x: Int, S: Array[Int]): Int = {
    var counter = 0
    var i       = 0

    while (i < S.length) {
      if (x == S(i)) {
        counter += 1
      }
      i += 1
    }
    
    counter
  }

}
