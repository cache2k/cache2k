package org.cache2k.core;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2020 headissue GmbH, Munich
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
  String groupPrefix = "";

  IntegrityState check(boolean f) {
    check(null, f);
    return this;
  }

  /**
   *
   * @param check Name of the test. Used when we build an exception.
   * @param f test outcome, true when success
   */
  protected IntegrityState check(String check, String note, boolean f) {
    if (check == null || check.length() == 0) {
      check = "test#" + bitNr;
    }
    stringsHashCode = stringsHashCode * 31 + check.hashCode();
    if (!f) {
      if (note != null) {
        failingTests.add(groupPrefix + '"' + check + "\" => " + note);
      } else {
        failingTests.add(groupPrefix + '"' + check+ '"');
      }
      state |= 1 << bitNr;
    }
    bitNr++;
    return this;
  }

  public IntegrityState group(String group) {
    groupPrefix = group + ": ";
    return this;
  }

  public IntegrityState check(String check, boolean f) {
    check(check, null, f);
    return this;
  }

  public IntegrityState checkEquals(String check, int v1, int v2) {
    if (v1 == v2) {
      check(check, null, true);
    } else {
      check(check, v1 + "==" + v2, false);
    }
    return this;
  }

  public IntegrityState checkEquals(String check, long v1, long v2) {
    if (v1 == v2) {
      check(check, null, true);
    } else {
      check(check, v1 + "==" + v2, false);
    }
    return this;
  }

  public IntegrityState checkLessOrEquals(String check, int v1, int v2) {
    if (v1 <= v2) {
      check(check, null, true);
    } else {
      check(check, v1 + "<=" + v2, false);
    }
    return this;
  }

  public IntegrityState checkLess(String check, int v1, int v2) {
    if (v1 < v2) {
      check(check, null, true);
    } else {
      check(check, v1 + "<" + v2, false);
    }
    return this;
  }

  public IntegrityState checkGreaterOrEquals(String check, int v1, int v2) {
    if (v1 >= v2) {
      check(check, null, true);
    } else {
      check(check, v1 + ">=" + v2, false);
    }
    return this;
  }

  public IntegrityState checkGreater(String check, int v1, int v2) {
    if (v1 > v2) {
      check(check, null, true);
    } else {
      check(check, v1 + ">" + v2, false);
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
