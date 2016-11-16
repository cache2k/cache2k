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

import java.io.Serializable;

/**
 * A Beagle is a Dog and a type of hound.
 *
 * @author Greg Luck
 */
public class Beagle extends Dog implements Hound, Serializable {


  public Beagle getThis() {
    return this;
  }

  /**
   * Tells the hound to bay
   *
   * @param loudness 0 for mute, 1 is the softest and 255 is the loudest
   * @param duration the duraction of the bay in seconds
   */
  public void bay(int loudness, int duration) {

  }




}
