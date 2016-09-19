package org.cache2k.core;

/*
 * #%L
 * cache2k core
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

import org.cache2k.CacheException;
import org.cache2k.junit.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.*;

/**
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class CacheManagerImplTest {

  @Test
  public void eventuallyThrowException_empty() {
    List<Throwable> li = new ArrayList<Throwable>();
    CacheManagerImpl.eventuallyThrowException(li);
  }

  @Test(expected = CacheException.class)
  public void eventuallyThrowException_normalException() {
    List<Throwable> li = new ArrayList<Throwable>();
    li.add(new IllegalArgumentException());
    CacheManagerImpl.eventuallyThrowException(li);
    fail("exception expected");
  }

  @Test(expected = CacheInternalError.class)
  public void eventuallyThrowException_error() {
    List<Throwable> li = new ArrayList<Throwable>();
    li.add(new LinkageError());
    CacheManagerImpl.eventuallyThrowException(li);
    fail("exception expected");
  }

  @Test(expected = CacheInternalError.class)
  public void eventuallyThrowException_ExecutionExceptionWithEWrror() {
    List<Throwable> li = new ArrayList<Throwable>();
    li.add(new ExecutionException(new LinkageError()));
    CacheManagerImpl.eventuallyThrowException(li);
    fail("exception expected");
  }

  @Test(expected = CacheInternalError.class)
  public void eventuallyThrowException_ExceptionAndError() {
    List<Throwable> li = new ArrayList<Throwable>();
    li.add(new IllegalArgumentException());
    li.add(new LinkageError());
    CacheManagerImpl.eventuallyThrowException(li);
    fail("exception expected");
  }

}
