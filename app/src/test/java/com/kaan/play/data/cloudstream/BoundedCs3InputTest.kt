package com.kaan.play.data.cloudstream

import org.junit.Assert.*
import org.junit.Test
import java.io.InputStream
import com.kaan.play.data.addon.AddonException

class BoundedCs3InputTest {
 @Test fun exactLimitIsReadable() {
  val bytes = ByteArray(1024) { (it % 255).toByte() }
  assertArrayEquals(bytes, readBounded(bytes.inputStream(), bytes.size))
 }
 @Test fun unknownLengthEndlessBodyStopsBeforeUnboundedAllocation() {
  var consumed = 0
  val input = object : InputStream() {
   override fun read(): Int { consumed++; return 1 }
   override fun read(b: ByteArray, off: Int, len: Int): Int {
    java.util.Arrays.fill(b, off, off + len, 1.toByte()); consumed += len; return len
   }
  }
  try { readBounded(input, 1024); fail("oversized body accepted") }
  catch (_: AddonException) { assertEquals(1025, consumed) }
 }
}
