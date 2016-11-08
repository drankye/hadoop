package org.apache.hadoop.ssm.api

object Action extends Enumeration {
  type Action = Value
  val CACHE = Value("cache")
  val ARCHIVE = Value("archive")

  def parse(value: String): Action.Value = Action.values.filter(value == _.toString).head
}
