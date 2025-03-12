plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
val javaVersion = JavaVersion.VERSION_21
val packageName = "cn.lalaki.rtsp_android_example"
android {
    namespace = packageName
    compileSdk = 35
    defaultConfig {
        applicationId = packageName
        minSdk = 34
        targetSdk = 35
        versionCode = 3
        versionName = "3.0"
        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }
    signingConfigs {
        create("release") {
            storeFile = file("D:\\imoe.jks")
            keyAlias = System.getenv("MY_PRIVATE_EMAIL")
            storePassword = System.getenv("mystorepass")
            keyPassword = System.getenv("mystorepass2")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            isJniDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs["release"]
        }
    }
    compileOptions {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    ndkVersion = "29.0.13113456 rc1"
}
dependencies {
    //noinspection GradleDependency
    implementation("com.github.pedroSG94.RootEncoder:library:2.3.5")
    implementation("com.github.pedroSG94:RTSP-Server:1.2.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
}