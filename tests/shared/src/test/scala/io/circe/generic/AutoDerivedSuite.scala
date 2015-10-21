package io.circe.generic

import algebra.Eq
import cats.data.{ NonEmptyList, Validated, Xor }
import io.circe.{ Decoder, Encoder, Json }
import io.circe.generic.auto._
import io.circe.tests.{ CodecTests, CirceSuite }
import io.circe.tests.examples._
import org.scalacheck.{ Arbitrary, Gen }
import org.scalacheck.Prop.forAll
import shapeless.CNil

class AutoDerivedSuite extends CirceSuite {
  final case class InnerCaseClassExample(a: String, b: String, c: String, d: String)
  final case class OuterCaseClassExample(a: String, inner: InnerCaseClassExample)

  object InnerCaseClassExample {
    implicit val arbitraryInnerCaseClassExample: Arbitrary[InnerCaseClassExample] =
      Arbitrary(
        for {
          a <- Arbitrary.arbitrary[String]
          b <- Arbitrary.arbitrary[String]
          c <- Arbitrary.arbitrary[String]
          d <- Arbitrary.arbitrary[String]
        } yield InnerCaseClassExample(a, b, c, d)
      )
  }

  object OuterCaseClassExample {
    implicit val eqOuterCaseClassExample: Eq[OuterCaseClassExample] = Eq.fromUniversalEquals

    implicit val arbitraryOuterCaseClassExample: Arbitrary[OuterCaseClassExample] =
      Arbitrary(
        for {
          a <- Arbitrary.arbitrary[String]
          i <- Arbitrary.arbitrary[InnerCaseClassExample]
        } yield OuterCaseClassExample(a, i)
      )
  }

  sealed trait RecursiveAdtExample
  case class BaseAdtExample(a: String) extends RecursiveAdtExample
  case class NestedAdtExample(r: RecursiveAdtExample) extends RecursiveAdtExample

  object RecursiveAdtExample {
    implicit val eqRecursiveAdtExample: Eq[RecursiveAdtExample] = Eq.fromUniversalEquals

    private def atDepth(depth: Int): Gen[RecursiveAdtExample] = if (depth < 3)
      Gen.oneOf(
        Arbitrary.arbitrary[String].map(BaseAdtExample(_)),
        atDepth(depth + 1).map(NestedAdtExample(_))
      ) else Arbitrary.arbitrary[String].map(BaseAdtExample(_))

    implicit val arbitraryRecursiveAdtExample: Arbitrary[RecursiveAdtExample] =
      Arbitrary(atDepth(0))
  }

  checkAll("Codec[Tuple1[Int]]", CodecTests[Tuple1[Int]].codec)
  checkAll("Codec[(Int, Int, Foo)]", CodecTests[(Int, Int, Foo)].codec)
  checkAll("Codec[Qux[Int]]", CodecTests[Qux[Int]].codec)
  checkAll("Codec[Foo]", CodecTests[Foo].codec)
  checkAll("Codec[OuterCaseClassExample]", CodecTests[OuterCaseClassExample].codec)
  checkAll("Codec[RecursiveAdtExample]", CodecTests[RecursiveAdtExample].codec)

  test("Decoder[Int => Qux[String]]") {
    check {
      forAll { (i: Int, s: String) =>
        Json.obj("a" -> Json.string(s)).as[Int => Qux[String]].map(_(i)) === Xor.right(Qux(i, s))
      }
    }
  }

  test("Decoder[Qux[String] => Qux[String]]") {
    check {
      forAll { (q: Qux[String], i: Option[Int], a: Option[String]) =>
        val json = Json.obj(
          "i" -> Encoder[Option[Int]].apply(i),
          "a" -> Encoder[Option[String]].apply(a)
        )

        val expected = Qux[String](i.getOrElse(q.i), a.getOrElse(q.a))

        json.as[Qux[String] => Qux[String]].map(_(q)) === Xor.right(expected)
      }
    }
  }

  test("Generic instances should not interfere with base instances") {
    check {
      forAll { (is: List[Int]) =>
        val json = Encoder[List[Int]].apply(is)

        json === Json.fromValues(is.map(Json.int)) && json.as[List[Int]] === Xor.right(is)
      }
    }
  }

  test("Generic decoders should not interfere with defined decoders") {
    check {
      forAll { (xs: List[String]) =>
        val json = Json.obj("Baz" -> Json.fromValues(xs.map(Json.string)))

        Decoder[Foo].apply(json.hcursor) === Xor.right(Baz(xs): Foo)
      }
    }
  }

  test("Generic encoders should not interfere with defined encoders") {
    check {
      forAll { (xs: List[String]) =>
        val json = Json.obj("Baz" -> Json.fromValues(xs.map(Json.string)))

        Encoder[Foo].apply(Baz(xs): Foo) === json
      }
    }
  }

  test("Decoding with Decoder[CNil] should fail") {
    assert(Json.empty.as[CNil].isLeft)
  }

  test("Encoding with Encoder[CNil] should throw an exception") {
    intercept[RuntimeException](Encoder[CNil].apply(null: CNil))
  }
}