/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.mapred;

/**
 * Used to describe the priority of the running job. 
 *
 */
public enum JobPriority {

  VERY_HIGH(5),
  HIGH(4),
  NORMAL(3),
  LOW(2),
  VERY_LOW(1);

  private int value = 0;

  private JobPriority(int value) {
    this.value = value;
  }

  public static JobPriority valueOf(int v) {
    switch (v) {
      case 1:
        return VERY_LOW;
      case 2:
        return LOW;
      case 3:
        return NORMAL;
      case 4:
        return HIGH;
      case 5:
        return VERY_HIGH;
      default:
        return null;
    }
  }

  @Override
  public String toString() {
    return String.valueOf(value);
  }

  public int value() {
    return value;
  }
}
