plugins {
    id("com.android.application")
}
val androidConfig = rootProject.extra["androidConfig"] as Map<*, *>
val javaVersion = rootProject.extra["javaVersion"] as JavaVersion
android {
    namespace = androidConfig["applicationId"].toString()
    compileSdk = androidConfig["compileSdkVersion"] as Int

    defaultConfig {
        applicationId = androidConfig["applicationId"].toString()
        minSdk = androidConfig["minSdkVersion"] as Int
        targetSdk = androidConfig["targetSdkVersion"] as Int
        versionCode = 10410
        versionName = "1.4.1"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        getByName("release") {

            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion

    }
    packaging{
        resources.excludes.add("META-INF/COPYRIGHT")
        resources.excludes.add("META-INF/LICENSE-notice.md")
        resources.excludes.add("META-INF/LICENSE.md")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = false
    }
    ndkVersion = "29.0.13599879"
    buildToolsVersion = "36.0.0"

    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "${getAppName()}_${buildType.name}_v${versionName}.apk"
        }
    }
    
}

fun getAppName(): String {
    val stringsFile = file("src/main/res/values/strings.xml")
    val parsedXml = org.xmlpull.v1.XmlPullParserFactory.newInstance().newPullParser().apply {
        setInput(stringsFile.reader())
    }

    var eventType = parsedXml.eventType
    var appName = ""
    while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
        if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG &&
            parsedXml.name == "string" &&
            parsedXml.getAttributeValue(null, "name") == "app_name"
        ) {
            appName = parsedXml.nextText()
            break
        }
        eventType = parsedXml.next()
    }
    return appName.replace(Regex("[^a-zA-Z0-9]"), "_")
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    // AndroidX 核心库
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.databinding:databinding-runtime:8.12.0")


    // AndroidX 扩展组件
    implementation("androidx.preference:preference:1.2.1")          // 设置偏好
    implementation("com.takisoft.preferencex:preferencex:1.1.0")    // 增强版Preference
    //noinspection GradleDependency
    implementation("androidx.work:work-runtime:2.10.1")              // WorkManager后台任务
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.9.2")  // MVVM架构支持
    implementation("androidx.lifecycle:lifecycle-livedata:2.9.2")

    // Material Design 组件
    implementation("com.google.android.material:material:1.12.0")

    // 响应式编程
    implementation("io.reactivex.rxjava3:rxjava:3.1.11")

    // 网络请求相关
    implementation("com.squareup.okhttp3:okhttp:5.1.0")
    implementation("com.squareup.okio:okio:3.16.0")

    // 数据解析与序列化
    implementation("com.google.code.gson:gson:2.13.1")                // JSON解析
    implementation("org.ini4j:ini4j:0.5.4")                           // INI文件解析

    // 工具库
    implementation("com.google.guava:guava:33.4.8-android")         // Google工具集合
    implementation("commons-logging:commons-logging:1.3.5")           // 通用日志接口
    implementation("com.jakewharton.timber:timber:5.0.1")            // 日志工具

    // UI组件扩展
    implementation("com.yanzhenjie.recyclerview:x:1.3.2")            // 增强版RecyclerView
    implementation("androidx.fragment:fragment:1.8.8")

    // 翻译服务
    implementation("app.nekogram.translator:translator:1.5.1")


    // AI服务集成
    implementation("io.github.lambdua:service:0.22.92")

    testImplementation("junit:junit:4.13.2")
}