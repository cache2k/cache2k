package org.cache2k.pinpoint.stress.pairwise;

/*
 * #%L
 * cache2k pinpoint
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

/**
 * The two actors will be executed concurrently in tests.
 *
 * @author Jens Wilke
 * @param <R> the result of the actor
 * @see ActorPairSuite
 */
public interface ActorPair<R> {

  /**
   * Setup or reset action executed before the actors.
   */
  void setup();

  /**
   * First actor executed concurrently with actor2.
   *
   * @return outcome of the actor
   */
  R actor1();

  /**
   * First actor executed concurrently with actor2.
   *
   * @return outcome of the actor
   */
  R actor2();

  /**
   * Checks the outcome after both actors have finished. The method is expected
   * to throw an exception or assertion error in case the result is unexpected.
   *
   * @param r1 outcome of {@link #actor1()}
   * @param r2 outcome of {@link #actor2()}
   */
  void check(R r1, R r2);

  /**
   * Useful for assertions based on success of each actor.
   */
  class SuccessTuple {
    private final boolean success1;
    private final boolean success2;

    public SuccessTuple(final boolean success1, final boolean success2) {
      this.success1 = success1;
      this.success2 = success2;
    }

    public boolean isSuccess1() { return success1; }

    public boolean isSuccess2() { return success2; }

    public boolean isBothSucceed() { return success1 && success2; }

    public boolean isOneSucceeds() { return success1 != success2; }

    @Override
    public String toString() {
      return "SuccessTuple{" +
        "success1=" + success1 +
        ", success2=" + success2 +
        '}';
    }
  }
}
