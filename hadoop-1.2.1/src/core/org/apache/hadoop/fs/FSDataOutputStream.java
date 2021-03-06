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
package org.apache.hadoop.fs;

import java.io.*;

/** Utility that wraps a {@link OutputStream} in a {@link DataOutputStream},
 * buffers output through a {@link BufferedOutputStream} and creates a checksum
 * file. */
public class FSDataOutputStream extends DataOutputStream implements Syncable {
  private OutputStream wrappedStream;

  public int jobid;
  public int reqtype;
  public int reqvalue;

  private static class PositionCache extends FilterOutputStream {
    private FileSystem.Statistics statistics;
    long position;

    public PositionCache(OutputStream out, 
                         FileSystem.Statistics stats,
                         long pos) throws IOException {
      super(out);
      statistics = stats;
      position = pos;
    }

    public void write(int b) throws IOException {
      out.write(b);
      position++;
      if (statistics != null) {
        statistics.incrementBytesWritten(1);
      }
    }
    
    public void write(byte b[], int off, int len) throws IOException {
      out.write(b, off, len);
      position += len;                            // update position
      if (statistics != null) {
        statistics.incrementBytesWritten(len);
      }
    }

    /**
     *
     * @param b
     * @param off
     * @param len
     * @param type
     * @param value
     * @throws IOException
     */
    public void write(byte b[], int off, int len, int type, long value) throws IOException {
      ((FSOutputSummer) out).write(b, off, len, type, value);
      position += len;                            // update position
      if (statistics != null) {
        statistics.incrementBytesWritten(len);
      }
    }

    public long getPos() throws IOException {
      return position;                            // return cached position
    }
    
    public void close() throws IOException {
      out.close();
    }
  }

  @Deprecated
  public FSDataOutputStream(OutputStream out) throws IOException {
    this(out, null);
  }

  public FSDataOutputStream(OutputStream out, FileSystem.Statistics stats)
    throws IOException {
    this(out, stats, 0);
  }

  public FSDataOutputStream(OutputStream out, FileSystem.Statistics stats,
                            long startPosition) throws IOException {
    super(new PositionCache(out, stats, startPosition));
    wrappedStream = out;    //wrapped stream would be the DFSOutputStream
    if (wrappedStream instanceof FSOutputSummer) {

    }
  }
  
  public long getPos() throws IOException {
    return ((PositionCache)out).getPos();
  }

  public void close() throws IOException {
    out.close();         // This invokes PositionCache.close()
  }

  // Returns the underlying output stream. This is used by unit tests.
  public OutputStream getWrappedStream() {
    return wrappedStream;
  }

  public void write(byte b[], int off, int len, int type, long value) throws IOException {
    //FSOutputSummer overrides it
    System.out.println("in write(byte b[], int off, int len, int type, long value) @FSDataOutputStream");
    ((PositionCache) out).write(b, off, len, type, value);
    //reimplement incCount,
    int temp = written + len;
    if (temp < 0) {
      temp = Integer.MAX_VALUE;
    }
    written = temp;
  }

  /**
   *
   * @param b
   * @param type
   * @param value
   * @throws IOException
   */
  public void write(byte b[], int type, long value) throws IOException {
    write(b, 0, b.length, type, value);
  }

  /** {@inheritDoc} */
  public void sync() throws IOException {
    if (wrappedStream instanceof Syncable) {
      ((Syncable)wrappedStream).sync();
    } else {
      wrappedStream.flush();
    }
  }

  public void setRequestType(int type) {
    if (wrappedStream instanceof FSOutputSummer) {
      ((FSOutputSummer) wrappedStream).setRequestType(type);
    }
  }

  public void setRequestValue(long value) {
    if (wrappedStream instanceof FSOutputSummer) {
      ((FSOutputSummer) wrappedStream).setRequestValue(value);
    }
  }

  public long getRequestValue() {
    return ((FSOutputSummer) wrappedStream).getRequestvalue();
  }

  public int getRequestType() {
    return ((FSOutputSummer) wrappedStream).getRequesttype();
  }
}
