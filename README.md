# Silent ANR

An LSPosed module that **hides and logs "App isn't responding" (ANR) dialogs** on Android.

## Features

| Feature | What it does |
| --- | --- |
| Dialog blocking | Hooks `AppErrors.handleShowAnrUi` in system_server so the ANR dialog never appears. |
| Rules | **Notify** / **Wait** / **Kill** / **System dialog**. Set a default, a rule per ANR type (input, service, broadcast, provider, job, app start, other) and a rule per app. App rule → type rule → default. |
| Notifications | A notification replaces the dialog, with *Close app*, *Wait* and *Always wait* actions. Automatic actions can post a silent notification too. |
| Log | App, process, PID/UID, reason (e.g. `Input dispatching timed out`) and the system report (CPU usage). |
| Filter and sort | Search, time range (24h / 7d / 30d / all), outcome, ANR type and app filters; newest, oldest, app name or frequency ordering. |
| Stats | Daily trend, outcome split, apps that hang most, ANR types. |
| System tab | Live memory and swap, per-app memory (PSS) and state (a process with a visible window counts as visible), kill background / force stop, kill all cached apps, drop caches (root), log retention and size limits. |
| System-app safety | Stopping a system app asks first; choosing *Kill* for a system app shows a warning. |
| Export | CSV export. |

## Install

1. Download `SilentANR-release.apk` from the latest [Release](../../releases) and install it (`SilentANR-debug.apk` is the unminified build).
2. LSPosed manager → Modules → enable **Silent ANR**.
3. Reboot. The Overview screen shows "Working" once the hook is loaded.

The scope is **fixed by the module** to System Framework, so there is nothing to pick in the scope list
(LSPosed/Vector show it as "Fixed by the module"). Changing actions, notifications and the like takes
effect immediately, without a reboot.

## How it works

```
InputDispatcher / ActiveServices / BroadcastQueue timeout
  └─ AnrHelper → ProcessErrorStateRecord.appNotResponding()   (Android 12+, ProcessRecord on 10–11)
        ├─ silent background ANR → killed right away       ← logged only (BG_KILLED)
        └─ SHOW_NOT_RESPONDING_UI_MSG
             └─ AppErrors.handleShowAnrUi(Message)     ← action decided here
                  ├─ mode = app rule → ANR-type rule → default (type from the ANR reason)
                  ├─ Wait / Notify: setNotResponding(false) + clearAnrDialogs() + scheduleServiceTimeoutLocked()
                  │                 (same as the dialog's "Wait" button)
                  ├─ Kill:          ActivityManagerService.killAppAtUsersRequest()  (same as "Close app")
                  └─ System dialog: original method runs
```

- **hook → app**: explicit broadcast (`ANR_EVENT`, receiver guarded by `DUMP` so it can't be forged) → Room DB + notification
- **app → hook**: a receiver registered inside system_server (guarded by the `signature`-level `CONTROL` permission), driven with ordered broadcasts
- **shared settings**: LSPosed `xposedsharedprefs` + `XSharedPreferences`
- **fixed scope**: `META-INF/xposed/module.prop` sets `staticScope=true` and `scope.list` names `system`. `targetApiVersion` is
  left unset and `java_init.list` is empty, so the framework still loads the hook through the legacy `assets/xposed_init`.

Requires Android 10 (API 29) or later and LSPosed (API 93+). Field and method names are resolved reflectively with per-version fallbacks.

## Build

```
./gradlew assembleDebug
```

Releases are built by the *Build and release APK* workflow, which runs only when started by hand
(Actions → Build and release APK → Run workflow) and publishes a GitHub Release tagged `v<version>`.

`xposed-stub` is a compile-only stub of the Xposed API and is not packaged into the APK (LSPosed provides it at runtime).
