package org.apache.hadoop.ssm.api

object Property extends Enumeration{
  type Property = Value
  val ACCESSCOUNT = Value("accessCount")
  val AGE = Value("age")

  def parse(value: String): Property.Value = Property.values.filter(value == _.toString).head
}
