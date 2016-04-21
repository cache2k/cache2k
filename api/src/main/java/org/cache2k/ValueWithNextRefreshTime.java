package org.cache2k;

/*
 * #%L
 * cache2k API
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

import org.cache2k.customization.ValueWithExpiryTime;

/**
 * Interface for use with {@link org.cache2k.AutoEntryRefreshController}
 *
 * @author Jens Wilke; created: 2013-05-02
 * @deprecated Replaces bye {@link ValueWithExpiryTime}
 */
public interface ValueWithNextRefreshTime {

  /**
   * Return time of next refresh (expiry time). A return value of 0 means the
   * entry expires immediately, or is always fetched from the source. A return value of
   * {@link Long#MAX_VALUE} means there is no specific expiry time
   * known or needed. In this case a reasonable default can be assumed for
   * the expiry, the cache will use the configured expiry time.
   */
  public long getNextRefreshTime();

}
