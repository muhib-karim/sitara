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

# WorkManager creates its input mergers by reflection (Class.newInstance) before it runs any worker. Its own rule keeps
# the classes but, in R8's full mode, not their constructors, so every job failed with "Could not create Input Merger":
# Glance's session worker never ran and the home-screen widget stayed on its loading layout (0.9.6).
-keepclassmembers class * extends androidx.work.InputMerger { public <init>(); }
# Glance creates a widget button's ActionCallback the same way (Class.forName, then the no-argument constructor).
-keepclassmembers class * implements androidx.glance.appwidget.action.ActionCallback { public <init>(); }
