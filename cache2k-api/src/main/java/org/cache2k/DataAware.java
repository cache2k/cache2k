package org.cache2k;

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

import org.cache2k.annotation.NonNull;

/**
 * Parent for all interfaces dealing with cached data. This is used to establish a common
 * contract for the K and V type parameter.
 *
 * @author Jens Wilke
 * @param <K> non null type for the cache key
 * @param <V> non null type for the cache value. Although cache2k support null values
 *           by {@link Cache2kBuilder#permitNullValues(boolean)} this is not the common
 *           use case
 * @since 2.0
 */
public interface DataAware<@NonNull K, @NonNull V> { }
