# Consumer ProGuard / R8 rules for the Traceback SDK

-keep class * implements com.inqbarna.traceback.sdk.TracebackConfigProvider { *; }
-keepclassmembers enum com.inqbarna.traceback.sdk.MatchType {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers enum com.inqbarna.traceback.sdk.ResolveSource {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
