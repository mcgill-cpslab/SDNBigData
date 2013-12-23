/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.util.collection

import org.scalatest.FunSuite


class OpenHashSetSuite extends FunSuite {

  test("primitive int") {
    val set = new OpenHashSet[Int]
    assert(set.size === 0)
    assert(!set.contains(10))
    assert(!set.contains(50))
    assert(!set.contains(999))
    assert(!set.contains(10000))

    set.add(10)
    assert(set.contains(10))
    assert(!set.contains(50))
    assert(!set.contains(999))
    assert(!set.contains(10000))

    set.add(50)
    assert(set.size === 2)
    assert(set.contains(10))
    assert(set.contains(50))
    assert(!set.contains(999))
    assert(!set.contains(10000))

    set.add(999)
    assert(set.size === 3)
    assert(set.contains(10))
    assert(set.contains(50))
    assert(set.contains(999))
    assert(!set.contains(10000))

    set.add(50)
    assert(set.size === 3)
    assert(set.contains(10))
    assert(set.contains(50))
    assert(set.contains(999))
    assert(!set.contains(10000))
  }

  test("primitive long") {
    val set = new OpenHashSet[Long]
    assert(set.size === 0)
    assert(!set.contains(10L))
    assert(!set.contains(50L))
    assert(!set.contains(999L))
    assert(!set.contains(10000L))

    set.add(10L)
    assert(set.size === 1)
    assert(set.contains(10L))
    assert(!set.contains(50L))
    assert(!set.contains(999L))
    assert(!set.contains(10000L))

    set.add(50L)
    assert(set.size === 2)
    assert(set.contains(10L))
    assert(set.contains(50L))
    assert(!set.contains(999L))
    assert(!set.contains(10000L))

    set.add(999L)
    assert(set.size === 3)
    assert(set.contains(10L))
    assert(set.contains(50L))
    assert(set.contains(999L))
    assert(!set.contains(10000L))

    set.add(50L)
    assert(set.size === 3)
    assert(set.contains(10L))
    assert(set.contains(50L))
    assert(set.contains(999L))
    assert(!set.contains(10000L))
  }

  test("non-primitive") {
    val set = new OpenHashSet[String]
    assert(set.size === 0)
    assert(!set.contains(10.toString))
    assert(!set.contains(50.toString))
    assert(!set.contains(999.toString))
    assert(!set.contains(10000.toString))

    set.add(10.toString)
    assert(set.size === 1)
    assert(set.contains(10.toString))
    assert(!set.contains(50.toString))
    assert(!set.contains(999.toString))
    assert(!set.contains(10000.toString))

    set.add(50.toString)
    assert(set.size === 2)
    assert(set.contains(10.toString))
    assert(set.contains(50.toString))
    assert(!set.contains(999.toString))
    assert(!set.contains(10000.toString))

    set.add(999.toString)
    assert(set.size === 3)
    assert(set.contains(10.toString))
    assert(set.contains(50.toString))
    assert(set.contains(999.toString))
    assert(!set.contains(10000.toString))

    set.add(50.toString)
    assert(set.size === 3)
    assert(set.contains(10.toString))
    assert(set.contains(50.toString))
    assert(set.contains(999.toString))
    assert(!set.contains(10000.toString))
  }

  test("non-primitive set growth") {
    val set = new OpenHashSet[String]
    for (i <- 1 to 1000) {
      set.add(i.toString)
    }
    assert(set.size === 1000)
    assert(set.capacity > 1000)
    for (i <- 1 to 100) {
      set.add(i.toString)
    }
    assert(set.size === 1000)
    assert(set.capacity > 1000)
  }

  test("primitive set growth") {
    val set = new OpenHashSet[Long]
    for (i <- 1 to 1000) {
      set.add(i.toLong)
    }
    assert(set.size === 1000)
    assert(set.capacity > 1000)
    for (i <- 1 to 100) {
      set.add(i.toLong)
    }
    assert(set.size === 1000)
    assert(set.capacity > 1000)
  }
}
