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
   *
   * @param r1 outcome of {@link #actor1()}
   * @param r2 outcome of {@link #actor2()}
   */
  void check(R r1, R r2);

}
