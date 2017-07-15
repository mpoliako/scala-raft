package com.poliako.raft.receiver

import akka.actor.Actor
import com.poliako.raft.model.{Entry, VolatileState}

trait Receiver {
  def appendEntries(term: Long, leaderId: String, prevLogIndex: Long, prevLogTerm: Long, entries: Array[Entry], leaderCommit: Long): AppendResult
  def requestVote(term: Long, candidateId: String, lastLogIndex: Long, lastLogTerm: Long): VoteResult
  case class AppendResult(term: Long, success: Boolean)
  case class VoteResult(term: Long, voteGranted: Boolean)
}


abstract class AbstractReceiver(state: VolatileState) extends Receiver {
  override def appendEntries(term: Long, leaderId: String, prevLogIndex: Long, prevLogTerm: Long, entries: Array[Entry], leaderCommit: Long): AppendResult = {

    // Reply false if term < currentTerm (§5.1)
    if (term > state.currentTerm) {
      AppendResult(-1l, false)
    }

    // TODO
    // Reply false if log doesn’t contain an entry at prevLogIndex
    // whose term matches prevLogTerm (§5.3)

    // TODO
    // If an existing entry conflicts with a new one (same index
    // but different terms), delete the existing entry and all that
    // follow it (§5.3)

    // TODO
    // Append any new entries not already in the log

    // If leaderCommit > commitIndex, set commitIndex =
    //   min(leaderCommit, index of last new entry)
    if (leaderCommit > state.commitIndex) {
      state.commitIndex = (leaderCommit :: entries.map(_.index).toList).min
    }

    // TODO
    // Results:
    //   term currentTerm, for leader to update itself
    //   success true if follower contained entry matching
    //   prevLogIndex and prevLogTerm
    AppendResult(???, true)
  }


  override def requestVote(term: Long, candidateId: String, lastLogIndex: Long, lastLogTerm: Long): VoteResult = {
    if (term < state.currentTerm) {
      VoteResult(-1, false)
    }

    state.votedFor match {
      case None =>
        // TODO: currentTerm, for candidate to update itself
        VoteResult(???, true)
      case Some(vote) if vote == candidateId =>
        // TODO: currentTerm, for candidate to update itself
        VoteResult(???, true)
      case _ => VoteResult(???, false)
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