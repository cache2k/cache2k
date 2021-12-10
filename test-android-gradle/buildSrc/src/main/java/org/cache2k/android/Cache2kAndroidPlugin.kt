package org.cache2k.android

/*
 * #%L
 * cache2k Android integration
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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.fileTree
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import java.io.File

/**
 * Custom build logic for hooking the Maven-based Cache2k core modules
 * to the Gradle-based Android infrastructure.
 *
 * This plugin has multiple responsibilities, e.g. declaring dependencies on
 * local Cache2k modules from a Gradle context, as well as ensuring
 * that Maven tasks are up-to-date before the instrumentation tests are executed.
 *
 * @author Marcel Schnelle
 */
class Cache2kAndroidPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        // Connect directly to the Android Gradle plugin
        target.plugins.withId("com.android.library") {
            applyInternal(target)
        }
    }

    /* Private */

    private fun applyInternal(project: Project) {
        // Register Cache2k dependencies for Android instrumentation tests
        registerCache2kDependencies(project)

        // Connect Maven packaging task to Android instrumentation tests
        registerMavenPackagingTask(project)
    }

    private fun registerCache2kDependencies(project: Project) =
            with(project) {
                // Depend on Cache2k test suite and its transitive dependencies
                dependencies.add("androidTestImplementation", cache2kDependency("api"))
                dependencies.add("androidTestImplementation", cache2kDependency("core"))
                dependencies.add("androidTestImplementation", cache2kDependency("pinpoint"))
                dependencies.add("androidTestImplementation", cache2kDependency("testing"))
                dependencies.add("androidTestImplementation", cache2kDependency("testsuite"))
            }

    private val Project.repositoryRoot: File
        get() = rootDir.parentFile

    private fun Project.cache2kDependency(moduleName: String): ConfigurableFileTree {
        val moduleFolder = repositoryRoot.resolve("cache2k-$moduleName")

        return project.fileTree(
                "dir" to moduleFolder.resolve("target"),
                "include" to "*.jar",
                "exclude" to listOf("*sources*", "*javadoc*")
        )
    }

    private fun registerMavenPackagingTask(project: Project) {
        // Create a Gradle task that in turn invokes Maven's compilation task
        // then make that task a precondition of Android's instrumentation test compilation
        val mavenTask = project.tasks.register<Exec>("packageCache2kTestsuiteWithMaven") {
            workingDir = project.repositoryRoot
            commandLine("mvn -DskipTests clean package -pl :cache2k-testsuite -am -T1C".split(" "))
        }

        project.tasks.withType<JavaCompile> {
            if (name == "compileDebugAndroidTestJavaWithJavac") {
                dependsOn(mavenTask)
            }
        }
    }
}
