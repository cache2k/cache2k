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

import org.cache2k.testing.category.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cache2k.config.Cache2kConfig.UNSET_LONG;
import static org.cache2k.core.eviction.EvictionFactory.determineSegmentCount;

/**
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class InternalCache2kBuilderTest {

  @Test
  public void determineSegmentCount_strictEviction() {
    int segs = determineSegmentCount(
      true, 12, true, 1000000,
      UNSET_LONG, 32);
    assertThat(segs).isEqualTo(1);
  }

  @Test
  public void determineSegmentCount_regularConcurrency() {
    int segs = determineSegmentCount(
      false, 12, false, 1000000,
      UNSET_LONG, 0);
    assertThat(segs).isEqualTo(2);
  }

  @Test
  public void determineSegmentCount_boostConcurrency() {
    int segs = determineSegmentCount(
      false, 12, true,
      1000000, UNSET_LONG, 0);
    assertThat(segs).isEqualTo(16);
  }

  @Test
  public void determineSegmentCount_noSegmentationBelow1000Entries() {
    int segs = determineSegmentCount(
      false, 12,  true,
      999, UNSET_LONG, 0);
    assertThat(segs).isEqualTo(1);
  }

  @Test
  public void determineSegmentCount_override12() {
    int segs = determineSegmentCount(
      false, 12, true,
      1000000, UNSET_LONG, 12);
    assertThat(segs).isEqualTo(16);
  }

  @Test
  public void determineSegmentCount_override1() {
    int segs = determineSegmentCount(
      false, 12, true,
      1000000, UNSET_LONG, 1);
    assertThat(segs).isEqualTo(1);
  }

  @Test
  public void determineSegmentCount_override16() {
    int segs = determineSegmentCount(
      false, 12, true,
      1000000, UNSET_LONG, 16);
    assertThat(segs).isEqualTo(16);
  }

  @Test
  public void determineSegmentCount_override17() {
    int segs = determineSegmentCount(
      false, 12, true, 1000000,
      UNSET_LONG, 17);
    assertThat(segs).isEqualTo(32);
  }

  @Test
  public void determineSegmentCount_override32() {
    int segs = determineSegmentCount(
      false, 12, true, 1000000,
      UNSET_LONG, 32);
    assertThat(segs).isEqualTo(32);
  }

}
