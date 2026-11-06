import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.losnote"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.losnote"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests {
            // Robolectric 需要访问 res/manifest 资源
            isIncludeAndroidResources = true
            // 有本地预置的 robolectric jar 时优先用（HOME 只读的环境），否则走默认 ~/.m2
            val roboDeps = rootProject.layout.projectDirectory.dir(".toolchain/robolectric-deps").asFile
            if (roboDeps.isDirectory) {
                all {
                    it.systemProperty("robolectric.dependency.dir", roboDeps.absolutePath)
                }
            }
        }
    }
}

// 跨语言 E2E（真 SyncClient → 真 Node 服务）靠 Gradle 属性开关；没配属性时该用例自动 skip
tasks.withType<Test>().configureEach {
    providers.gradleProperty("losnote.e2e.server").orNull?.let { systemProperty("losnote.e2e.server", it) }
    providers.gradleProperty("losnote.e2e.token").orNull?.let { systemProperty("losnote.e2e.token", it) }
}

// 全局排除冲突的旧 annotations 包（prism4j 传递依赖引入，与 androidx 的 annotations 重复）
configurations.all {
    exclude(group = "org.jetbrains", module = "annotations-java5")
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.fragment)
    implementation(libs.markwon.core)
    implementation(libs.markwon.tables)
    implementation(libs.markwon.strikethrough)
    implementation(libs.markwon.syntax)
    implementation(libs.prism4j)
    annotationProcessor(libs.prism4j.bundler)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}