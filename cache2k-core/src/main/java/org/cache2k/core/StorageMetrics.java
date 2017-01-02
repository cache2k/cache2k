package org.cache2k.core;

/*
 * #%L
 * cache2k core
 * %%
 * Copyright (C) 2000 - 2017 headissue GmbH, Munich
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
 * @author Jens Wilke
 */
public interface StorageMetrics {

  Dummy DUMMY = new Dummy();

  /**
   * Entry was read from storage and fresh.
   */
  long getReadHitCount();

  /**
   * Storage did not contain the requested entry.
   */
  long getReadMissCount();

  /**
   * Read from storage, but the entry was not fresh and cannot be returned.
   */
  long getReadNonFreshCount();

  interface Updater extends StorageMetrics {

    void readHit();
    void readHit(long cnt);

    void readNonFresh();
    void readNonFresh(long cnt);

    void readMiss();
    void readMiss(long cnt);

  }

  class Dummy implements Updater {

    @Override
    public void readHit() {

    }

    @Override
    public void readHit(final long cnt) {

    }

    @Override
    public void readNonFresh() {

    }

    @Override
    public void readNonFresh(final long cnt) {

    }

    @Override
    public void readMiss() {

    }

    @Override
    public void readMiss(final long cnt) {

    }

    @Override
    public long getReadHitCount() {
      return 0;
    }

    @Override
    public long getReadMissCount() {
      return 0;
    }

    @Override
    public long getReadNonFreshCount() {
      return 0;
    }
  }

}
