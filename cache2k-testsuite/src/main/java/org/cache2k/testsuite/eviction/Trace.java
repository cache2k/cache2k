package org.cache2k.testsuite.eviction;

/*
 * #%L
 * cache2k testsuite on public API
 * %%
 * Copyright (C) 2000 - 2021 headissue GmbH, Munich
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;

/**
 * Read compact integer traces from the class path. The integer array is shared
 * between tests for efficiency reasons and must not be modified.
 *
 * @author Jens Wilke
 */
public class Trace implements Supplier<int[]> {

  /**
   * Access trace on the product catalog on an eCommerce website of 24 hours starting from
   * 4am to 4am the next morning. All requests of robots (web spiders from search engines
   * like Google or Bing) were removed, so the trace contains mostly user activity.
   * The requests were reduced by removing a random selection of keys. The first integer of
   * the trace is the time in seconds, the second one the accessed key. Both integers are
   * normalized starting at 0. The trace characteristics are: request count: 66319,
   * unique keys: 8810, maximum hitrate: 86.72
   */
  public final static Trace WEBLOG424_NOROBOTS = new Trace("weblog-424-norobots.slt.gz");

  /**
   * Same access trace as {@link #WEBLOG424_NOROBOTS} containing also the robot activity.
   * The robots do access a big amount of outdated product data with no repetition and low
   * overlap.
   */
  public final static Trace WEBLOG424_ROBOTS = new Trace("weblog-424.slt.gz");

  private final Supplier<int[]> supplier;
  private int[] data = null;

  public Trace(Supplier<int[]> supplier) {
    this.supplier = supplier;
  }

  /** Compressed trace from class path */
  public Trace(String fileName) {
    this(() -> readTrace(fileName));
  }

  @Override
  public int[] get() {
    if (data != null) {
      return data;
    }
    return data = supplier.get();
  }

  static int[] readTrace(String fileName) {
    int count = 0;
    int[] buffer = new int[2048];
    try {
      DataInputStream dataInput =
        new DataInputStream(
          new GZIPInputStream(Trace.class.getResourceAsStream(fileName)));
      for (;;) {
        if (count >= buffer.length) {
          int[] copy = new int[buffer.length * 2];
          System.arraycopy(buffer, 0, copy, 0, buffer.length);
          buffer = copy;
        }
        buffer[count] = dataInput.readInt();
        count++;
      }
    } catch (EOFException eof) {
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
    int[] result = new int[count];
    System.arraycopy(buffer, 0, result, 0, count);
    return result;
  }

}
