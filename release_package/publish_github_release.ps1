# Spindle Release Publisher Script
# Publishes Spindle Standard v1.6.0-STUDIO to GitHub Releases

$TAG = "v1.6.0-STUDIO"
$TITLE = "Spindle Standard v1.6.0-STUDIO"
$NOTES_FILE = "RELEASE_NOTES_v1.6.0-STUDIO.md"
$ARTIFACTS_DIR = "release_package/latest"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Publishing $TITLE to GitHub Releases" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan

# Check if gh CLI is installed
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Write-Host "[!] GitHub CLI (gh) is not installed." -ForegroundColor Yellow
    Write-Host "Please create the release manually on GitHub Web at:" -ForegroundColor Cyan
    Write-Host "https://github.com/manaphassan/Spindle/releases/new?tag=$TAG" -ForegroundColor White
    exit 0
}

# Check gh auth status
$authStatus = & gh auth status 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "[!] GitHub CLI is not logged in." -ForegroundColor Yellow
    Write-Host "Run: gh auth login" -ForegroundColor Green
    Write-Host ""
    Write-Host "Or create the release manually in your browser at:" -ForegroundColor Cyan
    Write-Host "https://github.com/manaphassan/Spindle/releases/new?tag=$TAG" -ForegroundColor White
    Write-Host ""
    Write-Host "Attach the following files from $ARTIFACTS_DIR:" -ForegroundColor Yellow
    Get-ChildItem -File $ARTIFACTS_DIR | ForEach-Object { Write-Host " - $($_.FullName)" -ForegroundColor White }
    exit 0
}

Write-Host "[*] Creating GitHub Release $TAG..." -ForegroundColor Cyan

& gh release create $TAG `
    "$ARTIFACTS_DIR/Spindle-Standard-v1.6.0-STUDIO-release.apk" `
    "$ARTIFACTS_DIR/Spindle-Standard-v1.6.0-STUDIO-release.aab" `
    "$ARTIFACTS_DIR/Spindle-Standard-v1.6.0-STUDIO-debug.apk" `
    "$ARTIFACTS_DIR/Spindle-Lite-v1.0.1-LITE.apk" `
    "$ARTIFACTS_DIR/SHA256SUMS.txt" `
    "$ARTIFACTS_DIR/PACKAGE_INFO.json" `
    --title "$TITLE" `
    --notes-file "$NOTES_FILE"

if ($LASTEXITCODE -eq 0) {
    Write-Host "[✓] GitHub Release $TAG successfully published!" -ForegroundColor Green
} else {
    Write-Host "[!] Failed to publish release via gh CLI." -ForegroundColor Red
}
