package com.poliako.raft.model

case class Entry(index: Long, body: Array[Byte])
