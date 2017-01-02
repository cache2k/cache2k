package org.cache2k.core.util;

/*
 * #%L
 * cache2k core
 * %%
 * Copyright (C) 2000 - 2017 headissue GmbH, Munich
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
 * Base class of all classes that contain code constants. For each
 * cache2k implementation class constants a centralized within an inner class.
 * The rationale behind this is explained in the following.
 *
 * <p>Wisely chosen constants are sometimes buried within the code.
 * These are the so called "magic numbers". So lets give them a default
 * place.
 *
 * <p>There may be a need to change such a "constant". This provides a simple system
 * wide mechanism to change a parameter, aka "tune" it. So, this can be used for
 * performance optimizations. It may be also possible to provide a tuning
 * set that goes tunes towards execution time or towards space efficiency.
 *
 * <p>Testing: Some code has operations that happen very seldom, e.g. for
 * reorganizing. For testing purposes we can trigger these situations by
 * de-tuning.
 *
 * <p>If there is a constant need to change a constant, please open a change
 * request. Either it is better to change the tunable constant to a real parameter
 * or a assign it to another value which fits the general purpose better.
 *
 * @author Jens Wilke; created: 2014-04-27
 */
public class TunableConstants implements Cloneable {

  @SuppressWarnings("CloneDoesntDeclareCloneNotSupportedException")
  @Override
  public  Object clone() {
    try {
      Object o = super.clone();
      return o;
    } catch (CloneNotSupportedException e) {
      throw new UnsupportedOperationException("never happens", e);
    }
  }

}
