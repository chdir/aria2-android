# Optimizations: Adding optimization introduces
# certain risks, since for example not all optimizations performed by
# ProGuard works on all versions of Dalvik.  The following flags turn
# off various optimizations known to have issues, but the list may not
# be complete or up to date. (The "arithmetic" optimization can be
# used if you are only targeting Android 2.0 or later.)  Make sure you
# test thoroughly if you go this route.
# handy for debugging

#-dontobfuscate - please, don't use this

#-dontoptimize - please, don't use this

# Note: inlining methods may allow for small number of codepoints
# without line numbers

# DO NOT USE - Proguard sucks at handling these:
#-keepattributes MethodParameters,LocalVariableTable,LocalVariableTypeTable

# code/simplification/cast - not recognized by verifier until 4.4
# field optimizations - VERY tricky to support, too much side-effects
# code/allocation/variable - can make parameter lists unsuitable for reflection ?!
# class merging - turns stack traces into crap
# interface merging - not supported
# see also http://osdir.com/ml/AndroidDevelopers/2009-07/msg03545.html
-optimizations !code/simplification/cast,!field/*,!class/merging/*,!code/allocation/variable

-optimizationpasses 5

-repackageclasses aria2

-allowaccessmodification

# no longer strictly necessary since 2.0 (see also https://code.google.com/p/android/issues/detail?id=2422)
# but makes stacktraces slightly better
-useuniqueclassmembernames

-adaptresourcefilenames

-keep class * extends java.lang.Exception { *; }
# Obfuscate our own classes. Don't shrink them: lots of codegen make it troublesome,
# and we don't have much unused code in our repository anyway
-keep,allowobfuscation,allowoptimization class net.sf.aria2.**,rx.**,dagger.** { *; }
-keep,allowobfuscation,allowoptimization interface net.sf.aria2.**,rx.**,dagger.** { *; }
-keep,allowobfuscation,allowoptimization enum net.sf.aria2.**,rx.**,dagger.** { *; }

# Do not touch anything else!
-keep class !org.jraf.android.backport.**,!com.afollestad.materialdialogs.**,!rx.**,!org.androidannotations.**,!com.squareup.picasso.**,!org.codegist.**,!com.googlecode.libphonenumber.**,!dagger.**,!net.sf.aria2.**,!android.support.**,!com.android.support.**,!com.google.android.**,** { *; }

# Google Services (support has full Proguard config in aar)
# http://developer.android.com/google/play-services/setup.html#Proguard
# http://developer.fyber.com/content/android/basics/getting-started-sdk/
# and others
-keep class * extends java.util.ListResourceBundle {
    protected java.lang.Object[][] getContents();
}

-keep public class com.google.android.gms.common.internal.safeparcel.SafeParcelable {
    public static final *** NULL;
}

-keepnames @com.google.android.gms.common.annotation.KeepName class *
-keepclassmembernames class * {
    @com.google.android.gms.common.annotation.KeepName *;
}

-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
    public static ** CREATOR;
    public static final android.os.Parcelable$Creator *;
    public static android.os.Parcelable$Creator *;
}

-keepnames class * implements android.os.Parcelable
-keepnames class * implements java.lang.Exception

-keepclassmembers class * implements java.io.Serializable {
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Add additional guards for any serializable objects, that may be written to disk!!!

-keep class com.google.android.gms.ads.identifier.AdvertisingIdClient** { *; }

# CRest
-keepclassmembers class org.codegist.** {
    <init>(...);
}
-keepclassmembers @org.codegist.crest.annotate.CRestComponent class * {
    <init>();
    <init>(org.codegist.crest.CRestConfig);
}
-keepclassmembers class * implements @org.codegist.crest.annotate.CRestComponent * {
    <init>();
    <init>(org.codegist.crest.CRestConfig);
}
-keepclassmembers class * extends org.codegist.common.log.Logger {
    <init>();
    <init>(java.lang.String);
}

# Some stuff must not be renamed, because it is accessed in runtime
# via reflection or explicit class loading

# Jackson
-keepclassmembers class * {
    @org.codehaus.jackson.annotate.* <fields>;
    @org.codehaus.jackson.annotate.* <init>(...);
}

# TODO why is this needed?
-keepnames class org.codehaus.jackson.** { *; }

# Butterknife :/
# Do not rename injectable classes and do not touch corresponding injectors
-keep class **$$ViewInjector { *; }
-keepclasseswithmembers class * {
    @butterknife.InjectView <fields>;
}
-keepclasseswithmembers class * {
    @butterknife.OnClick <methods>;
}
-keepclasseswithmembers class * {
    @butterknife.OnEditorAction <methods>;
}
-keepclasseswithmembers class * {
    @butterknife.OnItemClick <methods>;
}
-keepclasseswithmembers class * {
    @butterknife.OnItemLongClick <methods>;
}
-keepclasseswithmembers class * {
    @butterknife.OnLongClick <methods>;
}

# Keep declared Javascript interfaces
-keepclasseswithmembers class ** {
    @android.webkit.JavascriptInterface *;
}

# Dagger 1 :(
# Keep the fields annotated with @Inject
# and preserve their names and names of classes, that contain them
-keepclasseswithmembers,includedescriptorclasses class * {
    @javax.inject.* <fields>;
}
-keepclasseswithmembers,includedescriptorclasses class * {
    @javax.inject.* <init>(...);
}

# Wizardroid
-keepclasseswithmembers class * { @org.codepond.wizardroid.persistence.ContextVariable *;}

# Remove - System method calls. Remove all invocations of System
# methods without side effects whose return values are not used.
-assumenosideeffects public class java.lang.System {
    public static long currentTimeMillis();
    static java.lang.Class getCallerClass();
    public static int identityHashCode(java.lang.Object);
    public static java.lang.SecurityManager getSecurityManager();
    public static java.util.Properties getProperties();
    public static java.lang.String getProperty(java.lang.String);
    public static java.lang.String getenv(java.lang.String);
    public static java.lang.String mapLibraryName(java.lang.String);
    public static java.lang.String getProperty(java.lang.String,java.lang.String);
}

# Remove - Math method calls. Remove all invocations of Math
# methods without side effects whose return values are not used.
-assumenosideeffects public class java.lang.Math {
    public static double sin(double);
    public static double cos(double);
    public static double tan(double);
    public static double asin(double);
    public static double acos(double);
    public static double atan(double);
    public static double toRadians(double);
    public static double toDegrees(double);
    public static double exp(double);
    public static double log(double);
    public static double log10(double);
    public static double sqrt(double);
    public static double cbrt(double);
    public static double IEEEremainder(double,double);
    public static double ceil(double);
    public static double floor(double);
    public static double rint(double);
    public static double atan2(double,double);
    public static double pow(double,double);
    public static int round(float);
    public static long round(double);
    public static double random();
    public static int abs(int);
    public static long abs(long);
    public static float abs(float);
    public static double abs(double);
    public static int max(int,int);
    public static long max(long,long);
    public static float max(float,float);
    public static double max(double,double);
    public static int min(int,int);
    public static long min(long,long);
    public static float min(float,float);
    public static double min(double,double);
    public static double ulp(double);
    public static float ulp(float);
    public static double signum(double);
    public static float signum(float);
    public static double sinh(double);
    public static double cosh(double);
    public static double tanh(double);
    public static double hypot(double,double);
    public static double expm1(double);
    public static double log1p(double);
}

# Remove - Number method calls. Remove all invocations of Number
# methods without side effects whose return values are not used.
-assumenosideeffects public class java.lang.* extends java.lang.Number {
    public static java.lang.String toString(byte);
    public static java.lang.Byte valueOf(byte);
    public static byte parseByte(java.lang.String);
    public static byte parseByte(java.lang.String,int);
    public static java.lang.Byte valueOf(java.lang.String,int);
    public static java.lang.Byte valueOf(java.lang.String);
    public static java.lang.Byte decode(java.lang.String);
    public int compareTo(java.lang.Byte);
    public static java.lang.String toString(short);
    public static short parseShort(java.lang.String);
    public static short parseShort(java.lang.String,int);
    public static java.lang.Short valueOf(java.lang.String,int);
    public static java.lang.Short valueOf(java.lang.String);
    public static java.lang.Short valueOf(short);
    public static java.lang.Short decode(java.lang.String);
    public static short reverseBytes(short);
    public int compareTo(java.lang.Short);
    public static java.lang.String toString(int,int);
    public static java.lang.String toHexString(int);
    public static java.lang.String toOctalString(int);
    public static java.lang.String toBinaryString(int);
    public static java.lang.String toString(int);
    public static int parseInt(java.lang.String,int);
    public static int parseInt(java.lang.String);
    public static java.lang.Integer valueOf(java.lang.String,int);
    public static java.lang.Integer valueOf(java.lang.String);
    public static java.lang.Integer valueOf(int);
    public static java.lang.Integer getInteger(java.lang.String);
    public static java.lang.Integer getInteger(java.lang.String,int);
    public static java.lang.Integer getInteger(java.lang.String,java.lang.Integer);
    public static java.lang.Integer decode(java.lang.String);
    public static int highestOneBit(int);
    public static int lowestOneBit(int);
    public static int numberOfLeadingZeros(int);
    public static int numberOfTrailingZeros(int);
    public static int bitCount(int);
    public static int rotateLeft(int,int);
    public static int rotateRight(int,int);
    public static int reverse(int);
    public static int signum(int);
    public static int reverseBytes(int);
    public int compareTo(java.lang.Integer);
    public static java.lang.String toString(long,int);
    public static java.lang.String toHexString(long);
    public static java.lang.String toOctalString(long);
    public static java.lang.String toBinaryString(long);
    public static java.lang.String toString(long);
    public static long parseLong(java.lang.String,int);
    public static long parseLong(java.lang.String);
    public static java.lang.Long valueOf(java.lang.String,int);
    public static java.lang.Long valueOf(java.lang.String);
    public static java.lang.Long valueOf(long);
    public static java.lang.Long decode(java.lang.String);
    public static java.lang.Long getLong(java.lang.String);
    public static java.lang.Long getLong(java.lang.String,long);
    public static java.lang.Long getLong(java.lang.String,java.lang.Long);
    public static long highestOneBit(long);
    public static long lowestOneBit(long);
    public static int numberOfLeadingZeros(long);
    public static int numberOfTrailingZeros(long);
    public static int bitCount(long);
    public static long rotateLeft(long,int);
    public static long rotateRight(long,int);
    public static long reverse(long);
    public static int signum(long);
    public static long reverseBytes(long);
    public int compareTo(java.lang.Long);
    public static java.lang.String toString(float);
    public static java.lang.String toHexString(float);
    public static java.lang.Float valueOf(java.lang.String);
    public static java.lang.Float valueOf(float);
    public static float parseFloat(java.lang.String);
    public static boolean isNaN(float);
    public static boolean isInfinite(float);
    public static int floatToIntBits(float);
    public static int floatToRawIntBits(float);
    public static float intBitsToFloat(int);
    public static int compare(float,float);
    public boolean isNaN();
    public boolean isInfinite();
    public int compareTo(java.lang.Float);
    public static java.lang.String toString(double);
    public static java.lang.String toHexString(double);
    public static java.lang.Double valueOf(java.lang.String);
    public static java.lang.Double valueOf(double);
    public static double parseDouble(java.lang.String);
    public static boolean isNaN(double);
    public static boolean isInfinite(double);
    public static long doubleToLongBits(double);
    public static long doubleToRawLongBits(double);
    public static double longBitsToDouble(long);
    public static int compare(double,double);
    public boolean isNaN();
    public boolean isInfinite();
    public int compareTo(java.lang.Double);
    public <init>(byte);
    public <init>(short);
    public <init>(int);
    public <init>(long);
    public <init>(float);
    public <init>(double);
    public <init>(java.lang.String);
    public byte byteValue();
    public short shortValue();
    public int intValue();
    public long longValue();
    public float floatValue();
    public double doubleValue();
    public int compareTo(java.lang.Object);
    public boolean equals(java.lang.Object);
    public int hashCode();
    public java.lang.String toString();
}

# Remove - String method calls. Remove all invocations of String
# methods without side effects whose return values are not used.
-assumenosideeffects public class java.lang.String {
    public <init>();
    public <init>(byte);
    public <init>(byte,int);
    public <init>(byte,int,int);
    public <init>(byte,int,int,int);
    public <init>(byte,int,int,java.lang.String);
    public <init>(byte,java.lang.String);
    public <init>(char);
    public <init>(char,int,int);
    public <init>(java.lang.String);
    public <init>(java.lang.StringBuffer);
    public static java.lang.String copyValueOf(char);
    public static java.lang.String copyValueOf(char,int,int);
    public static java.lang.String valueOf(boolean);
    public static java.lang.String valueOf(char);
    public static java.lang.String valueOf(char);
    public static java.lang.String valueOf(char,int,int);
    public static java.lang.String valueOf(double);
    public static java.lang.String valueOf(float);
    public static java.lang.String valueOf(int);
    public static java.lang.String valueOf(java.lang.Object);
    public static java.lang.String valueOf(long);
    public boolean contentEquals(java.lang.StringBuffer);
    public boolean endsWith(java.lang.String);
    public boolean equalsIgnoreCase(java.lang.String);
    public boolean equals(java.lang.Object);
    public boolean matches(java.lang.String);
    public boolean regionMatches(boolean,int,java.lang.String,int,int);
    public boolean regionMatches(int,java.lang.String,int,int);
    public boolean startsWith(java.lang.String);
    public boolean startsWith(java.lang.String,int);
    public byte getBytes();
    public byte getBytes(java.lang.String);
    public char charAt(int);
    public char toCharArray();
    public int compareToIgnoreCase(java.lang.String);
    public int compareTo(java.lang.Object);
    public int compareTo(java.lang.String);
    public int hashCode();
    public int indexOf(int);
    public int indexOf(int,int);
    public int indexOf(java.lang.String);
    public int indexOf(java.lang.String,int);
    public int lastIndexOf(int);
    public int lastIndexOf(int,int);
    public int lastIndexOf(java.lang.String);
    public int lastIndexOf(java.lang.String,int);
    public int length();
    public java.lang.CharSequence subSequence(int,int);
    public java.lang.String concat(java.lang.String);
    public java.lang.String replaceAll(java.lang.String,java.lang.String);
    public java.lang.String replace(char,char);
    public java.lang.String replaceFirst(java.lang.String,java.lang.String);
    public java.lang.String split(java.lang.String);
    public java.lang.String split(java.lang.String,int);
    public java.lang.String substring(int);
    public java.lang.String substring(int,int);
    public java.lang.String toLowerCase();
    public java.lang.String toLowerCase(java.util.Locale);
    public java.lang.String toString();
    public java.lang.String toUpperCase();
    public java.lang.String toUpperCase(java.util.Locale);
    public java.lang.String trim();
}

# Remove - StringBuffer method calls. Remove all invocations of StringBuffer
# methods without side effects whose return values are not used.
-assumenosideeffects public class java.lang.StringBuffer {
    public <init>();
    public <init>(int);
    public <init>(java.lang.String);
    public <init>(java.lang.CharSequence);
    public java.lang.String toString();
    public char charAt(int);
    public int capacity();
    public int codePointAt(int);
    public int codePointBefore(int);
    public int indexOf(java.lang.String,int);
    public int lastIndexOf(java.lang.String);
    public int lastIndexOf(java.lang.String,int);
    public int length();
    public java.lang.String substring(int);
    public java.lang.String substring(int,int);
}
# Remove - StringBuilder method calls. Remove all invocations of StringBuilder
# methods without side effects whose return values are not used.
-assumenosideeffects public class java.lang.StringBuilder {
    public <init>();
    public <init>(int);
    public <init>(java.lang.String);
    public <init>(java.lang.CharSequence);
    public java.lang.String toString();
    public char charAt(int);
    public int capacity();
    public int codePointAt(int);
    public int codePointBefore(int);
    public int indexOf(java.lang.String,int);
    public int lastIndexOf(java.lang.String);
    public int lastIndexOf(java.lang.String,int);
    public int length();
    public java.lang.String substring(int);
    public java.lang.String substring(int,int);
}
