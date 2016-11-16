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

package domain;

/**
 * These are Lassie dogs.
 *
 * Incidentally though Lassie was a she, the actors were male dogs
 * as they have the bigger coats.
 * @author Greg Luck
 */
public class RoughCoatedCollie extends Dog implements Collie {

  /**
   * Tells the Collie to herd
   */
  @Override
  public void herd() {

  }

  protected RoughCoatedCollie getThis() {
    return this;
  }

}

