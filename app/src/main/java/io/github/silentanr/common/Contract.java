package io.github.silentanr.common;

/**
 * Constants shared between the system_server hook (runs inside the "android" process) and the
 * app UI. Kept in plain Java with no dependencies so both sides can use it.
 */
public final class Contract {
    private Contract() {
    }

    public static final String APP_PACKAGE = "io.github.silentanr";
    public static final String PREFS_NAME = "module_prefs";

    /** system_server -> app: an ANR happened. Explicit broadcast to {@link #RECEIVER_ANR}. */
    public static final String ACTION_ANR_EVENT = APP_PACKAGE + ".action.ANR_EVENT";
    public static final String RECEIVER_ANR = APP_PACKAGE + ".receiver.AnrEventReceiver";

    /** app -> system_server: control commands, guarded by {@link #PERMISSION_CONTROL}. */
    public static final String ACTION_CONTROL = APP_PACKAGE + ".action.CONTROL";
    public static final String PERMISSION_CONTROL = APP_PACKAGE + ".permission.CONTROL";

    // Control ops
    public static final String OP_PING = "ping";
    public static final String OP_KILL = "kill";
    public static final String OP_FORCE_STOP = "force_stop";
    public static final String OP_KILL_BACKGROUND = "kill_bg";
    public static final String OP_KILL_ALL_BACKGROUND = "kill_all_bg";
    public static final String OP_PROCESSES = "procs";

    public static final String EXTRA_OP = "op";
    public static final String EXTRA_PACKAGE = "pkg";
    public static final String EXTRA_PROCESS = "process";
    public static final String EXTRA_PID = "pid";
    public static final String EXTRA_UID = "uid";
    public static final String EXTRA_USER_ID = "user_id";
    public static final String EXTRA_REASON = "reason";
    public static final String EXTRA_DETAILS = "details";
    public static final String EXTRA_ACTION = "action";
    public static final String EXTRA_TIMESTAMP = "ts";
    public static final String EXTRA_CONTINUOUS = "continuous";
    public static final String EXTRA_ABOVE_SYSTEM = "above_system";
    public static final String EXTRA_NOTIFY = "notify";
    public static final String EXTRA_HOOK_VERSION = "hook_version";
    /** Result of the kill ops: how many processes actually went away. */
    public static final String EXTRA_COUNT = "count";

    // Process list result (parallel arrays in result extras)
    public static final String EXTRA_PROC_NAMES = "proc_names";
    public static final String EXTRA_PROC_PKGS = "proc_pkgs";
    public static final String EXTRA_PROC_PIDS = "proc_pids";
    public static final String EXTRA_PROC_UIDS = "proc_uids";
    public static final String EXTRA_PROC_PSS = "proc_pss";
    public static final String EXTRA_PROC_IMPORTANCE = "proc_importance";

    public static final int RESULT_OK = 1;
    public static final int HOOK_VERSION = 1;

    // Per-app handling mode. Stored as prefs key MODE_KEY_PREFIX + packageName.
    public static final String MODE_DEFAULT = "default";
    /** Hide the popup, keep waiting, and post a notification with "close app" action. */
    public static final String MODE_NOTIFY = "notify";
    /** Hide the popup and keep waiting silently. */
    public static final String MODE_WAIT = "wait";
    /** Hide the popup and close the app immediately. */
    public static final String MODE_KILL = "kill";
    /** Leave the stock system ANR dialog in place (still recorded). */
    public static final String MODE_DIALOG = "dialog";

    // Recorded action taken
    public static final String ACT_NOTIFIED = "NOTIFIED";
    public static final String ACT_AUTO_WAIT = "AUTO_WAIT";
    public static final String ACT_AUTO_KILL = "AUTO_KILL";
    public static final String ACT_DIALOG = "DIALOG";
    public static final String ACT_BACKGROUND_KILLED = "BG_KILLED";

    // Pref keys
    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_DEFAULT_MODE = "default_mode";
    public static final String KEY_NOTIFY_AUTO = "notify_auto";
    public static final String KEY_RECORD_BACKGROUND = "record_background";
    public static final String MODE_KEY_PREFIX = "mode_";
    /** Per-ANR-type mode. Stored as TYPE_KEY_PREFIX + type; an app rule still wins over it. */
    public static final String TYPE_KEY_PREFIX = "type_mode_";

    // ANR types, derived from the ANR annotation / short message.
    public static final String TYPE_INPUT = "input";
    public static final String TYPE_SERVICE = "service";
    public static final String TYPE_BROADCAST = "broadcast";
    public static final String TYPE_PROVIDER = "provider";
    public static final String TYPE_JOB = "job";
    public static final String TYPE_START = "start";
    public static final String TYPE_OTHER = "other";

    public static final String[] TYPES = {
            TYPE_INPUT, TYPE_SERVICE, TYPE_BROADCAST, TYPE_PROVIDER, TYPE_JOB, TYPE_START, TYPE_OTHER,
    };

    /** Classifies a raw ANR reason ("Input dispatching timed out ...") into one of {@link #TYPES}. */
    public static String typeOf(String reason) {
        if (reason == null) return TYPE_OTHER;
        String r = reason.toLowerCase(java.util.Locale.ROOT);
        if (r.contains("input dispatching") || r.contains("input event") || r.contains("keydispatching")) {
            return TYPE_INPUT;
        }
        if (r.contains("executing service") || r.contains("service.start") || r.contains("startforegroundservice")
                || r.contains("foreground service") || (r.contains("service") && r.contains("timeout"))) {
            return TYPE_SERVICE;
        }
        if (r.contains("broadcast")) return TYPE_BROADCAST;
        if (r.contains("contentprovider") || r.contains("content provider")) return TYPE_PROVIDER;
        if (r.contains("job")) return TYPE_JOB;
        if (r.contains("bind application") || r.contains("bindapplication")) return TYPE_START;
        return TYPE_OTHER;
    }
}
