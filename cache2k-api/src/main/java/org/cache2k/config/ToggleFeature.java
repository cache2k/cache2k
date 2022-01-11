package org.cache2k.config;

/*-
 * #%L
 * cache2k API
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

import org.cache2k.Cache2kBuilder;
import org.cache2k.annotation.Nullable;

import java.util.Set;

/**
 * Base class for a cache feature that can be enabled or disables and
 * appears only once in the feature set. A feature can be disabled in
 * two ways: Either by setting enabled to {@code false} or by removing
 * it from the feature set.
 *
 * <p>This allows enablement of features based on the users' preference:
 * A feature can be enabled by default and then disabled per individual
 * cache or just enabled at the individual cache level.
 *
 * @author Jens Wilke
 */
public abstract class ToggleFeature implements SingleFeature {

  /**
   * Enable the feature in the main configuration. If the feature is
   * already existing it is replaced by a newly created one.
   *
   * @return the created feature to set additional parameters
   */
  public static <T extends ToggleFeature> T enable(Cache2kBuilder<?, ?> builder,
                                                   Class<T> featureType) {
    try {
      T feature = featureType.getConstructor().newInstance();
      Set<Feature> features = builder.config().getFeatures();
      features.remove(feature);
      features.add(feature);
      return feature;
    } catch (Exception e) {
      throw new LinkageError("Instantiation failed", e);
    }
  }

  /**
   * Disable the feature by removing it from the configuration.
   */
  public static void disable(Cache2kBuilder<?, ?> builder,
                            Class<? extends ToggleFeature> featureType) {
    builder.config().getFeatures().removeIf(feature -> feature.getClass().equals(featureType));
  }

  /**
   * Returns the feature instance, if present.
   */
  @SuppressWarnings("unchecked")
  public static <T extends ToggleFeature> @Nullable T extract(Cache2kBuilder<?, ?> builder,
                                                              Class<T> featureType) {
    for (Feature feature : builder.config().getFeatures()) {
      if (feature.getClass().equals(featureType)) {
        return (T) feature;
      }
    }
    return null;
  }

  /**
   * Returns true if the feature is enabled. Meaning, the feature instance is present
   * and enabled.
   */
  public static boolean isEnabled(Cache2kBuilder<?, ?> builder,
                                  Class<? extends ToggleFeature> featureType) {
    ToggleFeature f = extract(builder, featureType);
    return f != null && f.isEnabled();
  }

  /**
   * Feature is enabled by default. The enabled field is intended for
   * usage with an external configuration, to disable a feature
   * which was enabled globally e.g. {@code false}
   */
  private boolean enabled = true;

  /**
   * Checks whether enabled and call implementations doEnlist method.
   */
  @Override
  public final <K, V> void enlist(CacheBuildContext<K, V> ctx) {
    if (enabled) {
      doEnlist(ctx);
    }
  }

  protected abstract <K, V> void doEnlist(CacheBuildContext<K, V> ctx);

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
   * Identical if it is the same implementation class. Relevant for
   * keeping only one feature within the configuration.
   */
  @Override
  public final boolean equals(@Nullable Object o) {
    if (o == null) { return false; }
    return getClass().equals(o.getClass());
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
