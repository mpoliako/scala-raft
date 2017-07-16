package com.poliako.raft.model

case class Entry(term: Long, index: Long, body: Array[Byte])
