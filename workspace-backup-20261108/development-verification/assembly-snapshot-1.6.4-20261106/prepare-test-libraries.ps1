$ErrorActionPreference = 'Stop'
$taskLibraries = Join-Path $PSScriptRoot 'test-libraries'
New-Item -ItemType Directory -Path $taskLibraries -Force | Out-Null
$taskArtifacts = @(
    @('org.junit.jupiter', 'junit-jupiter-api', '5.11.4'),
    @('org.junit.jupiter', 'junit-jupiter-params', '5.11.4'),
    @('org.junit.jupiter', 'junit-jupiter-engine', '5.11.4'),
    @('org.junit.platform', 'junit-platform-commons', '1.11.4'),
    @('org.junit.platform', 'junit-platform-engine', '1.11.4'),
    @('org.junit.platform', 'junit-platform-launcher', '1.11.4'),
    @('org.opentest4j', 'opentest4j', '1.3.0'),
    @('org.apiguardian', 'apiguardian-api', '1.1.2'),
    @('org.mockito', 'mockito-core', '5.15.2'),
    @('net.bytebuddy', 'byte-buddy', '1.15.11'),
    @('net.bytebuddy', 'byte-buddy-agent', '1.15.11'),
    @('org.objenesis', 'objenesis', '3.3')
)
$taskManifest = foreach ($taskArtifact in $taskArtifacts) {
    $taskName = $taskArtifact[1] + '-' + $taskArtifact[2] + '.jar'
    $taskUri = 'https://repo.maven.apache.org/maven2/' + $taskArtifact[0].Replace('.', '/') + '/' +
        $taskArtifact[1] + '/' + $taskArtifact[2] + '/' + $taskName
    $taskPath = Join-Path $taskLibraries $taskName
    if (-not (Test-Path -LiteralPath $taskPath)) { Invoke-WebRequest -Uri $taskUri -OutFile $taskPath -TimeoutSec 60 }
    [pscustomobject]@{file=$taskName;source=$taskUri;bytes=(Get-Item -LiteralPath $taskPath).Length;sha256=(Get-FileHash -LiteralPath $taskPath -Algorithm SHA256).Hash}
}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'test-libraries-manifest.json'),
    ($taskManifest | ConvertTo-Json -Depth 3), [Text.UTF8Encoding]::new($false))
$taskManifest | Select-Object file,bytes
