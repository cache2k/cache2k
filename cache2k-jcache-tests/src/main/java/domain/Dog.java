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
 * A Dog.
 *
 * @author Greg Luck
 */
public class Dog {


  private Identifier name;
  private String color;
  private int weightInKg;
  private long lengthInCm;
  private long heightInCm;
  private Sex sex;
  private boolean neutered;

  public Dog() {
    //empty constructor
  }

  public Dog(Identifier name) {
    this.name = name;
  }

  public Dog(Identifier name, String color, int weightInKg, long lengthInCm, long heightInCm, Sex sex, boolean neutered) {
    this.name = name;
    this.color = color;
    this.weightInKg = weightInKg;
    this.lengthInCm = lengthInCm;
    this.heightInCm = heightInCm;
    this.sex = sex;
    this.neutered = neutered;
  }

  protected Dog getThis() {
    return this;
  }

  public Identifier getName() {
    return name;
  }

  public void setName(Identifier name) {
    this.name = name;
  }

  public Dog name(Identifier name) {
    this.name = name;
    return getThis();
  }

  public String getColor() {
    return color;
  }


  public Dog color(String color) {
    this.color = color;
    return getThis();
  }

  public int getWeight() {
    return weightInKg;
  }

  public Dog weight(int weighInKg) {
    this.weightInKg = weighInKg;
    return getThis();
  }

  public long getLengthInCm() {
    return lengthInCm;
  }

  public Dog length(long lengthInCm) {
    this.lengthInCm = lengthInCm;
    return getThis();
  }


  public long getHeight() {
    return heightInCm;
  }

  public Dog height(long heightInCm) {
    this.heightInCm = heightInCm;
    return getThis();
  }

  public Sex getSex() {
    return sex;
  }

  public Dog sex(Sex sex) {
    this.sex = sex;
    return getThis();
  }

  public boolean isNeutered() {
    return neutered;
  }

  public Dog neutered(boolean neutered) {
    this.neutered = neutered;
    return getThis();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Dog)) return false;

    Dog dog = (Dog) o;

    if (heightInCm != dog.heightInCm) return false;
    if (lengthInCm != dog.lengthInCm) return false;
    if (neutered != dog.neutered) return false;
    if (weightInKg != dog.weightInKg) return false;
    if (color != null ? !color.equals(dog.color) : dog.color != null) return false;
    if (name != null ? !name.equals(dog.name) : dog.name != null) return false;
    if (sex != dog.sex) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = getName() != null ? getName().hashCode() : 0;
    result = 31 * result + (getColor() != null ? getColor().hashCode() : 0);
    result = 31 * result + getWeight();
    result = 31 * result + (int) (getLengthInCm() ^ (getLengthInCm() >>> 32));
    result = 31 * result + (int) (getHeight() ^ (getHeight() >>> 32));
    result = 31 * result + (getSex() != null ? getSex().hashCode() : 0);
    result = 31 * result + (isNeutered() ? 1 : 0);
    return result;
  }


}
