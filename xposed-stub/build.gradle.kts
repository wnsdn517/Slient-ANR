// Compile-only stubs of the Xposed API (API 82). At runtime LSPosed provides the real classes,
// so this module must only ever be used as `compileOnly`.
plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
