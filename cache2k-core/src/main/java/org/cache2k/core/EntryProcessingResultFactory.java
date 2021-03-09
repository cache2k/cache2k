package org.cache2k.core;

/*
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2021 headissue GmbH, Munich
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

import org.cache2k.annotation.Nullable;
import org.cache2k.processor.EntryProcessingException;
import org.cache2k.processor.EntryProcessingResult;

/**
 * @author Jens Wilke
 */
@SuppressWarnings("Convert2Diamond")
public class EntryProcessingResultFactory {

  public static <R> EntryProcessingResult<R> result(R result) {
    return new EntryProcessingResult<R>() {
      @Override
      public @Nullable R getResult() {
        return result;
      }

      @Override
      public @Nullable Throwable getException() {
        return null;
      }
    };
  }

  public static <R> EntryProcessingResult<R> exception(Throwable exception) {
    if (exception instanceof EntryProcessingException) {
      exception = exception.getCause();
    }
    Throwable finalException = exception;
    return new EntryProcessingResult<R>() {
      @Override
      public @Nullable R getResult() {
        throw new EntryProcessingException(finalException);
      }

      @Override
      public @Nullable Throwable getException() {
        return finalException;
      }
    };
  }

}
