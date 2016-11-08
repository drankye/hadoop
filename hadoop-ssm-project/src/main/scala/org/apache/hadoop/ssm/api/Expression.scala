package org.apache.hadoop.ssm.api

import org.apache.hadoop.ssm._
import org.apache.hadoop.ssm.Property
import org.apache.hadoop.ssm.Action
import java.time.Duration

object Expression {
  sealed trait Operator

  case object AND extends Operator

  case object OR extends Operator

  case class TreeNode(value: Operator, left: TreeNode, right: TreeNode){
    def and(other: TreeNode): TreeNode = {
      new TreeNode(AND, this, other)
    }

    def or(other: TreeNode): TreeNode = {
      new TreeNode(OR, this, other)
    }

    def cache: SSMRule = {
      new SSMRule(null, this, Action.CACHE)
    }
  }

  case class SSMRule(fileFilterRule: FileFilterRule[String], root: TreeNode, action: Action) {
    def getId(): Long = {
      this.hashCode()
    }
  }

  sealed trait PropertyManipulation

  case object Historical extends PropertyManipulation

  case class Window(size: Duration, step: Duration) extends PropertyManipulation

  class Rule[T](condition: Condition[T]) extends Operator {
    def meetCondition(arg: T): Boolean = {
      condition(arg)
    }
  }

  case class FileFilterRule[T](condition: Condition[T]) extends Rule(condition)

  case class PropertyFilterRule[T](condition: Condition[T], property: Property,
    propertyManipulation: PropertyManipulation = Historical) extends Rule(condition) {

    def in(stateManipulation: PropertyManipulation): PropertyFilterRule[T] = {
      new PropertyFilterRule(condition, property, stateManipulation)
    }
  }

  object Rule {
    implicit def ruleToTreenode[T](rule: Rule[T]): TreeNode = {
      new TreeNode(rule, null, null)
    }
  }
}
