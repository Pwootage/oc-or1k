package com.pwootage.oc.or1k.components

import java.util.UUID

import com.pwootage.oc.or1k.OCOR1K
import com.pwootage.sor1k.IllegalMemoryAccessException
import com.pwootage.sor1k.cpu.InstructionCodes.InterruptVector
import com.pwootage.sor1k.cpu.OR1K
import com.pwootage.sor1k.memory.ByteMemoryAccess
import li.cil.oc.api.machine.Machine
import scala.collection.JavaConversions._

/**
 * Represents a way of retrieving a list of components
 */
class ComponentList(or1k: OR1K, machine: Machine) extends ByteMemoryAccess {

  object A {
    val Count = 0x00000
    val UuidOff = 0x10000
  }

  override def getByte(location: Int): Byte = {
    if (location == A.Count) {
      machine.components().size().toByte
    } else if (location < A.UuidOff) {
      0
    } else if (location < A.UuidOff + machine.components().size() * 16) {
      val machineNum = (location - A.UuidOff) / 16
      getComponent(machineNum) match {
        case None => 0
        case Some(x) =>
          val off = location % 16
          val byt = if (off < 8) {
            ((x.getMostSignificantBits >>> ((7 - off) * 8)) & 0xFFL).toByte
          } else {
            ((x.getLeastSignificantBits >>> ((7 - (off - 8)) * 8)) & 0xFFL).toByte
          }
          byt
      }
    } else {
      0
    }
  }

  def getComponent(num: Int) = {
    if (num < 0 || num > machine.components().size()) {
      None
    } else {
      val comp = (for ((k, v) <- machine.components()) yield k).toSeq.sorted
      Some(UUID.fromString(comp(num)))
    }

  }

  override def setByte(location: Int, value: Byte): Unit = {
    //Cannot write to this device
  }
}
