param(
    [Parameter(Mandatory)] [string] $BaselineSource,
    [Parameter(Mandatory)] [string] $OutputDirectory,
    [ValidateRange(1, 5)] [int] $Runs = 5,
    [ValidateSet(3, 27)] [int] $Keys = 27
)

$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$baseline = (Resolve-Path -LiteralPath $BaselineSource).Path
if ([IO.Path]::GetFileName($baseline) -ne 'OverloadedInterfaceBlockEntity.java') {
    throw 'BaselineSource must be an immutable snapshot named OverloadedInterfaceBlockEntity.java'
}
$output = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $output) { throw "Refusing to overwrite benchmark output: $output" }
New-Item -ItemType Directory -Path $output | Out-Null
$sources = @(
    $baseline,
    (Join-Path $project 'src/main/java/com/moakiee/ae2lt/blockentity/OverloadedInterfaceBlockEntity.java'),
    (Join-Path $project 'src/jdb/java/com/moakiee/ae2lt/blockentity/OverloadedInterfaceExportFairnessGameTests.java'),
    (Join-Path $PSScriptRoot 'interface-export-fairness-test.init.gradle')
)
$hashes = @($sources | ForEach-Object { (Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash })
$head = (& git -C $project rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve Git HEAD' }
$gradle = Join-Path $project 'gradlew.bat'

for ($run = 1; $run -le $Runs; $run++) {
    $variants = if ($run % 2 -eq 1) { @('baseline', 'candidate') } else { @('candidate', 'baseline') }
    foreach ($variant in $variants) {
        for ($index = 0; $index -lt $sources.Count; $index++) {
            if ((Get-FileHash -LiteralPath $sources[$index] -Algorithm SHA256).Hash -ne $hashes[$index]) {
                throw "Benchmark source changed: $($sources[$index])"
            }
        }
        $world = Join-Path $output "worlds/$variant-run$run"
        $scenario = "fairness-$variant-256x$Keys-run$run"
        $arguments = @('-p', $project, '--offline', '--no-configuration-cache',
            '-I', (Join-Path $PSScriptRoot 'interface-export-fairness-test.init.gradle'),
            'runGameTestServer', '-Pae2ltFairnessBenchmark=true', "-Pae2ltFairnessKeys=$Keys",
            "-Pae2ltFairnessRunDirectory=$world", "-Pae2ltBenchmarkScenario=$scenario",
            "-Pae2ltBenchmarkGitHead=$head")
        if ($variant -eq 'baseline') { $arguments += "-Pae2ltFairnessBaselineSource=$baseline" }
        Write-Output "Starting $scenario"
        $ErrorActionPreference = 'Continue'
        & $gradle @arguments *> (Join-Path $output "$variant-run$run.log")
        $exitCode = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        if ($exitCode -ne 0) { throw "Benchmark failed: $scenario (exit $exitCode)" }
        $metrics = @(Get-ChildItem -LiteralPath (Join-Path $world 'benchmark-reports/wireless-interface-io') -Filter '*.json')
        if ($metrics.Count -ne 1) { throw "Expected exactly one timing report: $scenario" }
        $workload = Join-Path $world 'benchmark-reports/fairness-workload.json'
        $result = Get-Content -LiteralPath $workload -Raw | ConvertFrom-Json
        if ($result.targets -ne 256 -or $result.keys -ne $Keys -or $result.batch -ne 512 -or
                $result.samples -ne 1200 -or $result.plannedEvents -ne 307200 -or
                $result.completedEvents -ne $result.plannedEvents -or
                $result.minimumTargetThroughput -ne 1 -or $result.minimumWindowThroughput -ne 1 -or
                -not $result.ownershipVerified -or $result.bufferedItems -ne 0) {
            throw "Fixed workload or throughput changed: $scenario"
        }
        Copy-Item -LiteralPath $metrics[0].FullName -Destination (Join-Path $output "$variant-run$run.metrics.json")
        Copy-Item -LiteralPath (Join-Path $metrics[0].DirectoryName ($metrics[0].BaseName + '-ticks.csv')) `
            -Destination (Join-Path $output "$variant-run$run-ticks.csv")
        Copy-Item -LiteralPath $workload -Destination (Join-Path $output "$variant-run$run.workload.json")
        Write-Output "Completed $scenario; throughput=100%; maxKeyWait=$($result.maxKeyWait)"
    }
}

[ordered]@{
    schema = 1; gitHead = $head; worktreeDirty = $true; runs = $Runs; targets = 256; keys = $Keys
    batch = 512; warmupTicks = 200; sampleTicks = 1200; sources = $sources; sha256 = $hashes
    freshJvmPerRun = $true; alternatingVersionOrder = $true; sameFixedWorkload = $true
} | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $output 'manifest.json') -Encoding UTF8
Write-Output "Fairness A/B reports: $output"
