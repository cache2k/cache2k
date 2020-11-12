package org.cache2k.addon;

/*
 * #%L
 * cache2k addon
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
import org.checkerframework.checker.nullness.qual.Nullable;
import org.cache2k.config.SectionBuilder;
import org.cache2k.config.ConfigSection;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * @author Jens Wilke
 */
public class UniversalResilienceConfig
  implements ConfigSection<UniversalResilienceConfig, UniversalResilienceConfig.Builder> {

  private int retryPercentOfResilienceDuration = 10;
  private @Nullable Duration minRetryInterval = Duration.ZERO;
  private @Nullable Duration retryInterval = null;
  private @Nullable Duration maxRetryInterval = null;
  private @Nullable Duration resilienceDuration = null;
  private double backoffMultiplier = 1.5;

  /**
   * @see Builder#retryInterval
   */
  public @Nullable Duration getRetryInterval() {
    return retryInterval;
  }

  /**
   * @see Builder#retryInterval
   */
  public void setRetryInterval(@Nullable Duration v) {
    this.retryInterval = v;
  }

  /**
   * @see Builder#maxRetryInterval
   */
  public @Nullable Duration getMaxRetryInterval() {
    return maxRetryInterval;
  }

  /**
   * @see Builder#maxRetryInterval
   */
  public void setMaxRetryInterval(@Nullable Duration v) {
    this.maxRetryInterval = v;
  }

  /**
   * @see Builder#resilienceDuration
   */
  public @Nullable Duration getResilienceDuration() {
    return resilienceDuration;
  }

  /**
   * @see Builder#resilienceDuration
   */
  public void setResilienceDuration(@Nullable Duration v) {
    this.resilienceDuration = v;
  }

  /**
   * @see Builder#backoffMultiplier
   */
  public double getBackoffMultiplier() {
    return backoffMultiplier;
  }

  /**
   * @see Builder#backoffMultiplier
   */
  public void setBackoffMultiplier(double backoffMultiplier) {
    this.backoffMultiplier = backoffMultiplier;
  }

  /**
   * @see Builder#retryPercentOfResilienceDuration
   */
  public int getRetryPercentOfResilienceDuration() {
    return retryPercentOfResilienceDuration;
  }

  /**
   * @see Builder#retryPercentOfResilienceDuration
   */
  public void setRetryPercentOfResilienceDuration(int retryPercentOfResilienceDuration) {
    this.retryPercentOfResilienceDuration = retryPercentOfResilienceDuration;
  }

  /**
   * @see Builder#minRetryInterval
   */
  public @Nullable Duration getMinRetryInterval() {
    return minRetryInterval;
  }

  public void setMinRetryInterval(@Nullable Duration minRetryInterval) {
    this.minRetryInterval = minRetryInterval;
  }

  @Override
  public Builder builder() {
    return new Builder(this);
  }

  public static class Builder implements SectionBuilder<Builder, UniversalResilienceConfig> {

    private UniversalResilienceConfig config;

    private Builder(UniversalResilienceConfig config) {
      this.config = config;
    }

    /**
     * If a loader exception happens, this is the time interval after a
     * retry attempt is made. If not specified, 10% of {@link #maxRetryInterval}.
     */
    public final Builder retryInterval(long v, TimeUnit u) {
      config.setRetryInterval(toDuration(v, u));
      return this;
    }

    /**
     * If a loader exception happens, this is the maximum time interval after a
     * retry attempt is made. For retries an exponential backoff algorithm is used.
     * It starts with the retry time and then increases the time to the maximum
     * according to an exponential pattern.
     *
     * <p>By default identical to {@link #resilienceDuration}
     */
    public final Builder maxRetryInterval(long v, TimeUnit u) {
      config.setMaxRetryInterval(toDuration(v, u));
      return this;
    }

    /**
     * Time span the cache will suppress loader exceptions if a value is available from
     * a previous load. After the time span is passed the cache will start propagating
     * loader exceptions.
     */
    public final Builder resilienceDuration(long v, TimeUnit u) {
      config.setResilienceDuration(toDuration(v, u));
      return this;
    }

    /**
     * Multiplier for exponential backoff if multiple exceptions occur in sequence.
     * Default is {@code 1.5}.
     */
    public final Builder backoffMultiplier(double v) {
      config.setBackoffMultiplier(v);
      return this;
    }

    /**
     * A minimum value of retry interval if its not explicitly set and derived
     * from resilience duration or expireAfterWrite.
     */
    public final Builder minRetryInterval(Duration v) {
      config.setMinRetryInterval(v);
      return this;
    }

    /**
     * If retry values are not specified and a resilience duration
     * available, calculates the retry interval time from the resilience
     * duration. Default 10, meaning 10 percent.
     */
    public final Builder retryPercentOfResilienceDuration(int v) {
      config.setRetryPercentOfResilienceDuration(v);
      return this;
    }
    @Override
    public UniversalResilienceConfig config() {
      return config;
    }

  }

  private static Duration toDuration(long v, TimeUnit u) {
    return Duration.ofMillis(u.toMillis(v));
  }

}
