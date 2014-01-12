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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

/****************************************************************
 * FSInputStream is a generic old InputStream with a little bit
 * of RAF-style seek ability.
 *
 *****************************************************************/
public abstract class FSInputStream extends InputStream
    implements Seekable, PositionedReadable {

  /**
   * Seek to the given offset from the start of the file.
   * The next read() will be from that location.  Can't
   * seek past the end of the file.
   */
  public abstract void seek(long pos) throws IOException;

  /**
   * Return the current offset from the start of the file
   */
  public abstract long getPos() throws IOException;

  /**
   * Seeks a different copy of the data.  Returns true if 
   * found a new source, false otherwise.
   */
  public abstract boolean seekToNewSource(long targetPos) throws IOException;

  public int read(long position, byte[] buffer, int offset, int length)
    throws IOException {
    synchronized (this) {
      long oldPos = getPos();
      int nread = -1;
      try {
        seek(position);
        nread = read(buffer, offset, length);
      } finally {
        seek(oldPos);
      }
      return nread;
    }
  }
    
  public void readFully(long position, byte[] buffer, int offset, int length)
    throws IOException {
    int nread = 0;
    while (nread < length) {
      int nbytes = read(position+nread, buffer, offset+nread, length-nread);
      if (nbytes < 0) {
        throw new EOFException("End of file reached before reading fully.");
      }
      nread += nbytes;
    }
  }

  public synchronized int readwithdeadline(byte[] buffer, int offset, int length, long deadline)
          throws IOException {
    //the default behavior is reading without deadline
    return read(buffer, offset, length);
  }


  /**
   * SHOULD be implemented by the subclasses
   * if the subclass is connecting to the remote server, it would return the
   * socket
   * @return the socket connecting to the server
   */
  public Socket getRemoteChannel() {
    return null;
  }

  public int readwithdeadline(long position, byte[] buffer, int offset, int length, long deadline)
          throws IOException {
    return 0;
  }
    
  public void readFully(long position, byte[] buffer)
    throws IOException {
    readFully(position, buffer, 0, buffer.length);
  }

}
