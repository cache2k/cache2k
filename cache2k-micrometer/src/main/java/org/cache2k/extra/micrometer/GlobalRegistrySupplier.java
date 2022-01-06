package org.cache2k.extra.micrometer;

/*-
 * #%L
 * cache2k micrometer monitoring support
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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import org.cache2k.config.CacheBuildContext;
import org.cache2k.config.CustomizationSupplier;

/**
 * @author Jens Wilke
 */
public final class GlobalRegistrySupplier implements CustomizationSupplier<MeterRegistry> {

  public static final GlobalRegistrySupplier INSTANCE = new GlobalRegistrySupplier();

  @Override
  public MeterRegistry supply(CacheBuildContext buildContext) {
    return Metrics.globalRegistry;
  }

}
