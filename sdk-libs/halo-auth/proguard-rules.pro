-keepattributes Exceptions
-keepparameternames
-keepattributes EnclosingMethod

-keep class com.bluelinelabs.logansquare.** { *; }
-keep @com.bluelinelabs.logansquare.annotation.JsonObject class *
-keep class **$$JsonObjectMapper { *; }