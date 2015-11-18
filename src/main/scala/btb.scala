package BOOM

// Superscalar BTB
// Given a PC, predict the next fetch packet.
// BTB tag encodes which instruction is the predicting branch,
// but tag check only looks at the "packet"'s PC.

// Gotchas - need to make sure predicting branch isn't masked off by current PC
// (e.g., first instruction is a branch, but PC starts at the second
// instruction, so ignore prediction).

import Chisel._
import Node._

import scala.math._
import rocket._
import uncore._

class BTBIO extends Bundle with BTBParameters
{
  val current_pc     = UInt(INPUT, vaddrBits)
  val hit            = Bool(OUTPUT)
  val hit_idx        = UInt(OUTPUT) // which inst in the fetch bundle is the predicted branch
  val target         = UInt(OUTPUT, vaddrBits)
  val wen            = Bool(INPUT)
  val clr            = Bool(INPUT)
  val invalidate     = Bool(INPUT)
  val correct_pc     = UInt(INPUT, vaddrBits)
  val correct_target = UInt(INPUT, vaddrBits)
}

 
// fully-associative branch target buffer
class BTB(fetchWidth: Int) extends Module with BTBParameters
{
  val io = new BTBIO

  println ("    BTB Entries  : " + entries)

  var hit_reduction = Bool(false)
  val update = Bool()
  var update_reduction = Bool(false)
  val valid = Vec.fill(entries){Reg(init=Bool(false))}
  val hits = Vec.fill(entries){Bool()}
  val updates = Vec.fill(entries){Bool()}
  val targets = Mem(entries, UInt())
  val hit_idxs = Mem(entries, UInt())
  val anyUpdate = updates.toBits.orR

  val random_way = rocket.Random(entries)
  val invalid_way = valid.indexWhere((x: Bool) => !x)
  val repl_way = Mux(valid.contains(Bool(false)), invalid_way, random_way)

  for (i <- 0 until entries) {
    val tag = Reg(UInt())
    
    // for superscalar, store which inst is the branch in the tag, but don't
    // check against it
    val msk_sz = if (fetchWidth == 1) 2 else (log2Up(fetchWidth) + 2)
    val check_mask = Cat(Fill(vaddrBits-msk_sz, Bits(1,1)), Fill(msk_sz, Bits(0,1)))

    val tag_check = tag & check_mask
    // idx of the predicting branch
    val hit_idx = UInt()
    if (fetchWidth == 1) hit_idx := UInt(0)
    else                 hit_idx := tag(msk_sz-1,2)

    // is the branch masked off by the PC? 
    val br_too_old = Bool()
    if (fetchWidth == 1) br_too_old := Bool(false)
    else                 br_too_old := io.current_pc(msk_sz-1,2) > hit_idx

    hits(i)     := valid(i) && tag_check === (io.current_pc & check_mask) && !br_too_old
    updates(i)  := valid(i) && tag_check === (io.correct_pc & check_mask)
    hit_idxs(i) := hit_idx

    when (io.wen && (updates(i) || !anyUpdate && UInt(i) === repl_way)) {
      valid(i) := Bool(false)
      when (!io.clr) {
        valid(i) := Bool(true)
        tag := io.correct_pc
        targets(i) := io.correct_target
      }
    }
  }

  io.hit    := hits.toBits.orR
  io.target := Mux1H(hits, targets)
  io.hit_idx:= Mux1H(hits, hit_idxs)
}
 
