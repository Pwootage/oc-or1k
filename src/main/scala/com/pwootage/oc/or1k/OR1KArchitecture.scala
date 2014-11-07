package com.pwootage.oc.or1k

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.{Paths, Files}
import java.util.UUID

import com.pwootage.oc.or1k.components.ComponentList
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
  val MaxStrLength: Int = 256

  object ParamTypes {
    //also the default
    val Int = 0x0
    val StringPtr = 0x1
    val Uuid = 0x2
  }

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

    //Component method call trap
    cpu.registerTrap(0x100)(componentCallTrap)
    cpu.registerTrap(0x101) { or1k =>
      val uuid = {
        val uuidPtr = or1k.reg.gp(3)
        getUuid(uuidPtr)
      }
      val destPtr = or1k.reg.gp(4)
      machine.components().get(uuid.toString) match {
        case null => writeStr(destPtr, "null")
        case x => writeStr(destPtr, x)
      }
    }
    cpu.registerTrap(0x102) { or1k =>
      OCOR1K.log.info(getString(or1k.reg.gp(3)))
    }
    cpu.registerTrap(0xFFFE) { or1k =>
      machine.crash(getString(or1k.reg.gp(3)))
    }

    mmu.registerDevice(new ComponentList(cpu, machine), 0xF0)

    //temp hack to load ROM.. :P
    mmu.putByteArray(Files.readAllBytes(Paths.get("/Users/pwootage/projects/pwix/bin/pwix.bin")), 0)

    //Entry point
    registers.pc = 0x100
    registers.npc = 0x104

    _initialized = true
    OCOR1K.log.info("Initing a CPU")

    _initialized
  }

  def getString(addr: Int) = {
    val len = strLen(addr)
    val buff = new Array[Byte](len)
    for (i <- 0 until len) {
      buff(i) = mmu.getByte(addr + i)
    }
    new String(buff, StandardCharsets.US_ASCII)
  }

  def writeStr(addr: Int, s: String): Any = {
    val len = Math.min(s.length, MaxStrLength)
    for (i <- 0 until len) {
      mmu.setByte(addr + i, s(i).toByte)
    }
    mmu.setByte(addr + len, 0)
  }

  def strLen(addr: Int): Int = {
    for (i <- 0 until MaxStrLength) {
      if (mmu.getByte(addr + i) == 0) {
        return i
      }
    }
    MaxStrLength
  }

  def componentCallTrap(or1k: OR1K) = {
    //SP params:
    //-4: &component
    //-8: &methodname
    //-12: paramCount
    //-13: paramTypes[]
    val uuid = {
      val compAddr = mmu.getInt(registers.gp(1) - 4)
      getUuid(compAddr)
    }
    val methodName = {
      val methodNameAddr = mmu.getInt(registers.gp(1) - 8)
      getString(methodNameAddr)
    }
    val paramTypes = {
      val paramCount = Math.max(0, Math.min(mmu.getInt(registers.gp(1) - 12), 6))
      for (i <- 0 until paramCount) yield mmu.getByte(registers.gp(1) - 16 - i)
    }
    OCOR1K.log.info(s"PramTypes: ${paramTypes.mkString(", ")}")
    val params = {
      for (i <- 0 until paramTypes.length) yield paramTypes(i) match {
        case ParamTypes.StringPtr => getString(registers.gp(3 + i))
        case ParamTypes.Uuid => getUuid(registers.gp(3 + i)).toString
        case _ => registers.gp(3 + i).toDouble
      }
    }.toArray.map(v => v.asInstanceOf[AnyRef])
    OCOR1K.log.info(s"Calling '$methodName' on '$uuid' width params (${params.mkString(",")})")
    machine.invoke(uuid.toString, methodName, params)
  }

  def getUuid(addr: Int): UUID = {
    val uuidBytes = (0 until 16).map(i => mmu.getByte(addr + i)).toArray
    val msb = (0 until 8).foldLeft(0L)((a: Long, b) => a | ((uuidBytes(b).toLong & 0xFF) << ((7 - b) * 8)))
    val lsb = (0 until 8).foldLeft(0L)((a: Long, b) => a | ((uuidBytes(b + 8).toLong & 0xFF) << ((7 - b) * 8)))
    new UUID(msb, lsb)
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
    try for (i <- 1 to 10000) {
      cpu.executeStep()
    } catch {
      case e: Throwable => OCOR1K.log.warn("Error in CPU loop: ", e)
    }
    new ExecutionResult.Sleep(1)
  }
}
