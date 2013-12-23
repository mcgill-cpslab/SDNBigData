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

package org.apache.spark.streaming.examples;

import com.google.common.collect.Lists;
import scala.Tuple2;
import org.apache.spark.api.java.function.FlatMapFunction;
import org.apache.spark.api.java.function.Function2;
import org.apache.spark.api.java.function.PairFunction;
import org.apache.spark.streaming.Duration;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaPairDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;

/**
 * Counts words in UTF8 encoded, '\n' delimited text received from the network every second.
 * Usage: NetworkWordCount <master> <hostname> <port>
 *   <master> is the Spark master URL. In local mode, <master> should be 'local[n]' with n > 1.
 *   <hostname> and <port> describe the TCP server that Spark Streaming would connect to receive data.
 *
 * To run this on your local machine, you need to first run a Netcat server
 *    `$ nc -lk 9999`
 * and then run the example
 *    `$ ./run spark.streaming.examples.JavaNetworkWordCount local[2] localhost 9999`
 */
public class JavaNetworkWordCount {
  public static void main(String[] args) {
    if (args.length < 3) {
      System.err.println("Usage: NetworkWordCount <master> <hostname> <port>\n" +
          "In local mode, <master> should be 'local[n]' with n > 1");
      System.exit(1);
    }

    // Create the context with a 1 second batch size
    JavaStreamingContext ssc = new JavaStreamingContext(args[0], "NetworkWordCount",
            new Duration(1000), System.getenv("SPARK_HOME"), System.getenv("SPARK_EXAMPLES_JAR"));

    // Create a NetworkInputDStream on target ip:port and count the
    // words in input stream of \n delimited test (eg. generated by 'nc')
    JavaDStream<String> lines = ssc.socketTextStream(args[1], Integer.parseInt(args[2]));
    JavaDStream<String> words = lines.flatMap(new FlatMapFunction<String, String>() {
      @Override
      public Iterable<String> call(String x) {
        return Lists.newArrayList(x.split(" "));
      }
    });
    JavaPairDStream<String, Integer> wordCounts = words.map(
      new PairFunction<String, String, Integer>() {
        @Override
        public Tuple2<String, Integer> call(String s) throws Exception {
          return new Tuple2<String, Integer>(s, 1);
        }
      }).reduceByKey(new Function2<Integer, Integer, Integer>() {
        @Override
        public Integer call(Integer i1, Integer i2) throws Exception {
          return i1 + i2;
        }
      });

    wordCounts.print();
    ssc.start();

  }
}
