package scodec.bits

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.UUID

import Arbitraries._
import org.scalacheck._
import org.scalatest.matchers.should.Matchers._

class ByteVectorTest extends BitsSuite {

  test("hashCode/equals") {
    forAll(bytesWithIndex) {
      case (b, m) =>
        (b.take(m) ++ b.drop(m)) shouldBe b
        (b.take(m) ++ b.drop(m)).hashCode shouldBe b.hashCode
        if (b.take(3) == b.drop(3).take(3)) {
          // kind of weak, since this will only happen 1/8th of attempts on average
          b.take(3).hashCode shouldBe b.drop(3).take(3).hashCode
        }
    }
  }

  test("issue #90") {
    val x = ByteVector.fill(Int.MaxValue.toLong + 1)(0)
    val y = ByteVector.fill(Int.MaxValue.toLong + 1)(1)
    (x === y) shouldBe false
  }

  test("=== consistent with ==") {
    forAll { (b: ByteVector, b2: ByteVector) =>
      (b == b2) shouldBe (b === b2)
    }
  }

  test("compact is a no-op for already compact byte vectors") {
    val b = ByteVector(0x80)
    (b.compact eq b.compact) shouldBe true
  }

  test("reverse.reverse == id") {
    forAll { (b: ByteVector) =>
      b.reverse.reverse shouldBe b
    }
  }

  test("foldRight/left") {
    forAll { (b: ByteVector) =>
      b.foldLeft(ByteVector.empty)(_ :+ _) shouldBe b
    }
    forAll { (b: ByteVector) =>
      b.foldRight(ByteVector.empty)(_ +: _) shouldBe b
    }
  }

  test("insert") {
    val b = ByteVector.empty
    b.insert(0, 1) shouldBe ByteVector(1)
    ByteVector(1, 2, 3, 4).insert(0, 0) shouldBe ByteVector(0, 1, 2, 3, 4)
    ByteVector(1, 2, 3, 4).insert(1, 0) shouldBe ByteVector(1, 0, 2, 3, 4)
    forAll { (b: ByteVector) =>
      b.foldLeft(ByteVector.empty)((acc, b) => acc.insert(acc.size, b)) shouldBe b
    }
  }

  test("zipWith") {
    val b1 = ByteVector(0, 1, 2, 3)
    val b2 = ByteVector(1, 2, 3, 4)
    b1.zipWithI(b2)(_ + _) shouldBe ByteVector(1, 3, 5, 7)
    forAll { (b: ByteVector) =>
      b.zipWithI(b)(_ - _) shouldBe ByteVector.fill(b.size)(0)
    }
  }

  test("zipWith2") {
    val b1 = ByteVector(0, 1, 2, 3)
    val b2 = ByteVector(1, 2, 3, 4)
    val b3 = ByteVector(2, 3, 4, 5)
    b1.zipWithI2(b2, b3)(_ + _ + _) shouldBe ByteVector(3, 6, 9, 12)
    forAll { (b: ByteVector) =>
      b.zipWithI2(b, b)(_ + _ - _) shouldBe b
    }
  }

  test("zipWith3") {
    val b1 = ByteVector(0, 1, 2, 3)
    val b2 = ByteVector(1, 2, 3, 4)
    val b3 = ByteVector(2, 3, 4, 5)
    val b4 = ByteVector(3, 4, 5, 6)
    b1.zipWithI3(b2, b3, b4)(_ + _ + _ + _) shouldBe ByteVector(6, 10, 14, 18)
    forAll { (b: ByteVector) =>
      b.zipWithI3(b, b, b)(_ + _ - _ - _) shouldBe ByteVector.fill(b.size)(0)
    }
  }

  test("consistent with Array[Byte] implementations") {
    forAll(bytesWithIndex) {
      case (b, ind) =>
        val ba = b.toArray
        b.take(ind).toArray shouldBe ba.take(ind.toInt)
        b.drop(ind).toArray shouldBe ba.drop(ind.toInt)
        b.lift(ind) shouldBe ba.lift(ind.toInt)
        b.takeRight(ind).toArray shouldBe ba.takeRight(ind.toInt)
        b.dropRight(ind).toArray shouldBe ba.dropRight(ind.toInt)
        b.reverse.toArray shouldBe ba.reverse
        b.partialCompact(ind).toArray shouldBe ba
        b.lastOption shouldBe ba.lastOption
        b.nonEmpty shouldBe ba.nonEmpty
        if (b.nonEmpty) {
          b.last shouldBe ba.last
          b.init.toArray shouldBe ba.init
        }
        if (ind < b.size) {
          val actual = b.update(ind, 9).toArray
          val correct = Vector(b.toIndexedSeq: _*).updated(ind.toInt, 9.toByte).toArray
          actual shouldBe correct
        }

    }
    forAll { (b1: ByteVector, b2: ByteVector) =>
      (b1 ++ b2).toArray shouldBe (b1.toArray ++ b2.toArray)
    }
  }

  val deadbeef = ByteVector(0xde, 0xad, 0xbe, 0xef)

  test("toHex") {
    deadbeef.toHex shouldBe "deadbeef"
  }

  test("fromHexDescriptive") {
    ByteVector.fromHexDescriptive("0xdeadbeef") shouldBe Right(deadbeef)
    ByteVector.fromHexDescriptive("0xDEADBEEF") shouldBe Right(deadbeef)
    ByteVector.fromHexDescriptive("0XDEADBEEF") shouldBe Right(deadbeef)
    ByteVector.fromHexDescriptive("deadbeef") shouldBe Right(deadbeef)
    ByteVector.fromHexDescriptive("DEADBEEF") shouldBe Right(deadbeef)
    ByteVector.fromHexDescriptive("de ad be ef") shouldBe Right(deadbeef)
    ByteVector.fromHexDescriptive("de\tad\nbe\tef") shouldBe Right(deadbeef)
    ByteVector.fromHexDescriptive("0xde_ad_be_ef") shouldBe Right(deadbeef)

    ByteVector.fromHexDescriptive("0xdeadbee") shouldBe Right(ByteVector(0x0d, 0xea, 0xdb, 0xee))
    ByteVector.fromHexDescriptive("0xde_ad_be_e") shouldBe Right(ByteVector(0x0d, 0xea, 0xdb, 0xee))

    ByteVector.fromHexDescriptive("garbage") shouldBe Left(
      "Invalid hexadecimal character 'g' at index 0"
    )
    ByteVector.fromHexDescriptive("deadbefg") shouldBe Left(
      "Invalid hexadecimal character 'g' at index 7"
    )
  }

  test("toBin") {
    deadbeef.toBin shouldBe "11011110101011011011111011101111"
  }

  test("fromBinDescriptive") {
    ByteVector.fromBinDescriptive(deadbeef.toBin) shouldBe Right(deadbeef)
    ByteVector.fromBinDescriptive(deadbeef.toBin.grouped(4).mkString(" ")) shouldBe Right(deadbeef)
    ByteVector.fromBinDescriptive("0001 0011") shouldBe Right(ByteVector(0x13))
    ByteVector.fromBinDescriptive("0b 0001 0011 0111") shouldBe Right(ByteVector(0x01, 0x37))
    ByteVector.fromBinDescriptive("1101a000") shouldBe Left(
      "Invalid binary character 'a' at index 4"
    )
    ByteVector.fromBinDescriptive("0b1101a000") shouldBe Left(
      "Invalid binary character 'a' at index 6"
    )
    ByteVector.fromBinDescriptive("0B1101a000") shouldBe Left(
      "Invalid binary character 'a' at index 6"
    )
  }

  test("fromValidBin") {
    ByteVector.fromValidBin(deadbeef.toBin) shouldBe deadbeef
    an[IllegalArgumentException] should be thrownBy { ByteVector.fromValidBin("1101a000"); () }
  }

  test("toBase32") {
    assert(hex"".toBase32 == (""))
    assert(hex"00".toBase32 == ("AA======"))
    assert(hex"61".toBase32 == ("ME======"))
    assert(hex"626262".toBase32 == ("MJRGE==="))
    assert(hex"636363".toBase32 == ("MNRWG==="))
    assert(
      hex"73696d706c792061206c6f6e6720737472696e67".toBase32 == ("ONUW24DMPEQGCIDMN5XGOIDTORZGS3TH")
    )
    assert(
      hex"00eb15231dfceb60925886b67d065299925915aeb172c06647".toBase32 == ("ADVRKIY57TVWBESYQ23H2BSSTGJFSFNOWFZMAZSH")
    )
    assert(hex"516b6fcd0f".toBase32 == ("KFVW7TIP"))
    assert(hex"bf4f89001e670274dd".toBase32 == ("X5HYSAA6M4BHJXI="))
    assert(hex"572e4794".toBase32 == ("K4XEPFA="))
    assert(hex"ecac89cad93923c02321".toBase32 == ("5SWITSWZHER4AIZB"))
    assert(hex"10c8511e".toBase32 == ("CDEFCHQ="))
    assert(hex"00000000000000000000".toBase32 == ("AAAAAAAAAAAAAAAA"))
  }

  test("fromValidBase32") {
    assert(ByteVector.fromValidBase32("") == (ByteVector.empty))
    assert(ByteVector.fromValidBase32("AA======") == hex"00")
    assert(ByteVector.fromValidBase32("ME======") == (hex"61"))
    assert(ByteVector.fromValidBase32("MJRGE===") == (hex"626262"))
    assert(ByteVector.fromValidBase32("MNRWG===") == (hex"636363"))
    assert(
      ByteVector
        .fromValidBase32("ONUW24DMPEQGCIDMN5XGOIDTORZGS3TH") == (hex"73696d706c792061206c6f6e6720737472696e67")
    )
    assert(
      ByteVector
        .fromValidBase32("ADVRKIY57TVWBESYQ23H2BSSTGJFSFNOWFZMAZSH") == (hex"00eb15231dfceb60925886b67d065299925915aeb172c06647")
    )
    assert(ByteVector.fromValidBase32("KFVW7TIP") == (hex"516b6fcd0f"))
    assert(ByteVector.fromValidBase32("X5HYSAA6M4BHJXI=") == (hex"bf4f89001e670274dd"))
    assert(ByteVector.fromValidBase32("K4XEPFA=") == (hex"572e4794"))
    assert(ByteVector.fromValidBase32("5SWITSWZHER4AIZB") == (hex"ecac89cad93923c02321"))
    assert(ByteVector.fromValidBase32("CDEFCHQ=") == (hex"10c8511e"))
    assert(ByteVector.fromValidBase32("AAAAAAAAAAAAAAAA") == (hex"00000000000000000000"))
  }

  test("fail due to illegal character fromBase32") {
    assert(
      ByteVector
        .fromBase32Descriptive("7654321") == Left("Invalid base 32 character '1' at index 6")
    )
    assert(
      ByteVector
        .fromBase32Descriptive("ABc") == Left("Invalid base 32 character 'c' at index 2")
    )
    assert(
      ByteVector
        .fromBase32Descriptive("AB CD 0") == Left("Invalid base 32 character '0' at index 6")
    )
    assert(ByteVector.fromBase32("a").isEmpty)
  }

  test("toBase58") {
    hex"".toBase58 shouldBe ("")
    hex"00".toBase58 shouldBe ("1")
    hex"61".toBase58 shouldBe ("2g")
    hex"626262".toBase58 shouldBe ("a3gV")
    hex"636363".toBase58 shouldBe ("aPEr")
    hex"73696d706c792061206c6f6e6720737472696e67".toBase58 shouldBe ("2cFupjhnEsSn59qHXstmK2ffpLv2")
    hex"00eb15231dfceb60925886b67d065299925915aeb172c06647".toBase58 shouldBe ("1NS17iag9jJgTHD1VXjvLCEnZuQ3rJDE9L")
    hex"516b6fcd0f".toBase58 shouldBe ("ABnLTmg")
    hex"bf4f89001e670274dd".toBase58 shouldBe ("3SEo3LWLoPntC")
    hex"572e4794".toBase58 shouldBe ("3EFU7m")
    hex"ecac89cad93923c02321".toBase58 shouldBe ("EJDM8drfXA6uyA")
    hex"10c8511e".toBase58 shouldBe ("Rt5zm")
    hex"00000000000000000000".toBase58 shouldBe ("1111111111")
  }

  test("fromValidBase58") {
    ByteVector.fromValidBase58("") shouldBe (ByteVector.empty)
    ByteVector.fromValidBase58("1") shouldBe hex"00"
    ByteVector.fromValidBase58("2g") shouldBe (hex"61")
    ByteVector.fromValidBase58("a3gV") shouldBe (hex"626262")
    ByteVector.fromValidBase58("aPEr") shouldBe (hex"636363")
    ByteVector.fromValidBase58("2cFupjhnEsSn59qHXstmK2ffpLv2") shouldBe (hex"73696d706c792061206c6f6e6720737472696e67")
    ByteVector.fromValidBase58("1NS17iag9jJgTHD1VXjvLCEnZuQ3rJDE9L") shouldBe (hex"00eb15231dfceb60925886b67d065299925915aeb172c06647")
    ByteVector.fromValidBase58("ABnLTmg") shouldBe (hex"516b6fcd0f")
    ByteVector.fromValidBase58("3SEo3LWLoPntC") shouldBe (hex"bf4f89001e670274dd")
    ByteVector.fromValidBase58("3EFU7m") shouldBe (hex"572e4794")
    ByteVector.fromValidBase58("EJDM8drfXA6uyA") shouldBe (hex"ecac89cad93923c02321")
    ByteVector.fromValidBase58("Rt5zm") shouldBe (hex"10c8511e")
    ByteVector.fromValidBase58("1111111111") shouldBe (hex"00000000000000000000")
  }

  test("fail due to illegal character fromBase58") {
    ByteVector.fromBase58Descriptive("R3C0NFxN") shouldBe Left(
      "Invalid base 58 character '0' at index 3"
    )
    ByteVector.fromBase58Descriptive("03CMNFxN") shouldBe Left(
      "Invalid base 58 character '0' at index 0"
    )
    ByteVector.fromBase58("3CMNFxN1oHBc4R1EpboAL5yzHGgE611Xol").isEmpty shouldBe (true)
  }

  test("base64 roundtrip") {
    forAll { (b: ByteVector) =>
      ByteVector.fromValidBase64(b.toBase64) shouldBe b
    }
  }

  test("base64 issue #45") {
    val base64 =
      "1MOyoQIABAAAAAAAAAAAAP//AAABAAAAPl6hVQvgDAA8AAAAPAAAAP///////wAhQwjkUwgARQAA\r\nKEPjAABAEd9lqf4Bgan+Af/a/hOIABSGXENNRAAAAAAbqf4B/wAAAAAAAD9eoVX52QYAPAAAADwA\r\nAAABgMIAAAAAH5AHOpIAJkJCAwAAAAAAkAAADlgwS+AAAAA3kAAADlgwS+CAAgIABgABAAQAc2Vy\r\nYwAAAAA="
    BitVector.fromBase64Descriptive(base64).map { _.size } shouldBe Right(1408)
  }

  test("buffer :+") {
    forAll { (b: ByteVector, bs: List[ByteVector], n: Int) =>
      val unbuf = bs.foldLeft(b)(_ ++ _)
      val buf = bs.foldLeft(b.bufferBy((n % 50).max(0) + 1))((acc, a) => a.foldLeft(acc)(_ :+ _))
      unbuf shouldBe buf
    }
  }

  test("buffer ++/take/drop") {
    forAll { (b: ByteVector, bs: List[ByteVector], n: Int) =>
      val unbuf = bs.foldLeft(b)(_ ++ _)
      val buf = bs.foldLeft(b.bufferBy((n % 50).max(0) + 1))(_ ++ _)
      unbuf shouldBe buf
      val ind = (n % (unbuf.size + 1)).max(0) + 1
      buf.take(ind) shouldBe unbuf.take(ind)
      buf.drop(ind) shouldBe unbuf.drop(ind)
    }
  }

  test("buffer rebuffering") {
    forAll { (b1: ByteVector, b2: ByteVector, b3: ByteVector, n: Int) =>
      val chunkSize = (n % 50).max(0) + 1
      val b1b = b1.bufferBy(chunkSize)
      val b1b2b3 = (b1b ++ b2).bufferBy(chunkSize + 1) ++ b3
      b1b2b3 shouldBe (b1 ++ b2 ++ b3)
    }
  }

  test("<<") {
    ByteVector(0x55, 0x55, 0x55) << 1 shouldBe ByteVector(0xaa, 0xaa, 0xaa)
  }

  test(">>") {
    ByteVector(0x55, 0x55, 0x55) >> 1 shouldBe ByteVector(0x2a, 0xaa, 0xaa)
    ByteVector(0xaa, 0xaa, 0xaa) >> 1 shouldBe ByteVector(0xd5, 0x55, 0x55)
  }

  test(">>>") {
    ByteVector(0x55, 0x55, 0x55) >>> 1 shouldBe ByteVector(0x2a, 0xaa, 0xaa)
    ByteVector(0xaa, 0xaa, 0xaa) >>> 1 shouldBe ByteVector(0x55, 0x55, 0x55)
  }

  test("rotations") {
    forAll { (b: ByteVector, n: Long) =>
      b.rotateLeft(b.size * 8) shouldBe b
      b.rotateRight(b.size * 8) shouldBe b
      b.rotateRight(n).rotateLeft(n) shouldBe b
      b.rotateLeft(n).rotateRight(n) shouldBe b
    }
  }

  test("hex string interpolator") {
    hex"deadbeef" shouldBe deadbeef
    val x = ByteVector.fromValidHex("be")
    hex"dead${x}ef" shouldBe deadbeef
    """hex"deadgg"""" shouldNot compile
  }

  test("toIterable roundtrip") {
    forAll { (b: ByteVector) =>
      val fromIter = ByteVector(b.toIterable)
      b shouldBe fromIter
      fromIter shouldBe b
    }
  }

  test("toArray roundtrip") {
    forAll { (b: ByteVector) =>
      val fromArr = ByteVector(b.toArray)
      b shouldBe fromArr
      fromArr shouldBe b
      // Ensure immutable behavior
      val fromArr2 = ByteVector(b.toArray)
      fromArr shouldBe fromArr2
    }
  }

  test("copyToStream roundtrip") {
    forAll { (b: ByteVector) =>
      val os = new ByteArrayOutputStream()
      b.copyToStream(os)
      val fromArr = ByteVector(os.toByteArray)
      b shouldBe fromArr
      fromArr shouldBe b
    }
  }

  test("toByteBuffer roundtrip") {
    forAll { (b: ByteVector) =>
      val fromBuffer = ByteVector(b.toByteBuffer)
      b shouldBe fromBuffer
      fromBuffer shouldBe b
    }
  }

  test("dropping from a view is consistent with dropping from a strict vector") {
    forAll { (b: ByteVector, n0: Long) =>
      val view = ByteVector.view(b.toArray)
      val n = n0.abs
      b.drop(n) shouldBe view.drop(n)
    }
  }

  test("grouped + concatenate") {
    forAll { (bv: ByteVector) =>
      if (bv.isEmpty) {
        bv.grouped(1).toList shouldBe Nil
      } else if (bv.size < 3) {
        bv.grouped(bv.size).toList shouldBe List(bv)
      } else {
        bv.grouped(bv.size / 3).toList.foldLeft(ByteVector.empty) { (acc, b) =>
          acc ++ b
        } shouldBe bv
      }
    }
  }

  test("indexOfSlice/containsSlice/startsWith") {
    forAll { (bv: ByteVector, m0: Int, n0: Int) =>
      val m = if (bv.nonEmpty) (m0 % bv.size).abs else 0
      val n = if (bv.nonEmpty) (n0 % bv.size).abs else 0
      val slice = bv.slice(m.min(n), m.max(n))
      val idx = bv.indexOfSlice(slice)
      idx shouldBe bv.toIndexedSeq.indexOfSlice(slice.toIndexedSeq)
      bv.containsSlice(slice) shouldBe true
      if (bv.nonEmpty) bv.containsSlice(bv ++ bv) shouldBe false
    }
  }

  test("endsWith") {
    forAll { (bv: ByteVector, n0: Int) =>
      val n = if (bv.nonEmpty) (n0 % bv.size).abs else 0
      val slice = bv.takeRight(n)
      bv.endsWith(slice) shouldBe true
      if (slice.nonEmpty) bv.endsWith(~slice) shouldBe false
    }
  }

  test("splice") {
    forAll { (x: ByteVector, y: ByteVector, n0: Int) =>
      val n = if (x.nonEmpty) (n0 % x.size).abs else 0
      x.splice(n, ByteVector.empty) shouldBe x
      x.splice(n, y) shouldBe (x.take(n) ++ y ++ x.drop(n))
    }
  }

  test("patch") {
    forAll { (x: ByteVector, y: ByteVector, n0: Int) =>
      val n = if (x.nonEmpty) (n0 % x.size).abs else 0
      x.patch(n, x.slice(n, n)) shouldBe x
      x.patch(n, y) shouldBe (x.take(n) ++ y ++ x.drop(n + y.size))
    }
  }

  test("short conversions") {
    forAll { (n: Short) =>
      ByteVector.fromShort(n).toShort() shouldBe n
      ByteVector
        .fromShort(n, ordering = ByteOrdering.LittleEndian)
        .toShort(ordering = ByteOrdering.LittleEndian) shouldBe n
    }
  }

  test("int conversions") {
    forAll { (n: Int) =>
      ByteVector.fromInt(n).toInt() shouldBe n
      ByteVector
        .fromInt(n, ordering = ByteOrdering.LittleEndian)
        .toInt(ordering = ByteOrdering.LittleEndian) shouldBe n
    }
  }

  test("long conversions") {
    forAll { (n: Long) =>
      ByteVector.fromLong(n).toLong() shouldBe n
      ByteVector
        .fromLong(n, ordering = ByteOrdering.LittleEndian)
        .toLong(ordering = ByteOrdering.LittleEndian) shouldBe n
    }
  }

  test("UUID conversions") {
    // Valid conversions
    forAll { (u: UUID) =>
      ByteVector.fromUUID(u).toUUID shouldBe u
    }
    // "Invalid" conversions
    val badlySizedByteVector: Gen[ByteVector] = byteVectors.suchThat(_.length != 16)
    forAll(badlySizedByteVector) { badlySizedByteVector =>
      an[IllegalArgumentException] should be thrownBy { badlySizedByteVector.toUUID }
    }
  }

  test("concat") {
    forAll { (bvs: List[ByteVector]) =>
      val c = ByteVector.concat(bvs)
      c.size shouldBe bvs.map(_.size).foldLeft(0L)(_ + _)
      bvs.headOption.foreach(h => c.startsWith(h))
      bvs.lastOption.foreach(l => c.endsWith(l))
    }
  }

  test("copyToArray with offset/size") {
    forAll { (b: ByteVector) =>
      val size = b.size / 3
      val start = b.size / 4
      val offset = b.size / 5
      val xs = new Array[Byte](b.size.toInt)
      b.copyToArray(xs, start.toInt, offset, size.toInt)
      val startPlusSize = start + size
      xs shouldBe (xs.take(start.toInt) ++ b.drop(offset).take(size).toArray ++ xs.drop(
        startPlusSize.toInt
      )).toArray
    }
  }

  test("copyToBuffer") {
    forAll { (b: ByteVector, bufferSize0: Int, initialPosition0: Int, direct: Boolean) =>
      val bufferSize = (bufferSize0 % 1000000).abs
      val buffer =
        if (direct) ByteBuffer.allocateDirect(bufferSize) else ByteBuffer.allocate(bufferSize)
      val initialPosition = if (bufferSize == 0) 0 else (initialPosition0 % bufferSize).abs
      buffer.position(initialPosition)
      val copied = b.copyToBuffer(buffer)
      buffer.flip()
      copied shouldBe ((bufferSize.toLong - initialPosition).min(b.size))
      ByteVector.view(buffer).drop(initialPosition.toLong) shouldBe b.take(copied.toLong)
    }
  }

  test("viewing ByteBuffer with non-zero positoin") {
    forAll { (b: Array[Byte], position0: Int, sliceSize0: Int, direct: Boolean) =>
      val buffer = if (direct) ByteBuffer.allocateDirect(b.size) else ByteBuffer.allocate(b.size)
      val position = if (b.size == 0) 0 else (position0 % b.size).abs
      val remaining = b.size - position
      val sliceSize = if (remaining == 0) 0 else (sliceSize0 % (b.size - position)).abs

      buffer.position(position).limit(position + sliceSize)
      val slice = buffer.slice()
      buffer.position(position).limit(position + sliceSize)
      ByteVector.view(buffer) shouldBe ByteVector.view(slice)

      buffer.position(position)
    }
  }

  test("dropWhile") {
    forAll { (x: ByteVector) =>
      val (expected, _) = x.foldLeft((ByteVector.empty, true)) {
        case ((acc, dropping), b) =>
          if (dropping) {
            if (b == 0) (acc :+ 0, false)
            else (acc, true)
          } else {
            (acc :+ b, false)
          }
      }
      x.dropWhile(_ != 0.toByte) shouldBe expected
    }
  }

  test("takeWhile") {
    forAll { (x: ByteVector) =>
      val (expected, _) = x.foldLeft((ByteVector.empty, true)) {
        case ((acc, taking), b) =>
          if (taking) {
            if (b == 0) (acc, false)
            else (acc :+ b, true)
          } else {
            (acc, false)
          }
      }
      x.takeWhile(_ != 0.toByte) shouldBe expected
    }
  }

  test("very large vectors") {
    val huge = ByteVector.fill(Int.MaxValue * 2L)(0)
    val huge2 = huge ++ huge ++ hex"deadbeef"
    huge2.takeRight(2) shouldBe hex"beef"
  }

  test("take") {
    hex"0011223344".take(3) shouldBe hex"001122"
    hex"0011223344".take(1000) shouldBe hex"0011223344"
    hex"0011223344".take(-10) shouldBe hex""
  }

  test("drop") {
    hex"0011223344".drop(3) shouldBe hex"3344"
    hex"0011223344".drop(-10) shouldBe hex"0011223344"
    hex"0011223344".drop(1000) shouldBe hex""
  }

  test("slice") {
    hex"001122334455".slice(1, 4) shouldBe hex"112233"
    hex"001122334455".slice(-21, 4) shouldBe hex"00112233"
    hex"001122334455".slice(-21, -4) shouldBe hex""
  }

  test("slice is consistent with array slice") {
    forAll { (b: ByteVector, from: Int, until: Int) =>
      b.slice(from.toLong, until.toLong) shouldBe ByteVector.view(b.toArray.slice(from, until))
    }
  }

  test("unapply") {
    val ByteVector(x, y, z) = hex"000102"
    x shouldBe 0.toByte
    y shouldBe 1.toByte
    z shouldBe 2.toByte

    hex"000102" match {
      case ByteVector(0, 1, 2) => // OK
    }
  }
}
