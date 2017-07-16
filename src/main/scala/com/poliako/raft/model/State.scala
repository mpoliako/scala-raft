package com.poliako.raft.model

class PersistentState(var currentTerm: Long, val votedFor: Option[String], var log: Array[Entry])

class VolatileState(var commitIndex: Long, val lastApplied: Long, val nextIndex: Array[Long], val matchIndex: Array[String], currentTerm: Long, votedFor: Option[String], log: Array[Entry])
  extends PersistentState(currentTerm, votedFor, log)

sealed trait ServerState
object Follower extends ServerState
object Candidate extends ServerState
object Leader extends ServerState