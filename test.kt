package org.jetbrains.kotlin.gradle.plugin

import com.android.build.gradle.BaseExtension
import com.android.build.gradle.BasePlugin
import com.android.builder.model.SourceProvider
import groovy.lang.Closure
import org.gradle.api.*
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

    protected abstract fun doCreateTask(project: Project, taskName: String): T
}

