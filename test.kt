package org.jetbrains.kotlin.gradle.plugin

import com.android.build.gradle.BaseExtension
import com.android.build.gradle.BasePlugin
import com.android.builder.model.SourceProvider
import groovy.lang.Closure
import org.gradle.api.*
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.attributes.Usage
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.InvalidPluginException
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.CompileClasspathNormalizer
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetOutput
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptionsImpl
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleJavaTargetExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.internal.Kapt3GradleSubplugin.Companion.getKaptGeneratedClassesDir
import org.jetbrains.kotlin.gradle.internal.Kapt3KotlinGradleSubplugin
import org.jetbrains.kotlin.gradle.internal.KaptVariantData
import org.jetbrains.kotlin.gradle.internal.checkAndroidAnnotationProcessorDependencyUsage
import org.jetbrains.kotlin.gradle.model.builder.KotlinModelBuilder
import org.jetbrains.kotlin.gradle.plugin.mpp.*
import org.jetbrains.kotlin.gradle.scripting.internal.ScriptingGradleSubplugin
import org.jetbrains.kotlin.gradle.tasks.*
import org.jetbrains.kotlin.gradle.utils.*
import java.io.File
import java.net.URL
import java.util.concurrent.Callable
import java.util.jar.Manifest

const val PLUGIN_CLASSPATH_CONFIGURATION_NAME = "kotlinCompilerPluginClasspath"
const val NATIVE_COMPILER_PLUGIN_CLASSPATH_CONFIGURATION_NAME = "kotlinNativeCompilerPluginClasspath"
internal const val COMPILER_CLASSPATH_CONFIGURATION_NAME = "kotlinCompilerClasspath"
val KOTLIN_DSL_NAME = "kotlin"
val KOTLIN_JS_DSL_NAME = "kotlin2js"
val KOTLIN_OPTIONS_DSL_NAME = "kotlinOptions"

internal abstract class KotlinSourceSetProcessor<T : AbstractKotlinCompile<*>>(
        val project: Project,
        val tasksProvider: KotlinTasksProvider,
        val taskDescription: String,
        val kotlinCompilation: KotlinCompilation
) {
    protected abstract fun doTargetSpecificProcessing()
    protected val logger = Logging.getLogger(this.javaClass)!!

    protected val isSeparateClassesDirSupported: Boolean by lazy {
        !CopyClassesToJavaOutputStatus.isEnabled(project) && isGradleVersionAtLeast(4, 0)
    }

    protected val sourceSetName: String = kotlinCompilation.compilationName

    protected val kotlinTask: T = createKotlinCompileTask()

    protected val javaSourceSet: SourceSet? = (kotlinCompilation as? KotlinWithJavaCompilation)?.javaSourceSet

    protected open val defaultKotlinDestinationDir: File
        get() {
            return if (isSeparateClassesDirSupported) {
                val kotlinExt = project.kotlinExtension
                val targetSubDirectory =
                        if (kotlinExt is KotlinSingleJavaTargetExtension)
                            "" // In single-target projects, don't add the target name part to this path
                        else
                            kotlinCompilation.target.disambiguationClassifier?.let { "$it/" }.orEmpty()
                File(project.buildDir, "classes/kotlin/$targetSubDirectory${kotlinCompilation.compilationName}")
            } else {
                kotlinCompilation.output.classesDirs.singleFile
            }
        }

    private fun createKotlinCompileTask(): T {
        val name = kotlinCompilation.compileKotlinTaskName
        logger.kotlinDebug("Creating kotlin compile task $name")
        val kotlinCompile = doCreateTask(project, name)
        kotlinCompile.description = taskDescription
        kotlinCompile.mapClasspath { kotlinCompilation.compileDependencyFiles }
        kotlinCompile.setDestinationDir { defaultKotlinDestinationDir }
        kotlinCompilation.output.tryAddClassesDir { project.files(kotlinTask.destinationDir).builtBy(kotlinTask) }
        return kotlinCompile
    }

    open fun run() {
        addKotlinDirectoriesToJavaSourceSet()
        doTargetSpecificProcessing()

        if (kotlinCompilation is KotlinWithJavaCompilation) {
            createAdditionalClassesTaskForIdeRunner()
        }
    }

    private fun addKotlinDirectoriesToJavaSourceSet() {
        if (javaSourceSet == null)
            return

        // Try to avoid duplicate Java sources in allSource; run lazily to allow changing the directory set:
        val kotlinSrcDirsToAdd = Callable {
            kotlinCompilation.kotlinSourceSets.map { filterOutJavaSrcDirsIfPossible(it.kotlin) }
        }

        javaSourceSet.allJava.srcDirs(kotlinSrcDirsToAdd)
        javaSourceSet.allSource.srcDirs(kotlinSrcDirsToAdd)
    }

    private fun filterOutJavaSrcDirsIfPossible(sourceDirectorySet: SourceDirectorySet): FileCollection {
        if (javaSourceSet == null)
            return sourceDirectorySet

        // If the API used below is not available, fall back to not filtering the Java sources.
        if (SourceDirectorySet::class.java.methods.none { it.name == "getSourceDirectories" }) {
            return sourceDirectorySet
        }

        fun getSourceDirectories(sourceDirectorySet: SourceDirectorySet): FileCollection {
            val method = SourceDirectorySet::class.java.getMethod("getSourceDirectories")
            return method(sourceDirectorySet) as FileCollection
        }

        // Build a lazily-resolved file collection that filters out Java sources from sources of this sourceDirectorySet
        return getSourceDirectories(sourceDirectorySet).minus(getSourceDirectories(javaSourceSet.java))
    }

    private fun createAdditionalClassesTaskForIdeRunner() {
        // Workaround: as per KT-26641, when there's a Kotlin compilation with a Java source set, we create another task
        // that has a name composed as '<IDE module name>Classes`, where the IDE module name is the default source set name:
        val expectedClassesTaskName = "${kotlinCompilation.defaultSourceSetName}Classes"
        project.tasks.run {
            if (findByName(expectedClassesTaskName) == null)
                create(expectedClassesTaskName) { task -> task.dependsOn(getByName(kotlinCompilation.compileAllTaskName)) }
        }
    }

    protected abstract fun doCreateTask(project: Project, taskName: String): T
}

