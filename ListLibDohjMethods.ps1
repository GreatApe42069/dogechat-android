# =========================
# PowerShell script: List all public methods from dogecoinj-core JAR
# =========================

# --- Step 1: Set the path to your JAR ---
$dogeJar = "C:\Program Files\dogechat-android\app\libs\dogecoinj-core-0.18-doge.jar"

if (-not (Test-Path $dogeJar)) {
    Write-Error "Jar not found at: $dogeJar`nUpdate the path at the top of this script if your file is elsewhere."
    exit 1
}

# --- Optional: try to locate protobuf-javalite from Gradle cache ---
$gradleCache = Join-Path $env:USERPROFILE ".gradle\caches\modules-2\files-2.1"
$protobufJar = $null
try {
    $maybeProtoRoot = Join-Path $gradleCache "com.google.protobuf\protobuf-javalite\3.18.0"
    if (Test-Path $maybeProtoRoot) {
        $protobufJar = Get-ChildItem -Path $maybeProtoRoot -Recurse -Filter "protobuf-javalite-3.18.0.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
    }
} catch { }

# Build the classpath string (Windows uses semicolons)
$runtimeJars = @($dogeJar)
if ($protobufJar -and (Test-Path $protobufJar.FullName)) {
    $runtimeJars += $protobufJar.FullName
}
$classpath = [string]::Join(";", $runtimeJars)

# --- Step 2: Get all classes from the jar (exclude META-INF and module-info) ---
$classes = & jar tf "$dogeJar" 2>$null |
    Where-Object { $_ -like "*.class" -and $_ -notlike "META-INF/*" -and $_ -ne "module-info.class" } |
    ForEach-Object { $_ -replace "/", "." -replace "\.class$", "" }

if (-not $classes -or $classes.Count -eq 0) {
    Write-Error "No classes found in $dogeJar"
    exit 1
}

# --- Step 3: Iterate through classes and extract public methods ---
$results = @()
$processed = 0
$total = $classes.Count

foreach ($c in $classes) {
    $processed++
    if (($processed % 250) -eq 0) {
        Write-Host ("Processed {0}/{1} classes..." -f $processed, $total)
    }

    try {
        $javapOutput = & javap -public -classpath "$classpath" "$c" 2>$null
        if (-not $javapOutput) { continue }

        foreach ($line in $javapOutput) {
            # Keep only method signatures (lines with parentheses), skip class/field/brace lines
            if ($line -match "\(.*\)" -and $line -notmatch "^\s*(class|interface|enum)\b" -and $line.Trim() -ne "{" -and $line.Trim() -ne "}") {
                $clean = $line.Trim() -replace ";$", ""
                $results += [PSCustomObject]@{
                    Class           = $c
                    MethodSignature = $clean
                }
            }
        }
    } catch {
        Write-Warning "Failed to parse $c"
    }
}

# --- Step 4: Output results ---
# To console
$results | Sort-Object Class, MethodSignature | Format-Table -AutoSize

# Save to CSV (same filename as before for continuity)
$outCsv = Join-Path (Get-Location) "libdohj_methods.csv"
$results | Sort-Object Class, MethodSignature | Export-Csv -Path $outCsv -NoTypeInformation

Write-Host "`nDone! Methods saved to $outCsv"
Write-Host "Classes processed: $processed, Methods found: $($results.Count)"