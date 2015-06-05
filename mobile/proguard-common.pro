################ chunk of config from $ANDROID_SDK/tools/proguard/proguard-android.txt

-keep public class com.google.vending.licensing.ILicensingService
-keep public class com.android.vending.licensing.ILicensingService

# For native methods, see http://proguard.sourceforge.net/manual/examples.html#native
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# keep setters in Views so that animations can still work.
# see http://proguard.sourceforge.net/manual/examples.html#beans
-keepclassmembers public class * extends android.view.View {
   void set*(***);
   *** get*();
}

# We want to keep methods in Activity that could be used in the XML attribute onClick
-keepclassmembers class * extends android.app.Activity {
   public void *(android.view.View);
}

# For enumeration classes, see http://proguard.sourceforge.net/manual/examples.html#enumerations
-keepclassmembers,allowoptimization enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

-keepclassmembers class **.R$* {
    public static <fields>;
}

# The support library contains references to newer platform versions.
# Don't warn about those in case this app is linking against an older
# platform version.  We know about them, and they are safe.
-dontwarn android.support.**

################ end of default file import

# fortunately this app does not have that crap... yet

# Some code may make further use of introspection to figure out the enclosing methods of anonymous inner classes.
# (see https://stuff.mit.edu/afs/sipb/project/android/sdk/android-sdk-linux/tools/proguard/docs/manual/examples.html)
#-keepattributes EnclosingMethod

# Allow runtime reflection on annotations (mainly for Jackson)
#-keepattributes *Annotation*

# wish I could have killed assholes, accessing _inner classes_ via reflection
#-keepattributes InnerClasses

# Need investigation (library obfuscation WTF?)
#-keepattributes Signature

-verbose

# Not needed on Android
-dontpreverify

# Making obfuscated stacktraces useful
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# Better safe th sorry
-dontskipnonpubliclibraryclassmembers
-dontskipnonpubliclibraryclasses

# Removing no-arg constructor would prevent framework from dynamically
# instantiating classes
-keepclassmembers class ** {
    !protected !private <init>();
}

# These don't really affect anything (except suppressing
# potentially important warning messages)

# virtually anything well-optimized
-dontwarn sun.misc.Unsafe

# CRest (lots of optional deps)
-dontwarn org.codegist.**

# Jackson (lots of optional deps)
-dontwarn org.codehaus.jackson.**

# RxJava
-dontwarn org.mockito.**
-dontwarn org.junit.**
-dontwarn org.robolectric.**

# TODO
-dontwarn rx.operators.OperatorConditionalBinding

# AndroidAnnotations (we don't need these parts)
-dontwarn org.androidannotations.api.rest.*

# OkHttp (those classes aren't present on Android)
-dontwarn com.squareup.picasso.OkHttpDownloader
-dontwarn java.nio.file.*
-dontwarn com.squareup.okhttp.internal.huc.*
-dontwarn com.squareup.okhttp.internal.http.*
-dontwarn org.codehaus.mojo.animal_sniffer.**

# Retrolambda, per https://github.com/evant/gradle-retrolambda
-dontwarn java.lang.invoke.*

# ButterKnife
-dontwarn butterknife.internal.**

# Dagger 1
-dontwarn dagger.internal.**

#-printusage unused.txt

# this file must be saved to decript stacktraces later
-printmapping mapping.txt

#-printconfiguration proguard-config.txt

#-obfuscationdictionary proguard-dict.txt
