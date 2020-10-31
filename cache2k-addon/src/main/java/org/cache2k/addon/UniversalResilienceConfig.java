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
import org.cache2k.config.SectionBuilder;
import org.cache2k.config.ConfigSection;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * @author Jens Wilke
 */
public class UniversalResilienceConfig
  implements ConfigSection
  <UniversalResilienceConfig, UniversalResilienceConfig.Builder> {

  private boolean suppressExceptions = true;

  private Duration retryInterval = null;
  private Duration maxRetryInterval = null;
  private Duration resilienceDuration = null;

  public boolean isSuppressExceptions() {
    return suppressExceptions;
  }

  public void setSuppressExceptions(boolean suppressExceptions) {
    this.suppressExceptions = suppressExceptions;
  }

  public Duration getRetryInterval() {
    return retryInterval;
  }

  public void setRetryInterval(Duration retryInterval) {
    this.retryInterval = retryInterval;
  }

  public Duration getMaxRetryInterval() {
    return maxRetryInterval;
  }

  public void setMaxRetryInterval(Duration maxRetryInterval) {
    this.maxRetryInterval = maxRetryInterval;
  }

  public Duration getResilienceDuration() {
    return resilienceDuration;
  }

  public void setResilienceDuration(Duration resilienceDuration) {
    this.resilienceDuration = resilienceDuration;
  }

  @Override
  public Builder builder() {
    return new Builder(this);
  }

  public static class Builder implements SectionBuilder<UniversalResilienceConfig> {

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
     * loader exceptions. If {@link #suppressExceptions} is switched off, this setting
     * has no effect.
     *
     * <p>Defaults to {@link Cache2kBuilder#expireAfterWrite}. If {@link #suppressExceptions}
     * is switched off, this setting has no effect.
     */
    public final Builder resilienceDuration(long v, TimeUnit u) {
      config.setResilienceDuration(toDuration(v, u));
      return this;
    }

    /**
     * Suppress an exception from the cache loader, if there is previous data.
     * When a load was not successful, the operation is retried at shorter interval then
     * the normal expiry, see {@link #retryInterval(long, TimeUnit)}.
     *
     * <p>Exception suppression is only active when entries expire (eternal is not true) or the
     * explicit configuration of the timing parameters for resilience, e.g.
     * {@link #resilienceDuration(long, TimeUnit)}. Check the user guide chapter for details.
     *
     * <p>Exception suppression is enabled by default. Setting this to {@code false}, will disable
     * exceptions suppression (aka resilience).
     *
     * @see <a href="https://cache2k.org/docs/latest/user-guide.html#resilience-and-exceptions">
     *   cache2k user guide - Exceptions and Resilience</a>
     */
    public final Builder suppressExceptions(boolean v) {
      config.setSuppressExceptions(v);
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
