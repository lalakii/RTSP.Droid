plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}
val javaVersion = JavaVersion.VERSION_21
val packageName = "cn.lalaki.rtsp_android_example"
android {
    namespace = packageName
    compileSdk = 35
    defaultConfig {
        applicationId = packageName
        minSdk = 33
        targetSdk = 35
        versionCode = 9
        versionName = "9.0"
    }
    signingConfigs {
        create("release") {
            storeFile = file("D:\\imoe.jks")
            keyAlias = System.getenv("MY_PRIVATE_EMAIL")
            keyPassword = System.getenv("mystorepass2")
            storePassword = System.getenv("mystorepass")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            isJniDebuggable = false
            renderscriptOptimLevel = 3
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
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        dataBinding = true
    }
}
dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("info.hoang8f:fbutton:1.0.5")
    implementation("com.github.zcweng:switch-button:0.0.3@aar")
    implementation("com.github.homayoonahmadi:GroupBoxLayout:1.2.0")
    //noinspection GradleDependency
    implementation("com.github.pedroSG94.RootEncoder:library:2.3.5")
    //noinspection GradleDependency
    implementation("com.github.pedroSG94:RTSP-Server:1.2.1")
}