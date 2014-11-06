package com.pwootage.oc.or1k

import java.nio.ByteBuffer

import com.pwootage.sor1k.CPUException
import com.pwootage.sor1k.cpu.OR1K
import com.pwootage.sor1k.memory.MMU
import com.pwootage.sor1k.registers.Registers
import li.cil.oc.api.machine.Machine
import li.cil.oc.api.machine.{ExecutionResult, Architecture}
import net.minecraft.nbt.NBTTagCompound

/**
 * OR1K Archetecture Wrapper
 */
@Architecture.Name("OR1K")
class OR1KArchitecture(machine: Machine) extends Architecture {
  private var _initialized = false
  private var cpu: OR1K = null
  private var registers: Registers = null
  private var mmu: MMU = null
  private var mem: ByteBuffer = null

  override def isInitialized: Boolean = _initialized

  override def onConnect(): Unit = {
  }

  override def initialize(): Boolean = {
    registers = new Registers
    mem = ByteBuffer.allocate(machine.host().installedMemory())
    mmu = new MMU(registers, mem)
    cpu = new OR1K(registers, mmu)
    _initialized = true

    _initialized
  }

  override def load(nbt: NBTTagCompound): Unit = {
    if (nbt.hasKey("registers")) {
      val reg = nbt.getCompoundTag("registers")
      registers.eear.set(reg.getInteger("eear"))
      registers.epcr.set(reg.getInteger("epcr"))
      registers.esrr.set(reg.getInteger("esrr"))
      registers.pc = reg.getInteger("pc")
      registers.npc = reg.getInteger("npc")
      registers.sr.set(reg.getInteger("sr"))
      val gp = reg.getIntArray("gp")
      for (i <- 0 until registers.gp.length) registers.gp(i) = gp(i)
    }
    if (nbt.hasKey("memory")) {
      val newMem = nbt.getByteArray("memory")
      //Stick what we can in the memory
      val end = Math.min(mem.capacity(), newMem.length)
      mem.position(0)
      mem.put(newMem, 0, end)
    }
  }

  override def runSynchronized(): Unit = {
    try {

    } catch {
      case e: CPUException =>
      case e: Throwable => OCOR1K.log.error("Unknown exception was thrown by CPU!", e)
    }
  }

  override def close(): Unit = {
    //GC is good enough
  }

  override def save(nbt: NBTTagCompound): Unit = {
    val reg = new NBTTagCompound()
    reg.setInteger("eear", registers.eear.get)
    reg.setInteger("epcr", registers.epcr.get)
    reg.setInteger("esrr", registers.esrr.get)
    reg.setInteger("pc", registers.pc)
    reg.setInteger("npc", registers.npc)
    reg.setInteger("sr", registers.sr.get)
    reg.setIntArray("gp", registers.gp)
    nbt.setTag("registers", reg)

    nbt.setByteArray("memory", mem.array())
  }

  override def recomputeMemory(): Unit = {
    if (machine.host().installedMemory() != mem.capacity()) {
      //Force-reinit (hard-reset)
      initialize()
    }
  }

  override def runThreaded(isSynchronizedReturn: Boolean): ExecutionResult = {
    new ExecutionResult.Sleep(1)
  }
}
