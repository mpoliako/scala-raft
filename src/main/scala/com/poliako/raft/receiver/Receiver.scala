package com.poliako.raft.receiver

import akka.actor.Actor
import com.poliako.raft.model.{Entry, VolatileState}
import scala.util.control.Breaks._

trait Receiver {
  def appendEntries(term: Long, leaderId: String, prevLogIndex: Long, prevLogTerm: Long, entries: Array[Entry], leaderCommit: Long): AppendResult
  def requestVote(term: Long, candidateId: String, lastLogIndex: Long, lastLogTerm: Long): VoteResult
  case class AppendResult(term: Long, success: Boolean)
  case class VoteResult(term: Long, voteGranted: Boolean)
}


abstract class AbstractReceiver(state: VolatileState) extends Receiver {
  override def appendEntries(term: Long, leaderId: String, prevLogIndex: Long, prevLogTerm: Long, entries: Array[Entry], leaderCommit: Long): AppendResult = {

    // Reply false if term < currentTerm (§5.1)
    if (term < state.currentTerm) {
      return AppendResult(state.currentTerm, false)
    }

    // Reply false if log doesn’t contain an entry at prevLogIndex
    // whose term matches prevLogTerm (§5.3)
    val prevEntry = state.log.find(entry =>
      entry.term == prevLogTerm && entry.index == prevLogIndex)

    if (prevEntry.isEmpty) {
      return AppendResult(state.currentTerm, false)
    }

    // If an existing entry conflicts with a new one (same index
    // but different terms), delete the existing entry and all that
    // follow it (§5.3)
    var logInd = state.log.indexOf(prevEntry.get) + 1
    var newInd = 0

    breakable {
      while (logInd < state.log.length && newInd < entries.length) {
        val logEntry = state.log(logInd)
        val newEntry = entries(newInd)

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
    val appendEntries = entries.drop(newInd)
    state.log = state.log ++ appendEntries

    // If leaderCommit > commitIndex, set commitIndex =
    //   min(leaderCommit, index of last new entry)
    if (leaderCommit > state.commitIndex) {
      state.commitIndex = scala.math.min(leaderCommit, appendEntries.last.index)
    }

    state.currentTerm = term

    // TODO
    // Results:
    //   term currentTerm, for leader to update itself
    //   success true if follower contained entry matching
    //   prevLogIndex and prevLogTerm
    return AppendResult(term, true)
  }


  override def requestVote(term: Long, candidateId: String, lastLogIndex: Long, lastLogTerm: Long): VoteResult = {
    if (term < state.currentTerm) {
      return VoteResult(state.currentTerm, false)
    }

    state.votedFor match {
      case None if (state.currentTerm < lastLogTerm || (state.currentTerm == lastLogTerm && state.commitIndex <= lastLogIndex)) =>
        // TODO: currentTerm, for candidate to update itself
        state.currentTerm = term
        VoteResult(term, true)
      case Some(vote) if vote == candidateId && (state.currentTerm < lastLogTerm || (state.currentTerm == lastLogTerm && state.commitIndex <= lastLogIndex)) =>
        // TODO: currentTerm, for candidate to update itself
        state.currentTerm = term
        VoteResult(term, true)
      case _ =>
        state.currentTerm = term
        VoteResult(term, false)
    }
  }
}


class ReceiverActor(state: VolatileState) extends AbstractReceiver(state) with Actor {

  def actCandidate: Receive = {
    // On conversion to candidate, start election:
    // Increment currentTerm
    state.currentTerm += 1

    // Vote for self

    ???
  }

  def actFollower: Receive = ???
  def actLeader: Receive = ???

  override def receive: Receive = actCandidate
  
}