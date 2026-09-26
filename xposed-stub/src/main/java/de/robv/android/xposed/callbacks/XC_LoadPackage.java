package de.robv.android.xposed.callbacks;

/** Compile-only stub. The real implementation is provided by LSPosed at runtime. */
public abstract class XC_LoadPackage extends XCallback {
    public static final class LoadPackageParam extends XCallback.Param {
        public String packageName;
        public String processName;
        public ClassLoader classLoader;
        public boolean isFirstApplication;
    }
}
