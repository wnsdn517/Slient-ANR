package io.github.wnsdn517.silentanr.xposed;

import de.robv.android.xposed.XSharedPreferences;
import io.github.wnsdn517.silentanr.common.Contract;

/** Reads the module settings written by the app UI (LSPosed "xposedsharedprefs"). */
final class HookConfig {
    private XSharedPreferences prefs;

    private synchronized XSharedPreferences prefs() {
        if (prefs == null) {
            try {
                prefs = new XSharedPreferences(Contract.APP_PACKAGE, Contract.PREFS_NAME);
            } catch (Throwable t) {
                AnrHooker.log("XSharedPreferences unavailable: " + t);
                return null;
            }
        }
        try {
            if (prefs.hasFileChanged()) prefs.reload();
        } catch (Throwable ignored) {
        }
        return prefs;
    }

    boolean enabled() {
        XSharedPreferences p = prefs();
        return p == null || p.getBoolean(Contract.KEY_ENABLED, true);
    }

    boolean notifyAutoActions() {
        XSharedPreferences p = prefs();
        return p == null || p.getBoolean(Contract.KEY_NOTIFY_AUTO, true);
    }

    boolean recordBackground() {
        XSharedPreferences p = prefs();
        return p == null || p.getBoolean(Contract.KEY_RECORD_BACKGROUND, true);
    }

    /**
     * Resolves the effective mode, never returning {@link Contract#MODE_DEFAULT}.
     * Precedence: app rule, then ANR-type rule, then the global default.
     */
    String modeFor(String packageName, String reason) {
        XSharedPreferences p = prefs();
        String fallback = Contract.MODE_NOTIFY;
        if (p == null) return fallback;
        String global = p.getString(Contract.KEY_DEFAULT_MODE, fallback);
        if (global == null || Contract.MODE_DEFAULT.equals(global)) global = fallback;
        if (packageName != null) {
            String app = p.getString(Contract.MODE_KEY_PREFIX + packageName, Contract.MODE_DEFAULT);
            if (app != null && !Contract.MODE_DEFAULT.equals(app)) return app;
        }
        String type = p.getString(Contract.TYPE_KEY_PREFIX + Contract.typeOf(reason), Contract.MODE_DEFAULT);
        return type == null || Contract.MODE_DEFAULT.equals(type) ? global : type;
    }
}
