package com.poliako.raft.receiver

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable}
import com.poliako.raft.model.{Entry, VolatileState}

import scala.util.control.Breaks._
import scala.concurrent.duration._

trait Receiver {
  def appendEntries(req: AppendRequest): AppendResult
  def requestVote(req: VoteRequest): VoteResult
  case class AppendRequest(term: Long, leaderId: String, prevLogIndex: Long, prevLogTerm: Long, entries: Array[Entry], leaderCommit: Long)
  case class VoteRequest(term: Long, candidateId: String, lastLogIndex: Long, lastLogTerm: Long)
  case class AppendResult(term: Long, success: Boolean)
  case class VoteResult(term: Long, voteGranted: Boolean)
  case object ScheduleRequest
}


abstract class AbstractReceiver(state: VolatileState) extends Receiver {
  override def appendEntries(req: AppendRequest): AppendResult = {

    // Reply false if term < currentTerm (§5.1)
    if (state.votedFor != req.leaderId || req.term < state.currentTerm) {
      return AppendResult(state.currentTerm, false)
    }

    // Reply false if log doesn’t contain an entry at prevLogIndex
    // whose term matches prevLogTerm (§5.3)
    val prevEntry = state.log.find(entry =>
      entry.term == req.prevLogTerm && entry.index == req.prevLogIndex)

    if (prevEntry.isEmpty) {
      return AppendResult(state.currentTerm, false)
    }

    // If an existing entry conflicts with a new one (same index
    // but different terms), delete the existing entry and all that
    // follow it (§5.3)
    var logInd = state.log.indexOf(prevEntry.get) + 1
    var newInd = 0

    breakable {
      while (logInd < state.log.length && newInd < req.entries.length) {
        val logEntry = state.log(logInd)
        val newEntry = req.entries(newInd)

        if (logEntry.term != newEntry.term || logEntry.index != newEntry.index) {
          state.log = state.log.dropRight(state.log.length - logInd)
          break
        }
        logInd += 1
        newInd += 1
      }
    }

    state.log = state.log.dropRight(state.log.length - logInd - 1)

    // Append any new entries not already in the log
    val appendEntries = req.entries.drop(newInd)
    state.log = state.log ++ appendEntries

    // If leaderCommit > commitIndex, set commitIndex =
    //   min(leaderCommit, index of last new entry)
    if (req.leaderCommit > state.commitIndex) {
      state.commitIndex = scala.math.min(req.leaderCommit, appendEntries.last.index)
    }

    while (state.lastApplied < state.commitIndex) {
      // TODO:
      // If commitIndex > lastApplied: increment lastApplied, apply
      // log[lastApplied] to state machine (§5.3)
    }

    state.currentTerm = req.term

    // Results:
    //   term currentTerm, for leader to update itself
    //   success true if follower contained entry matching
    //   prevLogIndex and prevLogTerm
    return AppendResult(req.term, true)
  }


  override def requestVote(req: VoteRequest): VoteResult = {
    if (req.term < state.currentTerm) {
      return VoteResult(state.currentTerm, false)
    }

    state.votedFor match {
      case None if (state.currentTerm < req.lastLogTerm || (state.currentTerm == req.lastLogTerm && state.commitIndex <= req.lastLogIndex)) =>
        // currentTerm, for candidate to update itself
        state.currentTerm = req.term
        state.votedFor = Some(req.candidateId)
        VoteResult(req.term, true)
      case Some(vote) if vote == req.candidateId && (state.currentTerm < req.lastLogTerm || (state.currentTerm == req.lastLogTerm && state.commitIndex <= req.lastLogIndex)) =>
        // currentTerm, for candidate to update itself
        state.currentTerm = req.term
        state.votedFor = Some(req.candidateId)
        VoteResult(req.term, true)
      case _ =>
        state.currentTerm = req.term
        VoteResult(req.term, false)
    }
  }
}


class ReceiverActor(state: VolatileState, servers: List[ActorRef]) extends AbstractReceiver(state) with Actor with ActorLogging {

  var scheduleTask: Cancellable = context.system.scheduler.scheduleOnce(5000 + scala.util.Random.nextInt(5000) millis, self, ScheduleRequest)
  var voteCount = 0

  def follower: Receive = {
    // Respond to RPCs from candidates and leaders
    case req @ AppendRequest =>
      modifyScehduler()
      sender() ! appendEntries(req)
    case req @ VoteRequest =>
      modifyScehduler()
      sender() ! requestVote(req)
    case ScheduleRequest =>
      // If election timeout elapses without receiving AppendEntries
      // RPC from current leader or granting vote to candidate:
      // convert to candidate
      context.become(candidate)
      self ! ScheduleRequest
    case er @ _ => log.error(s"Follower: Unknown message $er")
  }

  def candidate: Receive = {
    case ScheduleRequest =>

      // On conversion to candidate, start election:
      // Increment currentTerm
      state.currentTerm += 1
      voteCount = 0

      // Vote for self
      val voteRequest = VoteRequest(state.currentTerm, state.serverId, state.commitIndex, state.currentTerm)
      val selfResult = requestVote(voteRequest)

      // Reset election timer
      modifyScehduler()

      // Send RequestVote RPCs to all other servers
      servers.foreach(_ ! voteRequest)
    case VoteResult(term: Long, voteGranted: Boolean) =>
      if (voteGranted && term == state.currentTerm) {
        voteCount += 1
      }
      // If votes received from majority of servers: become leader
      if (voteCount*2 > servers.size) {
        log.info(s"Candiate ${state.serverId}: I'll be leader. Received majority of votes: ${voteCount}")
        context.become(leader)
      }
    case req @ AppendRequest =>
      // If AppendEntries RPC received from new leader: convert to follower
      context.become(follower)
      modifyScehduler()
    case er @ _ => log.error(s"Candiate: Unknown message $er")
  }

  def leader: Receive = ???

  override def receive: Receive = follower

  def modifyScehduler(): Unit = {
    log.info(s"AppendRequest: closed schedule task. result: ${scheduleTask.cancel()}")
    scheduleTask = context.system.scheduler.scheduleOnce(5000 + scala.util.Random.nextInt(5000) millis, self, ScheduleRequest)
  }
}