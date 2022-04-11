package org.cache2k.core;

/*-
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2022 headissue GmbH, Munich
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

import org.cache2k.testing.category.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.text.SimpleDateFormat;
import java.util.Date;

import static java.lang.Integer.MAX_VALUE;
import static java.util.TimeZone.getTimeZone;
import static org.assertj.core.api.Assertions.assertThat;
import static org.cache2k.core.Entry.ProcessingState.*;
import static org.cache2k.core.Entry.num2processingStateText;

/**
 * @author Jens Wilke
 */
@SuppressWarnings({"rawtypes", "SynchronizationOnLocalVariableOrMethodParameter"})
@Category(FastTests.class)
public class EntryTest {

  @Test
  public void testLastModifiedStoresValue() {
    Entry e = new Entry();
    synchronized (e) {
      e.setModificationTime(4711);
      assertThat(e.getModificationTime()).isEqualTo(4711);
      e.setModificationTime(123456);
      assertThat(e.getModificationTime()).isEqualTo(123456);
    }
  }

  @Test
  public void testLastModified50YearsRange() {
    long millis50years = 50L * 365 * 24 * 60 * 60 * 1000;
    long t = System.currentTimeMillis() + millis50years;
    Entry e = new Entry();
    synchronized (e) {
      e.setModificationTime(t);
      assertThat(e.getModificationTime()).isEqualTo(t);
    }
  }

  @Test
  public void testLastModifiedMaxRange() {
    Entry e = new Entry();
    synchronized (e) {
      e.setModificationTime(Long.MAX_VALUE);
      SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      df.setTimeZone(getTimeZone("GMT"));
      assertThat(df.format(new Date(e.getModificationTime()))).isEqualTo("2248-09-26 15:10:22");
    }
  }

  @Test
  public void testProcessingStateInitial() {
    Entry e = new Entry();
    assertThat(e.getProcessingState()).isEqualTo(DONE);
  }

  @Test
  public void testProcessingFetch() {
    Entry e = new Entry();
    synchronized (e) {
      e.setRawExpiry(4711);
      e.startProcessing(REFRESH, null);
      assertThat(e.isGettingRefresh()).isTrue();
      e.processingDone();
    }
    assertThat(e.getProcessingState()).isEqualTo(DONE);
  }

  @Test
  public void testHot() {
    Entry e = new Entry();
    assertThat(e.isHot()).isFalse();
    e.setHot(true);
    assertThat(e.isHot()).isTrue();
    e.setHot(false);
    assertThat(e.isHot()).isFalse();
  }

  /**
   * Just check that toString is producing no exception on empty entry.
   */
  @Test
  public void virginToString() {
    Entry e = new Entry();
    e.toString();
  }

  @Test
  public void timeSpan32Bit() {
    int days = MAX_VALUE
      / 1000
      / 60
      / 60
      / 24;
    assertThat(days).isEqualTo(24);
  }

  @Test
  public void num2processingState() {
    assertThat(num2processingStateText(DONE)).isEqualTo("DONE");
    assertThat(num2processingStateText(READ)).isEqualTo("READ");
    assertThat(num2processingStateText(READ_COMPLETE)).isEqualTo("READ_COMPLETE");
    assertThat(num2processingStateText(MUTATE)).isEqualTo("MUTATE");
    assertThat(num2processingStateText(LOAD)).isEqualTo("LOAD");
    assertThat(num2processingStateText(COMPUTE)).isEqualTo("COMPUTE");
    assertThat(num2processingStateText(REFRESH)).isEqualTo("REFRESH");
    assertThat(num2processingStateText(EXPIRY)).isEqualTo("EXPIRY");
    assertThat(num2processingStateText(EXPIRY_COMPLETE)).isEqualTo("EXPIRY_COMPLETE");
    assertThat(num2processingStateText(WRITE)).isEqualTo("WRITE");
    assertThat(num2processingStateText(WRITE_COMPLETE)).isEqualTo("WRITE_COMPLETE");
    assertThat(num2processingStateText(STORE)).isEqualTo("STORE");
    assertThat(num2processingStateText(STORE_COMPLETE)).isEqualTo("STORE_COMPLETE");
    assertThat(num2processingStateText(NOTIFY)).isEqualTo("NOTIFY");
    assertThat(num2processingStateText(PINNED)).isEqualTo("PINNED");
    assertThat(num2processingStateText(LAST)).isEqualTo("LAST");
  }

  @Test
  public void scanRound() {
    Entry e = new Entry();
    assertThat(e.getScanRound()).isEqualTo(0);
    e.setScanRound(15);
    assertThat(e.getScanRound()).isEqualTo(15);
    e.setHot(true);
    e.setCompressedWeight(4711);
    assertThat(e.getScanRound()).isEqualTo(15);
  }

}
