package io.github.wnsdn517.silentanr.xposed;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Minimal, exception-free reflection helpers. system_server internals differ between Android
 * versions and OEM ROMs, so every lookup walks the class hierarchy and returns null on failure.
 */
final class Reflect {
    private Reflect() {
    }

    static Field findField(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    static Object getField(Object obj, String... names) {
        if (obj == null) return null;
        for (String name : names) {
            Field f = findField(obj.getClass(), name);
            if (f == null) continue;
            try {
                return f.get(obj);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    static boolean setField(Object obj, String name, Object value) {
        if (obj == null) return false;
        Field f = findField(obj.getClass(), name);
        if (f == null) return false;
        try {
            f.set(obj, value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static int getInt(Object obj, int def, String... names) {
        Object v = getField(obj, names);
        return v instanceof Integer ? (Integer) v : def;
    }

    static boolean getBoolean(Object obj, boolean def, String... names) {
        Object v = getField(obj, names);
        return v instanceof Boolean ? (Boolean) v : def;
    }

    /** Finds a method by name and arity (first match, any parameter types). */
    static Method findMethod(Class<?> cls, String name, int argCount) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && (argCount < 0 || m.getParameterTypes().length == argCount)) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        return null;
    }

    /**
     * Invokes the method with exactly {@code args.length} parameters. Returns {@link #FAILED}
     * when the method is missing or throws, so callers can distinguish "null result".
     */
    static Object call(Object obj, String name, Object... args) {
        if (obj == null) return FAILED;
        Method m = findMethod(obj.getClass(), name, args.length);
        if (m == null) return FAILED;
        try {
            return m.invoke(obj, args);
        } catch (Throwable t) {
            AnrHooker.log("call " + name + " failed: " + t);
            return FAILED;
        }
    }

    static Object callOrNull(Object obj, String name, Object... args) {
        Object r = call(obj, name, args);
        return r == FAILED ? null : r;
    }

    static final Object FAILED = new Object();
}
