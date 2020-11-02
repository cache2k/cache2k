package org.cache2k.config;

/*
 * #%L
 * cache2k API
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

import org.cache2k.Cache2kBuilder;

import java.util.Iterator;

/**
 * Base class for a cache feature that is enabled or not and appears only once in the feature list.
 * A feature can be disabled in two ways: Either by setting enabled to {@code false} or by removing
 * it from the feature set.
 *
 * @author Jens Wilke
 */
public abstract class ToggleFeature implements Feature {

  public static ToggleFeature enable(Cache2kBuilder<?, ?> builder,
                            Class<? extends ToggleFeature> featureType) {
    try {
      ToggleFeature feature = featureType.getConstructor().newInstance();
      builder.config().getFeatures().add(feature);
      return feature;
    } catch (Exception e) {
      throw new LinkageError("Instantiation failed", e);
    }
  }

  public static void disable(Cache2kBuilder<?, ?> builder,
                            Class<? extends ToggleFeature> featureType) {
    Iterator<Feature> it = builder.config().getFeatures().iterator();
    while (it.hasNext()) {
      if (it.next().getClass().equals(featureType)) {
        it.remove();
      }
    }
  }

  private boolean enabled = true;

  /**
   * Check whether enabled and call implementations' doEnlist method.
   */
  @Override
  public final void enlist(CacheBuildContext<?, ?> ctx) {
    if (enabled) {
      doEnlist(ctx);
    }
  }

  protected abstract void doEnlist(CacheBuildContext<?, ?> ctx);

  public final boolean isEnabled() {
    return enabled;
  }

  public final void setEnabled(boolean v) {
    this.enabled = v;
  }

  /**
   * Alternate setter for spelling flexibility in XML configuration.
   */
  public final void setEnable(boolean v) {
    this.enabled = v;
  }

  /**
   * Identical if its the same implementation class.
   */
  @Override
  public final boolean equals(Object o) {
    return getClass().equals(o);
  }

  /**
   * Hashcode from the implementation class.
   */
  @Override
  public final int hashCode() {
    return getClass().hashCode();
  }

  /**
   * Override if this takes additional parameters.
   */
  @Override
  public String toString() {
    return getClass().getSimpleName() + '{' + (enabled ? "enabled" : "disabled") + '}';
  }
}
