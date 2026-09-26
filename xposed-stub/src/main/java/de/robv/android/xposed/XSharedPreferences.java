package de.robv.android.xposed;

import java.io.File;
import java.util.Map;
import java.util.Set;

/** Compile-only stub. The real implementation is provided by LSPosed at runtime. */
public final class XSharedPreferences {
    public XSharedPreferences(String packageName, String prefFileName) {
        throw new UnsupportedOperationException("stub");
    }

    public File getFile() {
        throw new UnsupportedOperationException("stub");
    }

    public boolean makeWorldReadable() {
        throw new UnsupportedOperationException("stub");
    }

    public boolean hasFileChanged() {
        throw new UnsupportedOperationException("stub");
    }

    public void reload() {
        throw new UnsupportedOperationException("stub");
    }

    public Map<String, ?> getAll() {
        throw new UnsupportedOperationException("stub");
    }

    public String getString(String key, String defValue) {
        throw new UnsupportedOperationException("stub");
    }

    public Set<String> getStringSet(String key, Set<String> defValues) {
        throw new UnsupportedOperationException("stub");
    }

    public int getInt(String key, int defValue) {
        throw new UnsupportedOperationException("stub");
    }

    public long getLong(String key, long defValue) {
        throw new UnsupportedOperationException("stub");
    }

    public boolean getBoolean(String key, boolean defValue) {
        throw new UnsupportedOperationException("stub");
    }

    public boolean contains(String key) {
        throw new UnsupportedOperationException("stub");
    }
}
