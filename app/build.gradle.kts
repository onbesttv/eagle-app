plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 21
        targetSdk = 35
        versionCode = 70
        versionName = "7.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "reseller"
    productFlavors {
        create("master") {
            dimension = "reseller"
            applicationId = "com.example.myapplication"
            resValue("string", "app_name", "Eagle")
            resValue("string", "reseller_id", "master")
        }

        create("eagle_tv") {
            dimension = "reseller"
            applicationId = "com.eagle.tv"
            versionCode = 83
            versionName = "3.13"
            resValue("string", "app_name", "Eagle TV")
            resValue("string", "reseller_id", "eagle_tv")
        }

        create("dream") {
            dimension = "reseller"
            applicationId = "com.redon.tv"
            versionCode = 85
            versionName = "3.15"
            resValue("string", "app_name", "RedOn")
            resValue("string", "reseller_id", "dream")
        }

        create("tv_fabio") {
            dimension = "reseller"
            applicationId = "com.eagle.tv_fabio"
            resValue("string", "app_name", "Fpinto TV")
            resValue("string", "reseller_id", "tv_fabio")
        }

        create("pereira_tv") {
            dimension = "reseller"
            applicationId = "com.eagle.pereira_tv"
            versionCode = 54
            versionName = "5.9"
            resValue("string", "app_name", "PereiraTV")
            resValue("string", "reseller_id", "pereira_tv")
        }

        create("godinho_tv") {
            dimension = "reseller"
            applicationId = "com.eagle.godinho_tv"
            versionCode = 60
            versionName = "6.4"
            resValue("string", "app_name", "GodinhoTV")
            resValue("string", "reseller_id", "godinho_tv")
        }

        create("tv_joker") {
            dimension = "reseller"
            applicationId = "com.eagle.tv_joker"
            versionCode = 24
            versionName = "3.3"
            resValue("string", "app_name", "TV do Joker")
            resValue("string", "reseller_id", "joker")
        }
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
    buildFeatures {
        resValues = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    // RecyclerView & CardView
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")

    // Media3 (ExoPlayer + UI + OkHttp)
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.4.1")

    // Imagens (Glide)
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // Rede & JSON
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Coroutines & Lifecycle
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    // WireGuard Tunnel
    implementation("com.wireguard.android:tunnel:1.0.20230706")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
