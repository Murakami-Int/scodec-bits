package scodec.bits

import java.security.MessageDigest
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Prop.forAll
import Arbitraries._

class BitVectorJvmTest extends BitsSuite {
  implicit val arbitraryBitVector: Arbitrary[BitVector] = Arbitrary {
    Gen.oneOf(flatBytes, balancedTrees, splitVectors, concatSplitVectors, bitStreams)
  }

  property("sizeGreater/LessThan concurrent") {
    forAll { (x: BitVector) =>
      val ok = new java.util.concurrent.atomic.AtomicBoolean(true)
      def t =
        new Thread {
          override def start =
            (0 until x.size.toInt).foreach { i =>
              ok.compareAndSet(true, x.sizeGreaterThan(i.toLong))
              ()
            }
        }
      val t1 = t
      val t2 = t
      t1.start
      t2.start
      ok.compareAndSet(true, x.sizeLessThan(x.size + 1))
      t1.join
      t2.join
      assert(ok.get == true)
    }
  }

  property("digest") {
    forAll { (x: BitVector) =>
      val sha256 = MessageDigest.getInstance("SHA-256")
      assert(x.digest("SHA-256") == BitVector(ByteVector(sha256.digest(x.toByteArray))))
    }
  }

  property("serialization") {
    forAll((x: BitVector) => serializationShouldRoundtrip(x))
  }

}
