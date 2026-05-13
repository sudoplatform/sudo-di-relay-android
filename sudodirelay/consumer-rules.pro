# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html
#   https://www.guardsquare.com/manual/home

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Keep GraphQL generated code
-keep class com.sudoplatform.sudodirelay.graphql.** { *; }
-keep class com.sudoplatform.sudouser.graphql.** { *; }
-keep class com.amplifyframework.api.graphql.** { *; }

# Keep classes involved in JSON serialisation
-keep class com.fasterxml.jackson.core.** { *; }
-keep class com.fasterxml.jackson.databind.** { *; }
-keep class com.auth0.jwt.** { *; }

# Keep Kotlin metadata for classes that use reflection
-keep class kotlin.Metadata
