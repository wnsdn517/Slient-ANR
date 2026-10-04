package io.github.wnsdn517.silentanr.xposed;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** LSPosed entry point (see assets/xposed_init). Scope: "android" (System Framework). */
public class XposedEntry implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if ("android".equals(lpparam.packageName) && "android".equals(lpparam.processName)) {
            AnrHooker.init(lpparam.classLoader);
        }
    }
}
