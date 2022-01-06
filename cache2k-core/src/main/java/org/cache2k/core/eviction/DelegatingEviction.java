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

import org.cache2k.core.Entry;
import org.cache2k.core.IntegrityState;
import org.cache2k.core.api.InternalCacheCloseContext;

import java.util.function.Supplier;

/**
 * Delegates all eviction interaction.
 *
 * @author Jens Wilke
 */
public abstract class DelegatingEviction implements Eviction {

  protected abstract Eviction delegate();

  @Override
  public long startNewIdleScanRound() {
    return delegate().startNewIdleScanRound();
  }

  @Override
  public void close(InternalCacheCloseContext closeContext) {
    delegate().close(closeContext);
  }

  @Override
  public boolean submitWithoutTriggeringEviction(Entry e) {
    return delegate().submitWithoutTriggeringEviction(e);
  }

  @Override
  public boolean updateWeight(Entry e) {
    return delegate().updateWeight(e);
  }

  @Override
  public void evictEventuallyBeforeInsertOnSegment(int hashCodeHint) {
    delegate().evictEventuallyBeforeInsertOnSegment(hashCodeHint);
  }

  @Override
  public void evictEventuallyBeforeInsert() {
    delegate().evictEventuallyBeforeInsert();
  }

  @Override
  public void evictEventually() {
    delegate().evictEventually();
  }

  @Override
  public long evictIdleEntries(int maxScan) {
    return delegate().evictIdleEntries(maxScan);
  }

  @Override
  public long removeAll() {
    return delegate().removeAll();
  }

  @Override
  public <T> T runLocked(Supplier<T> j) {
    return delegate().runLocked(j);
  }

  @Override
  public void checkIntegrity(IntegrityState integrityState) {
    delegate().checkIntegrity(integrityState);
  }

  @Override
  public EvictionMetrics getMetrics() {
    return delegate().getMetrics();
  }

  @Override
  public boolean isWeigherPresent() {
    return delegate().isWeigherPresent();
  }

  @Override
  public void changeCapacity(long entryCountOrWeight) {
    delegate().changeCapacity(entryCountOrWeight);
  }

  @Override
  public String toString() {
    return delegate().toString();
  }

}
