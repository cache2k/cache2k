/**
 * API package for cache2k Java caching library.
 *
 * <p>A {@link org.cache2k.Cache} can be created the {@link org.cache2k.Cache2kBuilder}. Besides the
 * parameters that can be set via the builder, a cache can be further customized and extended via
 * a {@link org.cache2k.io.CacheLoader}, {@link org.cache2k.io.CacheWriter},
 * {@link org.cache2k.expiry.ExpiryPolicy} or {@link org.cache2k.io.ResiliencePolicy}.
 *
 * @author Jens Wilke
 * @see <a href="https://cache2k.org>cache2k homepage</a>
 * @see <a href="https://cache2k.org/docs/latest/user-guide.html">cache2k User Guide</a>
 */
@NonNullApi
package org.cache2k;

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

import org.cache2k.annotation.NonNullApi;
