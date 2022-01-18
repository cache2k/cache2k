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

/**
 * Used to record and check the integrity. We support to record 64 different
 * constraints to produce a single long state value.
 *
 * @author Jens Wilke
 */
@SuppressWarnings("unused")
public class IntegrityState {

  private final StringBuilder builder = new StringBuilder();
  private boolean failure = false;
  private String groupPrefix = "";
  private int bitNr = 0;

  /**
   *
   * @param check Name of the test. Used when we build an exception.
   * @param f test outcome, true when success
   */
  protected IntegrityState check(String check, String note, boolean f) {
    if (check == null || check.length() == 0) {
      check = "test#" + bitNr;
    }
    if (!f) {
      if (builder.length() > 0) {
        builder.append(", ");
      }
      builder.append(groupPrefix).append(check);
      if (note != null) {
          builder.append('(').append(note).append(')');
      }
      failure = true;
    }
    bitNr++;
    return this;
  }

  public IntegrityState group(String group) {
    if (group == null || group.isEmpty()) {
      groupPrefix = "";
    } else {
      groupPrefix = group + ":";
    }
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

  public boolean isFailure() {
    return failure;
  }

  public String getFailingChecks() {
    return builder.toString();
  }

}
