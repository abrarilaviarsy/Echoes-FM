# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep data models used for JSON serialization/deserialization and Room DB
-keep class com.example.data.model.** { *; }

# Keep Room database and DAOs
-keep class com.example.data.db.** { *; }

# Keep Retrofit interfaces
-keep class com.example.data.api.** { *; }

# Keep any auto-generated files or Moshi adapters if needed
-keep class *JsonAdapter { *; }

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
