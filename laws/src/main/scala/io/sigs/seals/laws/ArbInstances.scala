/*
 * Copyright 2016 Daniel Urban
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.sigs.seals
package laws

import shapeless._

import org.scalacheck.{ Arbitrary, Gen, Cogen }
import org.scalacheck.derive.Recursive

object ArbInstances extends ArbInstances

trait ArbInstances {

  implicit def arbEnvelope[A: Reified](implicit A: Arbitrary[A]): Arbitrary[Envelope[A]] = {
    Arbitrary {
      for {
        v <- A.arbitrary
      } yield Envelope(v)
    }
  }

  implicit def cogenEnvelope[A](implicit A: Cogen[A]): Cogen[Envelope[A]] = {
    Cogen.apply { (seed, env) =>
      A.perturb(seed, env.value).reseed(Envelope.hashSeed)
    }
  }

  implicit def arbReified[A](implicit r: Reified[A]): Arbitrary[Reified[A]] = Arbitrary {
    Gen.const(r)
  }

  implicit def cogenReified[A]: Cogen[Reified[A]] =
    cogenModel.contramap(_.model)

  implicit def arbAtomic[A](implicit a: Atomic[A]): Arbitrary[Atomic[A]] = Arbitrary {
    Gen.const(a)
  }

  implicit def cogenAtomic[A]: Cogen[Atomic[A]] =
    Cogen[Int].contramap(_.##)

  implicit def arbKleene[F[_]](implicit k: Kleene[F]): Arbitrary[Kleene[F]] = Arbitrary {
    Gen.const(k)
  }

  implicit def arbModelHlist(implicit arbM: Lazy[Arbitrary[Model]]): Arbitrary[Model.HList] = Arbitrary {
    Gen.oneOf(arbModelHnil.arbitrary, Gen.lzy(arbModelHcons(arbM).arbitrary))
  }

  implicit def arbModelHnil: Arbitrary[Model.HNil.type] = Arbitrary {
    Arbitrary.arbUnit.arbitrary.map(_ => Model.HNil)
  }

  implicit def arbModelHcons(implicit arbM: Lazy[Arbitrary[Model]]): Arbitrary[Model.HCons] = Arbitrary {
    for {
      sym <- Gen.alphaStr
      opt <- Gen.oneOf(true, false)
      h <- Gen.lzy(arbM.value.arbitrary)
      t <- Gen.lzy(arbModelHlist(arbM).arbitrary)
    } yield Model.HCons(Symbol(sym), opt, h, t)
  }

  implicit def arbModelCoproduct(implicit arbM: Lazy[Arbitrary[Model]]): Arbitrary[Model.Coproduct] = Arbitrary {
    Gen.oneOf(arbModelCnil.arbitrary, Gen.lzy(arbModelCcons(arbM).arbitrary))
  }

  implicit def arbModelCnil: Arbitrary[Model.CNil.type] = Arbitrary {
    Arbitrary.arbUnit.arbitrary.map(_ => Model.CNil)
  }

  implicit def arbModelCcons(implicit arbM: Lazy[Arbitrary[Model]]): Arbitrary[Model.CCons] = Arbitrary {
    for {
      sym <- Gen.alphaStr
      h <- Gen.lzy(arbM.value.arbitrary)
      t <- Gen.lzy(arbModelCoproduct(arbM).arbitrary)
    } yield Model.CCons(Symbol(sym), h, t)
  }

  implicit def arbModelVector(implicit arbM: Lazy[Arbitrary[Model]]): Arbitrary[Model.Vector] = Arbitrary {
    Gen.lzy(arbM.value.arbitrary).map(e => Model.Vector(e))
  }

  implicit def arbModelAtom: Arbitrary[Model.Atom] = Arbitrary {
    Gen.oneOf(core.Atomic.registry.values.toSeq)
  }

  implicit def recModel: Recursive[Model] =
    Recursive(arbModelAtom.arbitrary)

  implicit def arbModel: Arbitrary[Model] = {
    Arbitrary {
      Gen.sized { n =>
        Gen.resize(n / 2, Gen.lzy(_arbModel.arbitrary))
      }
    }
  }

  private[this] lazy val _arbModel: Arbitrary[Model] = {

    type ModelReprH = Model.HNil.type
    type ModelReprT = Model.HCons :+: Model.CNil.type :+: Model.CCons :+: Model.Atom :+: Model.Vector :+: CNil
    type ModelRepr = ModelReprH :+: ModelReprT

    implicit val modGen: Generic.Aux[Model, ModelRepr] = new Generic[Model] {
      type Repr = ModelRepr
      def from(r: Repr): Model =
        shapeless.ops.coproduct.Unifier[Repr].apply(r)
      def to(t: Model): Repr = t match {
        case Model.HNil => Inl(Model.HNil)
        case hc: core.Model.HCons => Inr(Inl(hc))
        case Model.CNil => Inr(Inr(Inl(Model.CNil)))
        case cc: core.Model.CCons => Inr(Inr(Inr(Inl(cc))))
        case a: core.Model.Atom => Inr(Inr(Inr(Inr(Inl(a)))))
        case v: core.Model.Vector => Inr(Inr(Inr(Inr(Inr(Inl(v))))))
      }
    }

    locally {
      import org.scalacheck.Shapeless._
      val arbModel = derivedArbitrary[Model](
        null : LowPriority, // scalastyle:ignore null
        org.scalacheck.derive.MkArbitrary[Model]
      )
      arbModel
    }
  }

  implicit def cogenModel: Cogen[Model] =
    Cogen[String].contramap(_.toString)
}
