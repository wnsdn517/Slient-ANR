# Xposed entry point is loaded reflectively by LSPosed via assets/xposed_init.
-keep class io.github.silentanr.xposed.** { *; }
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**

# Smaller dex: move everything not kept into one package and drop debug-only Kotlin checks.
-repackageclasses
-allowaccessmodification
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNull*(...);
    public static void checkParameterIsNotNull(...);
    public static void checkNotNullParameter(...);
    public static void checkExpressionValueIsNotNull(...);
    public static void checkNotNullExpressionValue(...);
    public static void checkReturnedValueIsNotNull(...);
    public static void checkFieldIsNotNull(...);
}
