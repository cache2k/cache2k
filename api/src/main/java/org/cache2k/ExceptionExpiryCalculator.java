package org.cache2k;

/*
 * #%L
 * cache2k API only package
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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

/**
 * Special expiry calculator to calculate a separate expiry time if an
 * exceptions was thrown in the cache source. The general idea is that
 * depending on the nature of the exception (e.g. temporary or permanent)
 * a different expiry value can determined.
 *
 * @author Jens Wilke; created: 2014-10-16
 * @since 0.20
 * @deprecated replaced with {@link org.cache2k.customization.ExceptionExpiryCalculator}
 */
public interface ExceptionExpiryCalculator<K> extends org.cache2k.customization.ExceptionExpiryCalculator<K> {

  long calculateExpiryTime(K key, Throwable _throwable, long _fetchTime);

}
