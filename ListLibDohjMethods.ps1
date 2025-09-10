# =========================
# PowerShell script: List all public methods from libdohj-core JAR
# =========================

# --- Step 1: Set the path to your JARs ---
$libDohjJar = "C:\Program Files\dogechat-android\app\libs\libdohj-core-0.16-SNAPSHOT.jar"

# Replace this with full Gradle runtime classpath if needed
# Example: include bitcoinj-core and other dependencies
$runtimeJars = @(
    $libDohjJar,
    "C:\Users\<USER>\.gradle\caches\modules-2\files-2.1\org.bitcoinj\bitcoinj-core\0.16.1\<HASH>\bitcoinj-core-0.16.1.jar",
    "C:\Program Files\dogechat-android\app\libs\protobuf-javalite-3.18.0.jar"
)

# Build the classpath string
$classpath = [string]::Join(";", $runtimeJars)

# --- Step 2: Get all classes from libdohj-core ---
$classes = jar tf $libDohjJar |
    Where-Object { $_ -like "*.class" } |
    ForEach-Object { $_ -replace "/", "." -replace "\.class$", "" }

# --- Step 3: Iterate through classes and extract public methods ---
$results = @()
foreach ($c in $classes) {
    try {
        $methods = javap -public -classpath $classpath $c 2>$null
        foreach ($m in $methods) {
            # Skip lines that aren't methods (filter out "class" and "{" lines)
            if ($m -match "\(.*\)") {
                # Clean method line and split into return type + method name
                $clean = $m.Trim() -replace ";", ""
                $results += [PSCustomObject]@{
                    Class = $c
                    MethodSignature = $clean
                }
            }
        }
    } catch {
        Write-Host "Failed to parse $c"
    }
}

# --- Step 4: Output results ---
# To console
$results | Format-Table -AutoSize

# Optionally, save to CSV
$results | Export-Csv -Path "libdohj_methods.csv" -NoTypeInformation

Write-Host "`nDone! Methods saved to libdohj_methods.csv"
