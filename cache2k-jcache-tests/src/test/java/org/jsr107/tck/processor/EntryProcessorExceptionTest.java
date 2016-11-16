/**
 *  Copyright 2011-2013 Terracotta, Inc.
 *  Copyright 2011-2013 Oracle, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jsr107.tck.processor;

import org.junit.Test;

import javax.cache.processor.EntryProcessorException;

/**
 * Tests the exception for completeness
 * @author Greg Luck
 */
public class EntryProcessorExceptionTest {


  @Test
  public void testEntryProcessorException() {
    try {
      throw new EntryProcessorException();
    } catch (EntryProcessorException e) {
      //
    }
  }

  @Test
  public void testEntryProcessorExceptionCause() {
    try {
      throw new EntryProcessorException(new NullPointerException());
    } catch (EntryProcessorException e) {
      //
    }
  }

  @Test
  public void testEntryProcessorExceptionCauseAndMessage() {
    try {
      throw new EntryProcessorException("Doh!", new NullPointerException());
    } catch (EntryProcessorException e) {
      //
    }
  }

  @Test
  public void testEntryProcessorExceptionMessage() {
    try {
      throw new EntryProcessorException("Doh!");
    } catch (EntryProcessorException e) {
      //
    }
  }

}
