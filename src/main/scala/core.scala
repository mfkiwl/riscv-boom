//******************************************************************************
// Copyright (c) 2015, The Regents of the University of California (Regents).
// All Rights Reserved. See LICENSE for license details.
//------------------------------------------------------------------------------
//------------------------------------------------------------------------------
// RISC-V Processor Core
//------------------------------------------------------------------------------
//------------------------------------------------------------------------------

package boom
{

import Chisel._
import Node._
import cde.Parameters
import rocket._
import uncore.HtifIO

abstract class BoomModule(implicit val p: Parameters) extends Module
  with HasBoomCoreParameters
class BoomBundle(implicit val p: Parameters) extends junctions.ParameterizedBundle()(p)
  with HasBoomCoreParameters

class CoreIo(implicit p: Parameters) extends BoomBundle()(p)
{
   val host = new HtifIO
   val dmem = new DCMemPortIo
   val imem = new rocket.FrontendIO
   val ptw_dat  = new DatapathPTWIO().flip
   val ptw_tlb  = new TLBPTWIO()
   val rocc     = new rocket.RoCCInterface().flip
   val counters = new CacheCounters().asInput
}

class Core(implicit p: Parameters) extends BoomModule()(p)
{
   val io = new CoreIo()

   val dpath  = Module(new DatPath())

   dpath.io.imem <> io.imem
   dpath.io.dmem <> io.dmem

   dpath.io.host <> io.host

   dpath.io.ptw_dat <> io.ptw_dat
   dpath.io.ptw_tlb <> io.ptw_tlb

   dpath.io.counters <> io.counters
}

}
