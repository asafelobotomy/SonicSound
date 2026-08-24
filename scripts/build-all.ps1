# Cap sync + optional Gradle release (Windows)

param(
    [switch]$AssembleRelease,
    [switch]$BundleRelease,
    [switch]$Clean
)

npm ci
npm run cap:sync

Set-Location android
if ($Clean) {
    .\gradlew.bat clean
}
if ($AssembleRelease) {
    .\gradlew.bat assembleRelease
}
if ($BundleRelease) {
    .\gradlew.bat bundleRelease
}
Set-Location ..
