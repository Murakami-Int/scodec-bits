package scodec.bits

import scala.quoted._

/**
  * Provides the `hex` string interpolator, which returns `ByteVector` instances from hexadecimal strings.
  * 
  * @example {{{
  * scala> val b = hex"deadbeef"
  * val b: scodec.bits.ByteVector = ByteVector(4 bytes, 0xdeadbeef)
  * }}}
  */
inline def (inline ctx: StringContext).hex (inline args: Any*): ByteVector =
  ${Literals.validate(Literals.Hex, 'ctx, 'args)}

/**
  * Provides the `bin` string interpolator, which returns `BitVector` instances from binary strings.
  *
  * @example {{{
  * scala> val b = bin"1010101010"
  * val b: scodec.bits.BitVector = BitVector(10 bits, 0xaa8)
  * }}}
  */
inline def (inline ctx: StringContext).bin (inline args: Any*): BitVector =
  ${Literals.validate(Literals.Bin, 'ctx, 'args)}

object Literals {

  trait Validator[A] {
    def validate(s: String): Option[String]
    def build(s: String)(using QuoteContext): Expr[A]
  }

  def validate[A](validator: Validator[A], strCtxExpr: Expr[StringContext], argsExpr: Expr[Seq[Any]])(using QuoteContext): Expr[A] = {
    strCtxExpr.unlift match {
      case Some(sc) => validate(validator, sc.parts, argsExpr)
      case None =>
        report.error("StringContext args must be statically known")
        ???
    }
  }

  private def validate[A](validator: Validator[A], parts: Seq[String], argsExpr: Expr[Seq[Any]])(using QuoteContext): Expr[A] = {
    if (parts.size == 1) {
      val literal = parts.head
      validator.validate(literal) match {
        case Some(err) =>
          report.error(err)
          ???
        case None =>
          validator.build(literal)
      }
    } else {
      report.error("interpolation not supported", argsExpr)
      ???
    }
  }

  object Hex extends Validator[ByteVector] {
    def validate(s: String): Option[String] =
      ByteVector.fromHex(s).fold(Some("hexadecimal string literal may only contain characters [0-9a-fA-f]"))(_ => None)
    def build(s: String)(using QuoteContext): Expr[ByteVector] =
      '{ByteVector.fromValidHex(${Expr(s)})},
  }    

  object Bin extends Validator[BitVector] {
    def validate(s: String): Option[String] =
      ByteVector.fromBin(s).fold(Some("binary string literal may only contain characters [0, 1]"))(_ => None)
    def build(s: String)(using QuoteContext): Expr[BitVector] =
      '{BitVector.fromValidBin(${Expr(s)})},
  }
}
