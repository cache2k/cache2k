package org.cache2k.core.eviction;

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

import org.cache2k.core.api.InternalCacheCloseContext;

/**
 * Merges in idle processing to eviction.
 *
 * @author Jens Wilke
 */
public class IdleProcessingEviction extends DelegatingEviction {

  private IdleProcessing idleProcessing;
  private Eviction eviction;

  public IdleProcessingEviction(Eviction eviction, IdleProcessing idleProcessing) {
    this.idleProcessing = idleProcessing;
    this.eviction = eviction;
  }

  @Override
  protected Eviction delegate() {
    return eviction;
  }

  @Override
  public void close(InternalCacheCloseContext closeContext) {
    idleProcessing.close(closeContext);
    super.close(closeContext);
  }

  @Override
  public String toString() {
    return idleProcessing.toString() + ", " + super.toString();
  }

}
