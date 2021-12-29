/*-
 * #%L
 * Kotlin tests
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
import org.cache2k.Cache2kBuilder

/**
 * Various expressions that are assigned to a wrong type to check
 * which type Kotlin is deriving from the Java code. In general we
 * look out that Kotlin is not assuming platform types (Int!) and
 * is not allowing us to set null values or keys.
 *
 * @author Jens Wilke
 */
class DoesNotCompileTest {

    // comment out the start of comment below and check compiler warnings
    /*
    fun basicProperties() {
        var xy: Xy
        val cache = object : Cache2kBuilder<Int, Int>() { }.build()
        xy = cache.name
        xy = cache.toString()
    }

    fun getSet() {
        var xy: Xy
        val cache = object : Cache2kBuilder<Int, Int>() { }.build()
        cache.put(null, 123)
        cache.put(213, null)
        xy = cache.get(123)
    }

    fun entryProcessor() {
        var xy: Xy
        val cache = object : Cache2kBuilder<Int, Int>() { }.build()
        xy = cache.invoke(123) { e -> e.value }
        xy = cache.invoke(123) { e -> e.value }
        xy = cache.invoke(123) { e -> 123 }
    }
    // */
}

class Xy { }
