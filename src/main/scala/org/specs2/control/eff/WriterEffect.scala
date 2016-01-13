package org.specs2.control.eff

//import org.specs2.control.eff.Effects._

import scala.collection.mutable._
import cats._, data._
import cats.syntax.semigroup._
import Tag._
import Eff._
import Interpret._

/**
 * Effect for logging values alongside computations
 *
 * Compared to traditional Writer monad which accumulates values by default
 * this effect can be interpreted in different ways:
 *
 *  - log values to the console or to a file as soon as they are produced
 *  - accumulate values in a list
 *
 * Several writer effects can be used in the same stack if they are tagged.
 *
 */
object WriterEffect {

  /** write a given value */
  def tell[R, O](o: O)(implicit member: Member[Writer[O, ?], R]): Eff[R, Unit] =
    send[Writer[O, ?], R, Unit](Writer(o, ()))

  /** write a given value */
  def tellTagged[R, T, O](o: O)(implicit member: Member[({type l[X] = Writer[O, X] @@ T})#l, R]): Eff[R, Unit] =
    send[({type l[X] = Writer[O, X] @@ T})#l, R, Unit](Tag(Writer(o, ())))

  /**
   * run a writer effect and return the list of written values
   *
   * This uses a ListBuffer internally to append values
   */
  def runWriter[R <: Effects, U <: Effects, O, A, B](w: Eff[R, A])(implicit m: Member.Aux[Writer[O, ?], R, U]): Eff[U, (A, List[O])] =
    runWriterFold(w)(ListFold)

  /**
   * More general fold of runWriter where we can use a fold to accumulate values in a mutable buffer
   */
  def runWriterFold[R <: Effects, U <: Effects, O, A, B](w: Eff[R, A])(fold: Fold[O, B])(implicit m: Member.Aux[Writer[O, ?], R, U]): Eff[U, (A, B)] = {
    val recurse: StateRecurse[Writer[O, ?], A, (A, B)] = new StateRecurse[Writer[O, ?], A, (A, B)] {
      type S = fold.S
      val init = fold.init
      def apply[X](x: Writer[O, X], s: S) = (x.run._2, fold.fold(x.run._1, s))
      def finalize(a: A, s: S) = (a, fold.finalize(s))
    }

    interpretState1[R, U, Writer[O, ?], A, (A, B)]((a: A) => (a, fold.finalize(fold.init)))(recurse)(w)
  }

  /**
   * run a tagged writer effect
   */
  def runTaggedWriter[R <: Effects, U <: Effects, T, O, A](w: Eff[R, A])(implicit m: Member.Aux[({type l[X] = Writer[O, X] @@ T})#l, R, U]): Eff[U, (A, List[O])] =
    runTaggedWriterFold(w)(ListFold)

  def runTaggedWriterFold[R <: Effects, U <: Effects, T, O, A, B](w: Eff[R, A])(fold: Fold[O, B])(implicit
        m: Member.Aux[({type l[X] = Writer[O, X] @@ T})#l, R, U]): Eff[U, (A, B)] = {
    type W[X] = Writer[O, X] @@ T

    val recurse = new StateRecurse[W, A, (A, B)] {
      type S = fold.S
      val init = fold.init

      def apply[X](xt: W[X], s: S): (X, S) =
        Tag.unwrap(xt) match {
          case x => (x.run._2, fold.fold(x.run._1, s))
        }

      def finalize(a: A, s: S): (A, B) =
        (a, fold.finalize(s))
    }

    interpretState1[R, U, W, A, (A, B)]((a: A) => (a, fold.finalize(fold.init)))(recurse)(w)
  }

  /** support trait for folding values while possibly keeping some internal state */
  trait Fold[A, B] {
    type S
    val init: S
    def fold(a: A, s: S): S
    def finalize(s: S): B
  }

  implicit def ListFold[A]: Fold[A, List[A]] = new Fold[A, List[A]] {
    type S = ListBuffer[A]
    val init = new ListBuffer[A]
    def fold(a: A, s: S) = { s.append(a); s }
    def finalize(s: S) = s.toList
  }

  def MonoidFold[A : Monoid]: Fold[A, A] = new Fold[A, A] {
    type S = A
    val init = Monoid[A].empty
    def fold(a: A, s: S) = a |+| s
    def finalize(s: S) = s
  }

//  implicit def WriterMemberZero[A]: Member.Aux[Writer[A, ?], Writer[A, ?] |: NoEffect, NoEffect] =
//    Member.infer[Writer[A, ?], Writer[A, ?] |: NoEffect, NoEffect, Zero](MemberNat.ZeroMemberNat[Writer[A, ?], NoEffect], P[Zero])
//
//  implicit def WriterMemberN[R <: Effects, A]: Member.Aux[Writer[A, ?], Writer[A, ?] |: R, R] =
//    Member.infer[Writer[A, ?], Writer[A, ?] |: R, R, Zero](MemberNat.ZeroMemberNat[Writer[A, ?], R], P[Zero])

}
