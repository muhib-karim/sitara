# Media3 and Compose ship their own consumer rules.

# NewPipeExtractor: Rhino runs YouTube's player JavaScript through reflection, and time-ago patterns are loaded by class name.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
# Desktop-only APIs referenced by Rhino and optional jsoup integrations; never reached on Android.
-dontwarn org.mozilla.javascript.tools.**
-dontwarn javax.script.**
-dontwarn java.beans.**
-dontwarn jdk.dynalink.**
-dontwarn com.google.re2j.**
