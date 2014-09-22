package org.cache2k.impl;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2014 headissue GmbH, Munich
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
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
