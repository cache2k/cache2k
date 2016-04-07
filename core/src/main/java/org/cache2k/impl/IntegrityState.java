package org.cache2k.impl;

/*
 * #%L
 * cache2k core package
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

import java.util.ArrayList;
import java.util.List;

/**
 * Used to record and check the integrity. We support to record 64 different
 * constraints to produce a single long state value.
 *
 * @author Jens Wilke; created: 2013-07-11
 */
@SuppressWarnings("unused")
public class IntegrityState {

  List<String> failingTests = new ArrayList<String>();
  long state = 0;
  long bitNr = 0;
  int stringsHashCode = 0;

  IntegrityState check(boolean f) {
    check(null, f);
    return this;
  }

  /**
   *
   * @param _check Name of the test. Used when we build an exception.
   * @param f test outcome, true when success
   */
  protected IntegrityState check(String _check, String _note, boolean f) {
    if (_check == null || _check.length() == 0) {
      _check = "test#" + bitNr;
    }
    stringsHashCode = stringsHashCode * 31 + _check.hashCode();
    if (!f) {
      if (_note != null) {
        failingTests.add('"' + _check + "\" => " + _note);
      } else {
        failingTests.add('"' + _check+ '"');
      }
      state |= 1 << bitNr;
    }
    bitNr++;
    return this;
  }

  public IntegrityState check(String _check, boolean f) {
    check(_check, null, f);
    return this;
  }

  public IntegrityState checkEquals(String _check, int v1, int v2) {
    if (v1 == v2) {
      check(_check, null, true);
    } else {
      check(_check, v1 + "==" + v2, false);
    }
    return this;
  }

  public IntegrityState checkEquals(String _check, long v1, long v2) {
    if (v1 == v2) {
      check(_check, null, true);
    } else {
      check(_check, v1 + "==" + v2, false);
    }
    return this;
  }

  public IntegrityState checkLessOrEquals(String _check, int v1, int v2) {
    if (v1 <= v2) {
      check(_check, null, true);
    } else {
      check(_check, v1 + "<=" + v2, false);
    }
    return this;
  }

  public IntegrityState checkLess(String _check, int v1, int v2) {
    if (v1 < v2) {
      check(_check, null, true);
    } else {
      check(_check, v1 + "<" + v2, false);
    }
    return this;
  }

  public IntegrityState checkGreaterOrEquals(String _check, int v1, int v2) {
    if (v1 >= v2) {
      check(_check, null, true);
    } else {
      check(_check, v1 + ">=" + v2, false);
    }
    return this;
  }

  public IntegrityState checkGreater(String _check, int v1, int v2) {
    if (v1 > v2) {
      check(_check, null, true);
    } else {
      check(_check, v1 + ">" + v2, false);
    }
    return this;
  }

  public String getStateDescriptor() {
    return Long.toHexString(state) + '.' + bitNr + '.' + Integer.toHexString(stringsHashCode);
  }

  public long getStateFlags() { return state; }

  public String getFailingChecks() { return failingTests.toString(); }

  public void throwIfNeeded() {
    if (state > 0) {
      throw new IllegalStateException("Integrity test failed: " + failingTests.toString());
    }
  }

}
