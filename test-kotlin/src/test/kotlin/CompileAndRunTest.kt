/*-
 * #%L
 * Kotlin tests
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
import org.assertj.core.api.Assertions.assertThatCode
import org.cache2k.Cache
import org.cache2k.Cache2kBuilder
import org.junit.jupiter.api.Test
import java.lang.NullPointerException
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

/**
 * @author Jens Wilke
 */
class CompileAndRunTest {

    /**
     * Kotlin bug https://youtrack.jetbrains.com/issue/KT-43262
     */
    @Test
    fun test() {
        assertThatCode {
            val cache = object : Cache2kBuilder<Int, Int>() { }.build()
            val map: MutableMap<Int, Int> = cache.asMap()
            map.entries.forEach(Consumer { t -> t.setValue(123) })
            // compile error:
            //- map.entries.forEach(Consumer { t -> t.setValue(null) })
            // compiles, expected: compile error
            cache.asMap().entries.forEach(Consumer { t -> t.setValue(null) })
            // compiles, expected: compile error
            cache.putAll(mapOf(Pair(null, 123)))
        }.isInstanceOf(NullPointerException::class.java)
    }

    /**
     * CHM does not support null keys, but we can declare it
     */
    @Test
    fun concurrentHashMap() {
        assertThatCode {
            val map = ConcurrentHashMap<Int?, Int>()
            map.putAll(mapOf(Pair(null, 123)))
        }.isInstanceOf(NullPointerException::class.java)
    }

    /**
     * https://youtrack.jetbrains.com/issue/KT-43262
     */
    @Test
    fun cacheWithNullKey() {
        assertThatCode {
            val cache = object : Cache2kBuilder<Int?, Int>() { }.build()
            cache.putAll(mapOf(Pair(null, 123)))
        }.isInstanceOf(NullPointerException::class.java)
    }

    @Test
    fun cacheWithJavaTypes() {
        val cache: Cache<Int, Int> =
            Cache2kBuilder.of(Int::class.java, Int::class.java)
                    .permitNullValues(true)
                    .build()
    }

    @Test
    fun entryProcessor() {
        val cache = object : Cache2kBuilder<Int, Int>() { }.build()
        val value1: Int? = cache.invoke(123) { e -> e.value }
        val value2: Int? = cache.invoke(123) { e -> e.value }
        // unfortunately we need to use Int? here
        val value3: Int? = cache.invoke(123) { e -> 123 }
    }

}
