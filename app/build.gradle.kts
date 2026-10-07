import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// 发布签名配置:凭据放 keystore.properties(已 gitignore),缺失时 release 保持未签名,
// 保证 CI / 其他开发者没有密钥也能正常构建。
val keystorePropsFile = rootProject.file("keystore.properties")
val hasReleaseSigning = keystorePropsFile.exists()
val keystoreProps = Properties().apply {
    if (hasReleaseSigning) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.armorlab.securedroid"
    compileSdk = 35

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    defaultConfig {
        applicationId = "com.armorlab.securedroid"
        minSdk = 26
        targetSdk = 35
        versionCode = 37
        versionName = "1.9.22"
        // 仅保留中文资源,release 剥离 androidx/material 的多语言表
        resourceConfigurations.addAll(listOf("zh", "zh-rCN"))
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        // 错误级问题(权限缺失 / API 误用 / 清单不一致等真实崩溃风险)阻断构建;
        // 警告级(硬编码文案、依赖更新提示等)保留在报告中不阻断。
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        // Robolectric 冒烟测试需要真实 Android 资源(布局 / 主题 / 清单)
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric 默认从 repo1.maven.org 拉取 android-all 运行库(约 150MB),
            // 受限网络下会静默挂起。这里指向可达镜像,并在本地已预置时走离线模式。
            it.systemProperty("robolectric.dependency.repo.id", "aliyun")
            it.systemProperty("robolectric.dependency.repo.url", "https://maven.aliyun.com/repository/public")
            val offlineDir = rootProject.file("robolectric-deps")
            if (offlineDir.isDirectory) {
                it.systemProperty("robolectric.offline", "true")
                it.systemProperty("robolectric.dependency.dir", offlineDir.absolutePath)
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // 本地单元测试(安全关键逻辑)
    testImplementation("junit:junit:4.13.2")
    // Robolectric:在 JVM 上真实启动 Application / 膨胀布局 / 拉起 Activity / 打开 Room
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
}
