package com.poliako.raft.model

class PersistentState(var currentTerm: Long, var votedFor: Option[String], val serverId: String, var log: Array[Entry])

class VolatileState(var commitIndex: Long, var lastApplied: Long, val nextIndex: Array[Long], val matchIndex: Array[String], currentTerm: Long, votedFor: Option[String], serverId: String, log: Array[Entry])
  extends PersistentState(currentTerm, votedFor, serverId, log)

sealed trait ServerState
object Follower extends ServerState
object Candidate extends ServerState
object Leader extends ServerState