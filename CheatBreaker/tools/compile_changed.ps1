param(
    [Parameter(Mandatory = $true)]
    [string[]]$Sources
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$sourceRoot = (Resolve-Path -LiteralPath (Join-Path $projectRoot 'src\main\java')).Path
$mavenRepository = Join-Path $env:USERPROFILE '.m2\repository'
$guava = Join-Path $mavenRepository 'com\google\guava\guava\17.0\guava-17.0.jar'
$baseClasses = Join-Path $projectRoot 'target\classes'
$rebuiltClasses = Join-Path $projectRoot 'target\rebuild-classes'

if (-not (Test-Path -LiteralPath $guava -PathType Leaf)) {
    throw "Missing Guava 17.0: $guava"
}
if (-not (Test-Path -LiteralPath $baseClasses -PathType Container)) {
    throw "Missing base classes: $baseClasses"
}

$javac = Get-Command javac -ErrorAction Stop
$previousErrorAction = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
    $version = (& $javac.Source -version 2>&1 | Out-String).Trim()
} finally {
    $ErrorActionPreference = $previousErrorAction
}
if ($LASTEXITCODE -ne 0 -or $version -notmatch 'javac 1\.8\.') {
    throw "This project requires a Java 8 javac; found: $version"
}

$resolvedSources = foreach ($source in $Sources) {
    $path = (Resolve-Path -LiteralPath (Join-Path $projectRoot $source)).Path
    if ((-not $path.StartsWith($sourceRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) -or ([IO.Path]::GetExtension($path) -ne '.java')) {
        throw "Source must be a Java file under src/main/java: $source"
    }
    $path
}

New-Item -ItemType Directory -Path $rebuiltClasses -Force | Out-Null
$jars = Get-ChildItem -LiteralPath $mavenRepository -Recurse -Filter '*.jar' |
    Where-Object { $_.FullName -notmatch '\\(guava|google-collections)\\' } |
    ForEach-Object FullName
$classpath = @($guava, $rebuiltClasses, $baseClasses) + $jars -join ';'

& $javac.Source -encoding UTF-8 -source 8 -target 8 -cp $classpath -d $rebuiltClasses $resolvedSources
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE"
}
