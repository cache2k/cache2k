package org.cache2k.core;

/*
 * #%L
 * cache2k core
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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

import org.cache2k.junit.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import static org.junit.Assert.*;

/**
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class EntryTest {

  @Test
  public void testLastModifiedStoresValue() {
    Entry e = new Entry();
    e.setLastModification(4711);
    assertEquals(4711, e.getLastModification());
    e.setLastModification(123456);
    assertEquals(123456, e.getLastModification());
  }

  @Test
  public void testLastModifiedDirty() {
    Entry e = new Entry();
    e.setLastModification(4711);
    assertTrue(e.isDirty());
    assertEquals(4711, e.getLastModification());
  }

  @Test
  public void testLastModifiedResetDirty() {
    Entry e = new Entry();
    e.setLastModification(4711);
    assertEquals(4711, e.getLastModification());
    e.resetDirty();
    assertFalse(e.isDirty());
    assertEquals(4711, e.getLastModification());
  }

  @Test
  public void testLastModifiedClean() {
    Entry e = new Entry();
    e.setLastModificationFromStorage(4711);
    assertFalse(e.isDirty());
    assertEquals(4711, e.getLastModification());
  }

  @Test
  public void testLastModified50YearsRange() {
    long _50yearsMillis = 50L * 365 * 24 * 60 * 60 * 1000;
    long t = System.currentTimeMillis() + _50yearsMillis;
    Entry e = new Entry();
    e.setLastModification(t);
    assertEquals(t, e.getLastModification());
  }

  @Test
  public void testLastModifiedMaxRange() {
    Entry e = new Entry();
    e.setLastModification(Long.MAX_VALUE);
    SimpleDateFormat df = new SimpleDateFormat("yyyy-MMM-dd HH:mm:ss");
    df.setTimeZone(TimeZone.getTimeZone("GMT"));
    assertEquals("2248-Sep-26 15:10:22", df.format(new Date(e.getLastModification())));
  }

  /** initial value, pretty meaningless, since we always should set the modification time */
  @Test
  public void testDirtyInitial() {
    Entry e = new Entry();
    assertTrue(e.isDirty());
  }

  @Test
  public void testDirtySetModificationTime() {
    Entry e = new Entry();
    long _time = 4711;
    e.setLastModification(_time);
    assertTrue(e.isDirty());
    assertEquals(_time , e.getLastModification());
  }

  @Test
  public void testDirtySetModificationTimeResetDirty() {
    Entry e = new Entry();
    long _time = 4711;
    e.setLastModification(_time);
    assertTrue(e.isDirty());
    assertEquals(_time , e.getLastModification());
    e.resetDirty();
    assertFalse(e.isDirty());
  }

  @Test
  public void testDirtySetModificationTimeFromStorage() {
    Entry e = new Entry();
    long _time = 4711;
    e.setLastModificationFromStorage(_time);
    assertFalse(e.isDirty());
    assertEquals(_time , e.getLastModification());
  }

  @Test
  public void testProcessingStateInitial() {
    Entry e = new Entry();
    assertEquals(Entry.ProcessingState.DONE, e.getProcessingState());
  }

  @Test
  public void testProcessingFetch() {
    Entry e = new Entry();
    e.nextRefreshTime = 4711;
    synchronized (e) {
      e.startProcessing(Entry.ProcessingState.REFRESH);
      assertTrue(e.isGettingRefresh());
      e.processingDone();
    }
    assertEquals(Entry.ProcessingState.DONE, e.getProcessingState());
  }

  @Test
  public void testStale() {
    Entry e = new Entry();
    assertFalse(e.isStale());
    e.setStale();
    assertTrue(e.isStale());
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
    int _days = Integer.MAX_VALUE
      / 1000
      / 60
      / 60
      / 24;
    assertEquals(24, _days);
  }

}
