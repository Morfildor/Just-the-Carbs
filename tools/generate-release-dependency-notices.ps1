param(
    [string]$OutputPath = "docs/resolved-release-dependencies.md"
)

$ErrorActionPreference = "Stop"

if (-not $env:JAVA_HOME) { $env:JAVA_HOME = "C:\atools\jdk-21.0.12+8" }
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = "C:\atools\sdk" }

$repositoryRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repositoryRoot
try {
    $report = & .\gradlew.bat :app:dependencies --configuration releaseRuntimeClasspath -q 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle dependency resolution failed with exit code $LASTEXITCODE.`n$($report -join "`n")"
    }

    $coordinates = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($line in $report) {
        if ($line -match '([A-Za-z0-9_.-]+):([A-Za-z0-9_.-]+):([0-9][A-Za-z0-9_.-]*)(?: -> ([0-9][A-Za-z0-9_.-]*))?') {
            $version = if ($Matches[4]) { $Matches[4] } else { $Matches[3] }
            [void]$coordinates.Add("$($Matches[1]):$($Matches[2]):$version")
        }
    }

    if ($coordinates.Count -lt 1) {
        throw "No Maven coordinates were parsed from releaseRuntimeClasspath."
    }

    $gradleFiles = Join-Path ([Environment]::GetFolderPath("UserProfile")) ".gradle\caches\modules-2\files-2.1"
    $rows = foreach ($coordinate in ($coordinates | Sort-Object)) {
        $group, $module, $version = $coordinate.Split(':', 3)
        $moduleDirectory = Join-Path $gradleFiles "$group\$module\$version"
        $pom = Get-ChildItem -LiteralPath $moduleDirectory -Recurse -Filter "$module-$version.pom" -File -ErrorAction SilentlyContinue |
            Select-Object -First 1

        $licences = @()
        if ($pom) {
            try {
                [xml]$xml = Get-Content -Raw -LiteralPath $pom.FullName
                $licences = @($xml.project.licenses.license | ForEach-Object {
                    if ($_.name) { [string]$_.name }
                } | Where-Object { $_ } | Sort-Object -Unique)
            } catch {
                $licences = @("POM could not be parsed")
            }
        }
        if ($licences.Count -eq 0) {
            $licences = @("Not declared in the published POM")
        }

        [pscustomobject]@{
            Coordinate = $coordinate
            Licence = ($licences -join "; ") -replace '\|', '\|'
        }
    }

    $today = Get-Date -Format "yyyy-MM-dd"
    $content = [System.Collections.Generic.List[string]]::new()
    $content.Add("# Resolved release dependencies")
    $content.Add("")
    $content.Add(('Generated on **{0}** from the exact `releaseRuntimeClasspath` resolved by Gradle.' -f $today))
    $content.Add('The project permits only Google Maven and Maven Central in `settings.gradle.kts`.')
    $content.Add("")
    $content.Add("This is inventory evidence, not a legal opinion. A missing licence means the published Maven")
    $content.Add("POM did not declare one; inspect the artifact's bundled notices or publisher terms before release.")
    $content.Add("")
    $content.Add("Regenerate with:")
    $content.Add("")
    $content.Add('```powershell')
    $content.Add('.\tools\generate-release-dependency-notices.ps1')
    $content.Add('```')
    $content.Add("")
    $content.Add("Resolved artifacts: **$($rows.Count)**")
    $content.Add("")
    $content.Add("| Maven coordinate | Licence declared in published POM |")
    $content.Add("|---|---|")
    foreach ($row in $rows) {
        $content.Add("| ``$($row.Coordinate)`` | $($row.Licence) |")
    }
    $content.Add("")

    $destination = Join-Path $repositoryRoot $OutputPath
    $destinationDirectory = Split-Path -Parent $destination
    if (-not (Test-Path -LiteralPath $destinationDirectory)) {
        New-Item -ItemType Directory -Path $destinationDirectory | Out-Null
    }
    Set-Content -LiteralPath $destination -Value $content -Encoding utf8
    Write-Output "Wrote $($rows.Count) resolved artifacts to $destination"
} finally {
    Pop-Location
}
