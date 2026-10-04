package io.github.wnsdn517.silentanr.xposed;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.UserHandle;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import io.github.wnsdn517.silentanr.common.Contract;

/**
 * Hooks the ANR pipeline inside system_server.
 *
 * <pre>
 * InputDispatcher / ActiveServices / BroadcastQueue timeout
 *   -> AnrHelper -> ProcessErrorStateRecord.appNotResponding()   (A12+; ProcessRecord on A10/11)
 *        - background ("silent") ANR: process is killed right here, no dialog
 *        - otherwise: setNotResponding(true) + SHOW_NOT_RESPONDING_UI_MSG
 *   -> AppErrors.handleShowAnrUi(Message{obj = AppNotRespondingDialog.Data})
 *        - shows AppNotRespondingDialog ("Close app" / "Wait")
 * </pre>
 *
 * We replace handleShowAnrUi according to the per-app mode and reproduce what the dialog
 * buttons do ourselves: "Wait" = clear the not-responding state and re-arm service timeouts,
 * "Close app" = ActivityManagerService.killAppAtUsersRequest(proc).
 */
public final class AnrHooker {
    private static final String TAG = "SilentANR: ";
    private static final int MAX_DETAILS = 16 * 1024;

    private static final HookConfig config = new HookConfig();
    private static final AtomicBoolean receiverRegistered = new AtomicBoolean();
    /** pid -> ProcessRecord of ANRs that were hidden and are still waiting for a user decision. */
    private static final Map<Integer, WeakReference<Object>> pending = new ConcurrentHashMap<>();
    /** pid -> ANR annotation captured in appNotResponding, consumed by handleShowAnrUi. */
    private static final Map<Integer, String> annotations = new ConcurrentHashMap<>();
    private static final ThreadLocal<Object[]> anrCallState = new ThreadLocal<>();

    private static volatile Context systemContext;
    private static volatile Object ams;
    private static Handler controlHandler;

    private AnrHooker() {
    }

    static void log(String msg) {
        try {
            XposedBridge.log(TAG + msg);
        } catch (Throwable ignored) {
        }
    }

    static void init(ClassLoader cl) {
        log("init, sdk=" + Build.VERSION.SDK_INT);
        hookShowAnrUi(cl);
        hookAppNotResponding(cl, "com.android.server.am.ProcessErrorStateRecord");
        hookAppNotResponding(cl, "com.android.server.am.ProcessRecord");
        hookSystemReady(cl);
    }

    // ---------------------------------------------------------------------------------------
    // Hooks
    // ---------------------------------------------------------------------------------------

    private static void hookShowAnrUi(ClassLoader cl) {
        Class<?> appErrors = XposedHelpers.findClassIfExists("com.android.server.am.AppErrors", cl);
        if (appErrors == null) {
            log("AppErrors not found; ANR popup blocking unavailable");
            return;
        }
        int n = XposedBridge.hookAllMethods(appErrors, "handleShowAnrUi", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    onShowAnrUi(param);
                } catch (Throwable t) {
                    log("handleShowAnrUi hook error: " + t);
                }
            }
        }).size();
        log("hooked AppErrors.handleShowAnrUi x" + n);
    }

    private static void hookAppNotResponding(ClassLoader cl, String className) {
        Class<?> cls = XposedHelpers.findClassIfExists(className, cl);
        if (cls == null) return;
        if (Reflect.findMethod(cls, "appNotResponding", -1) == null) return;
        int n = XposedBridge.hookAllMethods(cls, "appNotResponding", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    Object proc = processOf(param.thisObject);
                    int pid = pidOf(proc);
                    String annotation = annotationOf(param.args);
                    if (pid > 0 && annotation != null) annotations.put(pid, annotation);
                    anrCallState.set(new Object[]{proc, pid, isKilled(proc), annotation});
                } catch (Throwable t) {
                    anrCallState.remove();
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object[] state = anrCallState.get();
                anrCallState.remove();
                if (state == null) return;
                try {
                    onAppNotRespondingDone(state[0], (Integer) state[1], (Boolean) state[2], (String) state[3]);
                } catch (Throwable t) {
                    log("appNotResponding hook error: " + t);
                }
            }
        }).size();
        log("hooked " + className + ".appNotResponding x" + n);
    }

    private static void hookSystemReady(ClassLoader cl) {
        Class<?> amsClass = XposedHelpers.findClassIfExists("com.android.server.am.ActivityManagerService", cl);
        if (amsClass == null) return;
        XposedBridge.hookAllMethods(amsClass, "systemReady", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    attach(param.thisObject, (Context) Reflect.getField(param.thisObject, "mContext"));
                } catch (Throwable t) {
                    log("systemReady hook error: " + t);
                }
            }
        });
    }

    // ---------------------------------------------------------------------------------------
    // ANR handling
    // ---------------------------------------------------------------------------------------

    private static void onShowAnrUi(XC_MethodHook.MethodHookParam param) {
        Object appErrors = param.thisObject;
        attach(Reflect.getField(appErrors, "mService"), (Context) Reflect.getField(appErrors, "mContext"));

        if (param.args.length == 0 || !(param.args[0] instanceof Message)) return;
        Object data = ((Message) param.args[0]).obj;
        Object proc = Reflect.getField(data, "proc");
        if (proc == null) return;

        // Already showing a dialog for this process: let the original method log & bail out.
        if (hasAnrDialog(proc)) return;

        String pkg = packageOf(proc);
        int pid = pidOf(proc);
        String mode = config.enabled() ? config.modeFor(pkg, reasonOf(proc, pid)) : Contract.MODE_DIALOG;
        boolean continuous = Reflect.getBoolean(data, false, "isContinuousAnr");
        boolean aboveSystem = Reflect.getBoolean(data, false, "aboveSystem");

        String action;
        boolean notify;
        switch (mode) {
            case Contract.MODE_DIALOG:
                action = Contract.ACT_DIALOG;
                notify = false;
                break;
            case Contract.MODE_KILL:
                param.setResult(null);
                killAtUserRequest(proc);
                action = Contract.ACT_AUTO_KILL;
                notify = config.notifyAutoActions();
                break;
            case Contract.MODE_WAIT:
                param.setResult(null);
                keepWaiting(proc);
                action = Contract.ACT_AUTO_WAIT;
                notify = config.notifyAutoActions();
                break;
            case Contract.MODE_NOTIFY:
            default:
                param.setResult(null);
                keepWaiting(proc);
                if (pid > 0) pending.put(pid, new WeakReference<>(proc));
                action = Contract.ACT_NOTIFIED;
                notify = true;
                break;
        }
        report(proc, pkg, pid, action, notify, continuous, aboveSystem, annotations.remove(pid));
    }

    /** Records ANRs that never reach the dialog stage (background/silent ANRs killed by AMS). */
    private static void onAppNotRespondingDone(Object proc, int pid, boolean wasKilled, String annotation) {
        if (proc == null || wasKilled) return;
        if (isNotResponding(proc)) return; // dialog path: handled in handleShowAnrUi
        if (!isKilled(proc)) return;       // skipped (shutdown, duplicate, crashing, ...)
        annotations.remove(pid);
        if (!config.recordBackground()) return;
        report(proc, packageOf(proc), pid, Contract.ACT_BACKGROUND_KILLED, false, false, false, annotation);
    }

    /** Same as pressing "Wait" on AppNotRespondingDialog. */
    private static void keepWaiting(Object proc) {
        Object service = ams;
        Object procLock = Reflect.getField(service, "mProcLock");
        Object outer = service != null ? service : proc;
        Object inner = procLock != null ? procLock : outer;
        synchronized (outer) {
            synchronized (inner) {
                Object errState = Reflect.getField(proc, "mErrorState");
                Object target = errState != null ? errState : proc;
                if (Reflect.call(target, "setNotResponding", false) == Reflect.FAILED) {
                    Reflect.setField(target, "notResponding", false);
                    Reflect.setField(target, "mNotResponding", false);
                }
                Object controller = Reflect.callOrNull(target, "getDialogController");
                if (controller == null) controller = Reflect.getField(target, "mDialogController");
                if (controller != null) {
                    Reflect.call(controller, "clearAnrDialogs");
                } else {
                    Reflect.setField(proc, "anrDialog", null); // Android 10
                }
            }
            Object services = Reflect.getField(service, "mServices");
            if (services != null) Reflect.call(services, "scheduleServiceTimeoutLocked", proc);
        }
    }

    /** Same as pressing "Close app" on AppNotRespondingDialog. */
    private static void killAtUserRequest(Object proc) {
        Object service = ams;
        if (service == null) return;
        Method m = Reflect.findMethod(service.getClass(), "killAppAtUsersRequest", -1);
        if (m == null) {
            forceStop(packageOf(proc), userIdOf(proc));
            return;
        }
        Object[] args = new Object[m.getParameterTypes().length];
        args[0] = proc; // Android 10 has a second (Dialog fromDialog) parameter; null is fine.
        long token = Binder.clearCallingIdentity();
        try {
            m.invoke(service, args);
        } catch (Throwable t) {
            log("killAppAtUsersRequest failed: " + t);
            forceStop(packageOf(proc), userIdOf(proc));
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Force-stops a package and returns how many of its processes were running.
     *
     * forceStopPackage leaves persistent processes (System UI, Phone, ...) alone, so on its own it
     * silently did nothing for them. The ANR "Close app" path kills the process regardless and
     * Android restarts persistent ones; do the same for whatever survived.
     */
    private static int stopPackage(String pkg, int userId) {
        if (pkg == null) return 0;
        int before = pidsOf(pkg, userId).size();
        forceStop(pkg, userId);
        for (int pid : pidsOf(pkg, userId)) {
            android.os.Process.killProcess(pid);
        }
        return before;
    }

    /** Pids of running processes that host {@code pkg} for {@code userId}; null/-1 match all. */
    private static java.util.List<Integer> pidsOf(String pkg, int userId) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        if (systemContext == null) return out;
        List<ActivityManager.RunningAppProcessInfo> procs =
                systemContext.getSystemService(ActivityManager.class).getRunningAppProcesses();
        if (procs == null) return out;
        for (ActivityManager.RunningAppProcessInfo p : procs) {
            if (userId >= 0 && p.uid / 100_000 != userId) continue;
            if (pkg != null && (p.pkgList == null || !java.util.Arrays.asList(p.pkgList).contains(pkg))) continue;
            out.add(p.pid);
        }
        return out;
    }

    /** Lets AMS finish removing killed process records before they are counted again. */
    private static final long SETTLE_MS = 300;

    private static Bundle count(int n) {
        Bundle b = new Bundle();
        b.putInt(Contract.EXTRA_COUNT, Math.max(n, 0));
        return b;
    }

    private static void forceStop(String pkg, int userId) {
        if (pkg == null || ams == null) return;
        long token = Binder.clearCallingIdentity();
        try {
            if (Reflect.call(ams, "forceStopPackage", pkg, userId) == Reflect.FAILED) {
                log("forceStopPackage failed for " + pkg);
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Reporting to the app
    // ---------------------------------------------------------------------------------------

    /** Same reason {@link #report} will record, so type rules match what the log shows. */
    private static String reasonOf(Object proc, int pid) {
        String reason = pid > 0 ? annotations.get(pid) : null;
        if (reason != null) return reason;
        Object report = notRespondingReport(proc);
        return report instanceof ActivityManager.ProcessErrorStateInfo
                ? ((ActivityManager.ProcessErrorStateInfo) report).shortMsg : null;
    }

    private static void report(Object proc, String pkg, int pid, String action, boolean notify,
                               boolean continuous, boolean aboveSystem, String annotation) {
        Context ctx = systemContext;
        if (ctx == null) return;
        String reason = annotation;
        String details = null;
        Object report = notRespondingReport(proc);
        if (report instanceof ActivityManager.ProcessErrorStateInfo) {
            ActivityManager.ProcessErrorStateInfo info = (ActivityManager.ProcessErrorStateInfo) report;
            if (reason == null) reason = info.shortMsg;
            details = info.longMsg;
        }
        if (details != null && details.length() > MAX_DETAILS) details = details.substring(0, MAX_DETAILS);

        Intent intent = new Intent(Contract.ACTION_ANR_EVENT)
                .setComponent(new ComponentName(Contract.APP_PACKAGE, Contract.RECEIVER_ANR))
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES | Intent.FLAG_RECEIVER_FOREGROUND)
                .putExtra(Contract.EXTRA_PACKAGE, pkg)
                .putExtra(Contract.EXTRA_PROCESS, (String) Reflect.getField(proc, "processName"))
                .putExtra(Contract.EXTRA_PID, pid)
                .putExtra(Contract.EXTRA_UID, uidOf(proc))
                .putExtra(Contract.EXTRA_USER_ID, userIdOf(proc))
                .putExtra(Contract.EXTRA_REASON, reason)
                .putExtra(Contract.EXTRA_DETAILS, details)
                .putExtra(Contract.EXTRA_ACTION, action)
                .putExtra(Contract.EXTRA_NOTIFY, notify)
                .putExtra(Contract.EXTRA_CONTINUOUS, continuous)
                .putExtra(Contract.EXTRA_ABOVE_SYSTEM, aboveSystem)
                .putExtra(Contract.EXTRA_TIMESTAMP, System.currentTimeMillis());
        long token = Binder.clearCallingIdentity();
        try {
            ctx.sendBroadcastAsUser(intent, allUsers());
        } catch (Throwable t) {
            log("report broadcast failed: " + t);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private static UserHandle allUsers() {
        try {
            return (UserHandle) UserHandle.class.getField("ALL").get(null);
        } catch (Throwable t) {
            return android.os.Process.myUserHandle();
        }
    }

    // ---------------------------------------------------------------------------------------
    // Control channel: app -> system_server
    // ---------------------------------------------------------------------------------------

    private static void attach(Object service, Context ctx) {
        if (service != null && ams == null) ams = service;
        if (ctx != null && systemContext == null) systemContext = ctx;
        if (systemContext == null || !receiverRegistered.compareAndSet(false, true)) return;
        try {
            HandlerThread thread = new HandlerThread("SilentANR-control");
            thread.start();
            controlHandler = new Handler(thread.getLooper());
            IntentFilter filter = new IntentFilter(Contract.ACTION_CONTROL);
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    try {
                        onControl(this, intent);
                    } catch (Throwable t) {
                        log("control error: " + t);
                    }
                }
            };
            int flags = Build.VERSION.SDK_INT >= 33 ? Context.RECEIVER_EXPORTED : 0;
            systemContext.registerReceiver(receiver, filter, Contract.PERMISSION_CONTROL, controlHandler, flags);
            log("control receiver registered");
        } catch (Throwable t) {
            receiverRegistered.set(false);
            log("control receiver registration failed: " + t);
        }
    }

    private static void onControl(BroadcastReceiver receiver, Intent intent) {
        String op = intent.getStringExtra(Contract.EXTRA_OP);
        if (op == null) return;
        String pkg = intent.getStringExtra(Contract.EXTRA_PACKAGE);
        int userId = intent.getIntExtra(Contract.EXTRA_USER_ID, 0);
        long token = Binder.clearCallingIdentity();
        try {
            switch (op) {
                case Contract.OP_PING:
                    Bundle ping = new Bundle();
                    ping.putInt(Contract.EXTRA_HOOK_VERSION, Contract.HOOK_VERSION);
                    receiver.setResultExtras(ping);
                    break;
                case Contract.OP_KILL: {
                    int pid = intent.getIntExtra(Contract.EXTRA_PID, -1);
                    WeakReference<Object> ref = pending.remove(pid);
                    Object proc = ref != null ? ref.get() : null;
                    if (proc != null && pidOf(proc) == pid && !isKilled(proc)) {
                        killAtUserRequest(proc);
                    } else {
                        stopPackage(pkg, userId);
                    }
                    break;
                }
                case Contract.OP_FORCE_STOP:
                    receiver.setResultExtras(count(stopPackage(pkg, userId)));
                    break;
                case Contract.OP_KILL_BACKGROUND: {
                    if (pkg == null) return;
                    int before = pidsOf(pkg, userId).size();
                    Reflect.call(ams, "killBackgroundProcesses", pkg, userId);
                    android.os.SystemClock.sleep(SETTLE_MS);
                    receiver.setResultExtras(count(before - pidsOf(pkg, userId).size()));
                    break;
                }
                case Contract.OP_KILL_ALL_BACKGROUND: {
                    int before = pidsOf(null, -1).size();
                    Reflect.call(ams, "killAllBackgroundProcesses");
                    android.os.SystemClock.sleep(SETTLE_MS);
                    receiver.setResultExtras(count(before - pidsOf(null, -1).size()));
                    break;
                }
                case Contract.OP_PROCESSES:
                    receiver.setResultExtras(listProcesses());
                    break;
                case Contract.OP_SERVICES:
                    receiver.setResultExtras(listServices(pkg));
                    break;
                case Contract.OP_STOP_SERVICE: {
                    String cls = intent.getStringExtra(Contract.EXTRA_SERVICE_CLASS);
                    boolean ok = stopService(pkg, cls);
                    Bundle res = new Bundle();
                    res.putInt(Contract.EXTRA_COUNT, ok ? 1 : 0);
                    receiver.setResultExtras(res);
                    break;
                }
                default:
                    return;
            }
            receiver.setResultCode(Contract.RESULT_OK);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private static ProcInfo readProcStat(int pid) {
        long vssKb = 0;
        long rssKb = 0;
        long cpuTimeMs = 0;
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader("/proc/" + pid + "/stat"));
            String line = br.readLine();
            br.close();
            if (line != null) {
                int lastParen = line.lastIndexOf(')');
                if (lastParen != -1 && lastParen + 2 < line.length()) {
                    String[] parts = line.substring(lastParen + 2).split(" +");
                    if (parts.length >= 22) {
                        long utime = Long.parseLong(parts[11]);
                        long stime = Long.parseLong(parts[12]);
                        long vsize = Long.parseLong(parts[20]);
                        long rssPages = Long.parseLong(parts[21]);

                        cpuTimeMs = (utime + stime) * 10L; // 100 ticks per second
                        vssKb = vsize / 1024L;
                        rssKb = rssPages * 4L; // 4KB page
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return new ProcInfo(vssKb, rssKb, cpuTimeMs);
    }

    private static class ProcInfo {
        final long vssKb;
        final long rssKb;
        final long cpuTimeMs;
        ProcInfo(long vssKb, long rssKb, long cpuTimeMs) {
            this.vssKb = vssKb;
            this.rssKb = rssKb;
            this.cpuTimeMs = cpuTimeMs;
        }
    }

    private static Bundle listProcesses() {
        Bundle out = new Bundle();
        if (systemContext == null) return out;
        ActivityManager am = systemContext.getSystemService(ActivityManager.class);
        List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
        if (procs == null) return out;
        int n = procs.size();
        int[] pids = new int[n];
        for (int i = 0; i < n; i++) pids[i] = procs.get(i).pid;
        Debug.MemoryInfo[] mem = am.getProcessMemoryInfo(pids);
        String[] names = new String[n];
        String[] pkgs = new String[n];
        int[] uids = new int[n];
        int[] pss = new int[n];
        long[] vss = new long[n];
        long[] rss = new long[n];
        long[] cpuTimes = new long[n];
        int[] importance = new int[n];
        for (int i = 0; i < n; i++) {
            ActivityManager.RunningAppProcessInfo p = procs.get(i);
            names[i] = p.processName;
            pkgs[i] = p.pkgList != null && p.pkgList.length > 0 ? p.pkgList[0] : p.processName;
            uids[i] = p.uid;
            pss[i] = mem != null && i < mem.length && mem[i] != null ? mem[i].getTotalPss() : 0;
            importance[i] = p.importance;
            if (importance[i] > ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE && hasVisibleUi(p.pid)) {
                importance[i] = ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;
            }

            ProcInfo pi = readProcStat(p.pid);
            vss[i] = pi.vssKb;
            rss[i] = pi.rssKb > 0 ? pi.rssKb : (mem != null && i < mem.length && mem[i] != null ? mem[i].getTotalRss() : 0);
            cpuTimes[i] = pi.cpuTimeMs;
        }
        out.putStringArray(Contract.EXTRA_PROC_NAMES, names);
        out.putStringArray(Contract.EXTRA_PROC_PKGS, pkgs);
        out.putIntArray(Contract.EXTRA_PROC_PIDS, pids);
        out.putIntArray(Contract.EXTRA_PROC_UIDS, uids);
        out.putIntArray(Contract.EXTRA_PROC_PSS, pss);
        out.putLongArray(Contract.EXTRA_PROC_VSS, vss);
        out.putLongArray(Contract.EXTRA_PROC_RSS, rss);
        out.putLongArray(Contract.EXTRA_PROC_CPU_TIME, cpuTimes);
        out.putIntArray(Contract.EXTRA_PROC_IMPORTANCE, importance);
        return out;
    }

    @SuppressWarnings("deprecation")
    private static Bundle listServices(String targetPkg) {
        Bundle out = new Bundle();
        if (systemContext == null) return out;
        ActivityManager am = systemContext.getSystemService(ActivityManager.class);
        List<ActivityManager.RunningServiceInfo> services = am.getRunningServices(Integer.MAX_VALUE);
        if (services == null) return out;

        List<ActivityManager.RunningServiceInfo> filtered = new java.util.ArrayList<>();
        for (ActivityManager.RunningServiceInfo s : services) {
            if (targetPkg == null || targetPkg.equals(s.service.getPackageName())) {
                filtered.add(s);
            }
        }

        int n = filtered.size();
        String[] classes = new String[n];
        String[] procs = new String[n];
        int[] pids = new int[n];
        int[] uids = new int[n];
        long[] actives = new long[n];
        boolean[] foregrounds = new boolean[n];

        long now = android.os.SystemClock.elapsedRealtime();
        for (int i = 0; i < n; i++) {
            ActivityManager.RunningServiceInfo s = filtered.get(i);
            classes[i] = s.service.getClassName();
            procs[i] = s.process;
            pids[i] = s.pid;
            uids[i] = s.uid;
            actives[i] = now - s.activeSince;
            foregrounds[i] = s.foreground;
        }

        out.putStringArray(Contract.EXTRA_SERVICE_CLASSES, classes);
        out.putStringArray(Contract.EXTRA_SERVICE_PROCS, procs);
        out.putIntArray(Contract.EXTRA_SERVICE_PIDS, pids);
        out.putIntArray(Contract.EXTRA_SERVICE_UIDS, uids);
        out.putLongArray(Contract.EXTRA_SERVICE_ACTIVES, actives);
        out.putBooleanArray(Contract.EXTRA_SERVICE_FOREGROUNDS, foregrounds);
        return out;
    }

    private static boolean stopService(String pkg, String cls) {
        if (systemContext == null || pkg == null || cls == null) return false;
        try {
            Intent intent = new Intent().setClassName(pkg, cls);
            return systemContext.stopService(intent);
        } catch (Throwable t) {
            log("stopService failed: " + t);
            return false;
        }
    }

    /** True when the process shows an activity, a top-level UI or an overlay window. */
    private static boolean hasVisibleUi(int pid) {
        Object map = Reflect.getField(ams, "mPidsSelfLocked");
        if (map == null) return false;
        Object proc;
        synchronized (map) {
            proc = Reflect.callOrNull(map, "get", pid);
        }
        if (proc == null) return false;
        Object wpc = Reflect.callOrNull(proc, "getWindowProcessController");
        if (wpc == null) wpc = Reflect.getField(proc, "mWindowProcessController");
        if (Boolean.TRUE.equals(Reflect.callOrNull(wpc, "hasVisibleActivities"))) return true;
        Object state = Reflect.getField(proc, "mState");
        Object target = state != null ? state : proc;
        return Boolean.TRUE.equals(Reflect.callOrNull(target, "hasOverlayUi"))
                || Boolean.TRUE.equals(Reflect.callOrNull(target, "hasTopUi"));
    }

    // ---------------------------------------------------------------------------------------
    // ProcessRecord accessors (field names changed across Android 10 .. 16)
    // ---------------------------------------------------------------------------------------

    private static Object processOf(Object self) {
        Object app = Reflect.getField(self, "mApp");
        return app != null ? app : self;
    }

    private static String packageOf(Object proc) {
        Object info = Reflect.getField(proc, "info");
        return info instanceof ApplicationInfo ? ((ApplicationInfo) info).packageName : null;
    }

    static int pidOf(Object proc) {
        Object pid = Reflect.callOrNull(proc, "getPid");
        if (pid instanceof Integer) return (Integer) pid;
        return Reflect.getInt(proc, -1, "mPid", "pid");
    }

    private static int uidOf(Object proc) {
        int uid = Reflect.getInt(proc, -1, "uid", "mUid");
        if (uid >= 0) return uid;
        Object info = Reflect.getField(proc, "info");
        return info instanceof ApplicationInfo ? ((ApplicationInfo) info).uid : -1;
    }

    private static int userIdOf(Object proc) {
        int userId = Reflect.getInt(proc, -1, "userId", "mUserId");
        if (userId >= 0) return userId;
        int uid = uidOf(proc);
        return uid >= 0 ? uid / 100000 : 0;
    }

    private static boolean isKilled(Object proc) {
        Object killed = Reflect.callOrNull(proc, "isKilled");
        if (killed instanceof Boolean) return (Boolean) killed;
        return Reflect.getBoolean(proc, false, "mKilled", "killed");
    }

    private static boolean isNotResponding(Object proc) {
        Object errState = Reflect.getField(proc, "mErrorState");
        Object target = errState != null ? errState : proc;
        Object r = Reflect.callOrNull(target, "isNotResponding");
        if (r instanceof Boolean) return (Boolean) r;
        return Reflect.getBoolean(target, false, "mNotResponding", "notResponding");
    }

    private static boolean hasAnrDialog(Object proc) {
        Object errState = Reflect.getField(proc, "mErrorState");
        Object target = errState != null ? errState : proc;
        Object controller = Reflect.callOrNull(target, "getDialogController");
        if (controller == null) controller = Reflect.getField(target, "mDialogController");
        if (controller != null) {
            Object r = Reflect.callOrNull(controller, "hasAnrDialogs");
            return r instanceof Boolean && (Boolean) r;
        }
        return Reflect.getField(proc, "anrDialog") != null;
    }

    private static Object notRespondingReport(Object proc) {
        Object errState = Reflect.getField(proc, "mErrorState");
        Object target = errState != null ? errState : proc;
        Object r = Reflect.callOrNull(target, "getNotRespondingReport");
        return r != null ? r : Reflect.getField(target, "mNotRespondingReport", "notRespondingReport");
    }

    /**
     * Android 14+ passes a TimeoutRecord (field mReason); older versions pass the annotation as
     * the last String parameter (after activity and parent component names).
     */
    private static String annotationOf(Object[] args) {
        String lastString = null;
        for (Object arg : args) {
            if (arg == null) continue;
            if (arg.getClass().getName().endsWith("TimeoutRecord")) {
                Object reason = Reflect.getField(arg, "mReason");
                if (reason instanceof String) return (String) reason;
            } else if (arg instanceof String) {
                lastString = (String) arg;
            }
        }
        return lastString;
    }
}
