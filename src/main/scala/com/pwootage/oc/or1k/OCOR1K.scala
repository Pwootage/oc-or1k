package com.pwootage.oc.or1k

import cpw.mods.fml.common.Mod
import cpw.mods.fml.common.Mod.EventHandler
import cpw.mods.fml.common.event._
import org.apache.logging.log4j.LogManager

@Mod(
  modid = OCOR1K.ID,
  name = OCOR1K.Name,
  version = OCOR1K.Version,
  modLanguage = "scala",
  useMetadata = true,
dependencies = "required-after:OpenComputers@[1.4.0,)"
)
object OCOR1K {
  final val ID = "oc.or1k"

  final val Name = "OC-OR1K"

  final val Version = "@VERSION@"

  var log = LogManager.getLogger("OC-OR1K")

  //  @SidedProxy(clientSide = "li.cil.oc.client.Proxy", serverSide = "li.cil.oc.server.Proxy")
  //  var proxy: Proxy = null


  @EventHandler
  def preInit(e: FMLPreInitializationEvent): Unit = {
    li.cil.oc.api.Machine.add(classOf[OR1KArchitecture])
  }

  @EventHandler
  def init(e: FMLInitializationEvent): Unit = {
  }

  @EventHandler
  def postInit(e: FMLPostInitializationEvent): Unit = {
  }
}
