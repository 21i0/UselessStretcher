[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidateRange(1,2147483647)][int]$ExpectedTests,
    [string]$BuildResult = (Join-Path $PSScriptRoot 'build-result.json'),
    [string]$TestLog = (Join-Path $PSScriptRoot 'unit-tests.log')
)
# ZIP/ASM/file verification only. No game class is loaded or initialized.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$workspace = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path.TrimEnd('\')
if ([IO.Path]::GetPathRoot($workspace) -ine 'D:\') { throw 'All audit outputs must remain on D:.' }
$version = '1.21.1-1.6.4-catalog-P4-assembly-lock'
$prefix = 'com/sorrowmist/useless/stretcher/'
$slotMember = $prefix + 'mixin/InstalledStretcherSlotMixin.class'
$guardMember = $prefix + 'content/ae/InstalledStretcherPlacementGuard.class'
$expectedMembers = @($slotMember,$guardMember)
$paths = [ordered]@{
    baselineP4='D:\AI\candidates\useless-stretcher-catalog-P4-20261107-a\delivery\useless_stretcher-1.21.1-1.6.4-catalog-P4.jar'
    frozenP3='D:\AI\candidates\useless-stretcher-catalog-P3-20261107-a\delivery\useless_stretcher-1.21.1-1.6.4-catalog-P3.jar'
    published163='D:\AI\useless_stretcher-1.21.1-1.6.3.jar'
    native246='D:\AI\candidates\useless-stretcher-catalog-P3-20261107-a\libs\compile\useless_mod-1.21.1-2.4.6.jar'
    native2462='D:\AI\sources\DollChronicle\build\mygenesis-longhuan-r6-D\mods\useless_mod-1.21.1-2.4.6.2.jar'
}
$expectedHashes = @{
    baselineP4='9C57CBB3B1343C8837B773003AC906C630378786A784B7490F1F4E943A5021FC'
    frozenP3='0D2CCCBD03E95EEC6AC1688F00028372D92313D90189BA8B5EBCA4DC3EE6832A'
    published163='00A10F69575B941C6A7545D28B43749362CB02FC9E30288ABC271520537B5BFC'
    native246='B86A12E8BD6417FD3C89F7C723CA195D68D1F9F9F099480850FFD3C93E5897BC'
    native2462='9F6F54AF76AA255123B354C8725D55C2ACE4525B8683E1136ACAEBCCC8813B17'
}
$runDirectory = Join-Path $PSScriptRoot ('audit-runs\' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmssfff'))
New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null
$report = [ordered]@{status='RUNNING'; timestampUtc=[DateTime]::UtcNow.ToString('o'); gameLaunched=$false; version=$version}
function Assert-Audit([bool]$Condition,[string]$Message) { if (-not $Condition) { throw $Message } }
function Local-Path([string]$Path) {
    $full = [IO.Path]::GetFullPath($Path)
    Assert-Audit ($full.StartsWith($script:workspace + '\',[StringComparison]::OrdinalIgnoreCase)) "Candidate path escapes workspace: $full"
    return $full
}
function Same-Set($Actual,$Expected,[string]$Purpose) {
    $a=@($Actual); $e=@($Expected)
    Assert-Audit ($a.Count -eq @($a | Select-Object -Unique).Count) "Duplicate $Purpose"
    Assert-Audit (($a.Count -eq $e.Count) -and ((@($a | Sort-Object) -join "`n") -ceq (@($e | Sort-Object) -join "`n"))) "Unexpected $Purpose"
}
function File-Hash([string]$Path) { return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash }
function Read-Zip([string]$Path) {
    $items=@{}; $zip=[IO.Compression.ZipFile]::OpenRead($Path)
    try {
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName.EndsWith('/')) { continue }
            Assert-Audit (-not $items.ContainsKey($entry.FullName)) "Duplicate ZIP member: $($entry.FullName)"
            $stream=$entry.Open(); $memory=[IO.MemoryStream]::new(); $hasher=[Security.Cryptography.SHA256]::Create()
            try {
                $stream.CopyTo($memory); $bytes=$memory.ToArray()
                $items[$entry.FullName]=[pscustomobject]@{bytes=$bytes;sha256=[Convert]::ToHexString($hasher.ComputeHash($bytes));size=$bytes.Length}
            } finally { $stream.Dispose();$memory.Dispose();$hasher.Dispose() }
        }
    } finally { $zip.Dispose() }
    return ,$items
}
function Zip-Text($Zip,[string]$Name) {
    Assert-Audit ($Zip.ContainsKey($Name)) "Missing ZIP member: $Name"
    return [Text.Encoding]::UTF8.GetString($Zip[$Name].bytes)
}
function Argument-Lines([string]$Path) {
    return @(Get-Content -LiteralPath (Local-Path $Path) | ForEach-Object { $_.Trim('"').Replace('/','\') })
}
function Check-CompileArguments($Arguments,$Sources,[string]$Destination) {
    $argsList=@($Arguments)
    $destIndex=[Array]::IndexOf($argsList,'-d')
    Assert-Audit ($destIndex -ge 0 -and $argsList[$destIndex+1] -ceq $Destination) 'Compiler destination differs from build record.'
    Same-Set @($argsList | Where-Object { $_.EndsWith('.java',[StringComparison]::OrdinalIgnoreCase) }) $Sources 'compiler source arguments'
    Assert-Audit ($argsList -ccontains '-proc:none') 'Unexpected compiler annotation processing.'
}
function Verify-Classes($Recorded,[string]$Directory) {
    $actual=@(Get-ChildItem -LiteralPath $Directory -Filter '*.class' -Recurse | ForEach-Object {
        [pscustomobject]@{name=$_.FullName.Substring($Directory.Length+1).Replace('\','/');sha256=(File-Hash $_.FullName)}
    })
    Same-Set @($Recorded | ForEach-Object name) @($actual | ForEach-Object name) 'compiled class inventory'
    foreach ($entry in $actual) {
        $match=@($Recorded | Where-Object name -CEQ $entry.name)
        Assert-Audit ($match.Count -eq 1 -and $match[0].sha256 -ceq $entry.sha256) "Compiled class changed: $($entry.name)"
    }
    return @($actual)
}
try {
    $BuildResult=Local-Path $BuildResult; $TestLog=Local-Path $TestLog
    $build=Get-Content -LiteralPath $BuildResult -Raw | ConvertFrom-Json
    Assert-Audit ($build.gameLaunched -eq $false -and $build.compilerExitCode -eq 0 -and $build.testCompilerExitCode -eq 0 -and $build.testExitCode -eq 0) 'Build did not record a successful offline compilation and test run.'
    $jar=Local-Path $build.jar; $classes=Local-Path $build.classesDirectory; $testClasses=Local-Path $build.testClassesDirectory
    Assert-Audit ($classes -cne $testClasses -and (Split-Path $classes -Parent) -ceq (Split-Path $testClasses -Parent)) 'Production and test output directories must be distinct siblings.'
    $before=[ordered]@{}
    foreach ($name in @($paths.Keys)) {
        $paths[$name]=(Resolve-Path -LiteralPath $paths[$name]).Path
        $before[$name]=File-Hash $paths[$name]
        Assert-Audit ($before[$name] -ceq $expectedHashes[$name]) "Exact reference bytes differ: $name"
    }
    $before.candidate=File-Hash $jar
    Assert-Audit ($before.candidate -ceq $build.sha256 -and (Get-Item -LiteralPath $jar).Length -eq $build.size) 'Candidate differs from successful build record.'
    Assert-Audit ($build.baselineSha256 -ceq $expectedHashes.baselineP4) 'Build did not use exact frozen P4.'
    $report.paths=$paths; $report.candidate=$jar; $report.hashesBefore=$before
    $mainExpected=@(
        (Join-Path $workspace 'src\main\java\com\sorrowmist\useless\stretcher\mixin\InstalledStretcherSlotMixin.java'),
        (Join-Path $workspace 'src\main\java\com\sorrowmist\useless\stretcher\content\ae\InstalledStretcherPlacementGuard.java')
    )
    $mainActual=@(Get-ChildItem -LiteralPath (Join-Path $workspace 'src\main\java') -Recurse -Filter '*.java' | ForEach-Object FullName)
    Same-Set $mainActual $mainExpected 'production source files'
    Same-Set @($build.mainSources | ForEach-Object path) $mainExpected 'recorded production sources'
    $testExpected=@(Get-ChildItem -LiteralPath (Join-Path $workspace 'src\test\java') -Recurse -Filter '*.java' | ForEach-Object FullName) + @((Join-Path $PSScriptRoot 'OfflineTestLauncher.java'))
    Same-Set @($build.testSources | ForEach-Object path) $testExpected 'recorded test sources'
    $metadata=Join-Path $workspace 'src\main\resources\META-INF\neoforge.mods.toml'
    $testResources=@(Get-ChildItem -LiteralPath (Join-Path $workspace 'src\test\resources') -File -Recurse | ForEach-Object FullName)
    Same-Set @($build.inputs | ForEach-Object path) (@($mainExpected)+@($testExpected)+@($metadata)+@($testResources)) 'build input inventory'
    foreach ($entry in @($build.inputs)+@($build.mainSources)+@($build.testSources)) {
        Assert-Audit ((File-Hash (Local-Path $entry.path)) -ceq $entry.sha256) "Input changed after build/test: $($entry.path)"
        $input=@($build.inputs | Where-Object path -CEQ $entry.path)
        Assert-Audit ($input.Count -eq 1 -and $input[0].sha256 -ceq $entry.sha256) "Source/input record mismatch: $($entry.path)"
    }
    $inputRecord=Join-Path (Split-Path $classes -Parent) 'inputs-before-build.json'
    $beforeInputs=Get-Content -LiteralPath $inputRecord -Raw | ConvertFrom-Json
    Assert-Audit (($beforeInputs | ConvertTo-Json -Depth 6 -Compress) -ceq ($build.inputs | ConvertTo-Json -Depth 6 -Compress)) 'Build-before inputs differ from tested input record.'
    Check-CompileArguments (Argument-Lines $build.mainCompilerArguments) $mainExpected $classes
    Check-CompileArguments (Argument-Lines $build.testCompilerArguments) $testExpected $testClasses
    $runArgs=Argument-Lines $build.javaArguments
    Assert-Audit ($runArgs[-2] -ceq 'OfflineTestLauncher' -and $runArgs[-1] -ceq $testClasses) 'Tests were not launched with the recorded output inventory.'
    $classpathIndex=[Array]::IndexOf($runArgs,'-cp')
    Assert-Audit ($classpathIndex -ge 0) 'No test classpath recorded.'
    $classpathEntries=@($runArgs[$classpathIndex+1].Split(';'))
    Assert-Audit ($classpathEntries[0] -ceq $classes -and $classpathEntries[1] -ceq $testClasses) 'Test runtime did not prioritize candidate production/test outputs.'
    $compiled=Verify-Classes $build.compiledClasses $classes
    Same-Set @($compiled | ForEach-Object name) $expectedMembers 'production compiled members'
    $compiledTests=Verify-Classes $build.testCompiledClasses $testClasses
    Assert-Audit (@($compiledTests | Where-Object name -CEQ ($prefix+'content/ae/InstalledStretcherPlacementGuardTest.class')).Count -eq 1) 'New placement guard test class was not compiled.'
    $testLogHash=File-Hash $TestLog
    Assert-Audit ($testLogHash -ceq $build.testLogSha256) 'Test log changed after build.'
    $runs=[regex]::Matches((Get-Content -LiteralPath $TestLog -Raw),'(?s)Test run finished after [^\r\n]+(?<summary>.*?)(?=Test run finished after|\z)')
    Assert-Audit ($runs.Count -gt 0) 'No final JUnit summary.'
    $summary=$runs[$runs.Count-1].Groups['summary'].Value; $tests=[ordered]@{}
    foreach ($metric in @('tests found','tests skipped','tests started','tests aborted','tests successful','tests failed','containers aborted','containers failed')) {
        $matches=[regex]::Matches($summary,'(?m)^\[\s*(\d+)\s+'+[regex]::Escape($metric)+'\s*\]\s*$')
        Assert-Audit ($matches.Count -eq 1) "Missing or ambiguous JUnit metric: $metric"
        $tests[$metric]=[int]$matches[0].Groups[1].Value
    }
    Assert-Audit ($tests['tests found'] -eq $ExpectedTests -and $tests['tests started'] -eq $ExpectedTests -and $tests['tests successful'] -eq $ExpectedTests) 'Final JUnit summary did not execute and pass the expected full suite.'
    foreach ($metric in @('tests skipped','tests aborted','tests failed','containers aborted','containers failed')) { Assert-Audit ($tests[$metric] -eq 0) "JUnit did not fully pass: $metric" }
    $base=Read-Zip $paths.baselineP4; $candidate=Read-Zip $jar
    $added=@($candidate.Keys | Where-Object { -not $base.ContainsKey($_) } | Sort-Object)
    $removed=@($base.Keys | Where-Object { -not $candidate.ContainsKey($_) } | Sort-Object)
    $changed=@($candidate.Keys | Where-Object { $base.ContainsKey($_) -and $candidate[$_].sha256 -cne $base[$_].sha256 } | Sort-Object)
    Same-Set $added @($guardMember) 'added JAR members'
    Same-Set $removed @() 'removed JAR members'
    Same-Set $changed @($slotMember,'META-INF/neoforge.mods.toml') 'changed JAR members'
    foreach ($entry in $compiled) { Assert-Audit ($candidate[$entry.name].sha256 -ceq $entry.sha256) "Packaged member differs from tested compilation: $($entry.name)" }
    foreach ($name in $candidate.Keys) {
        Assert-Audit (-not ($name -match '(?i)(^|/)(regression|storytest|guidetest|test|tests)(/|\.)|(?:Test|Tests|Checks)(?:\$[^/]*)?\.class$|useless_stretcher_test|org/(junit|mockito)/|net/bytebuddy/')) "Test member packaged: $name"
        Assert-Audit (-not $name.EndsWith('.jar',[StringComparison]::OrdinalIgnoreCase)) "Nested JAR packaged: $name"
        if ($name.EndsWith('.class')) { Assert-Audit ($name.StartsWith($prefix,[StringComparison]::Ordinal)) "Third-party class packaged: $name" }
    }
    $toml=Zip-Text $candidate 'META-INF/neoforge.mods.toml'; $baselineToml=Zip-Text $base 'META-INF/neoforge.mods.toml'
    $expectedToml=$baselineToml -replace '(?m)^version\s*=\s*"1\.21\.1-1\.6\.4-catalog-P4"',('version="'+$version+'"')
    Assert-Audit ($toml -ceq $expectedToml -and $toml -ceq (Get-Content -LiteralPath $metadata -Raw)) 'Metadata changed beyond the exact expected version or differs from hashed source.'
    $report.entryCounts=[ordered]@{baseline=$base.Count;candidate=$candidate.Count;unchanged=$base.Count-$changed.Count;added=$added.Count;changed=$changed.Count;removed=$removed.Count}
    $report.memberDiff=[ordered]@{added=@($added | ForEach-Object { [ordered]@{name=$_;sha256=$candidate[$_].sha256} });changed=@($changed | ForEach-Object { [ordered]@{name=$_;baselineSha256=$base[$_].sha256;sha256=$candidate[$_].sha256} });removed=$removed}

    # Standalone ASM helper reads classfiles without loading Minecraft or mod classes.
    $helperDirectory=Join-Path $runDirectory 'static-helper'; New-Item -ItemType Directory -Path $helperDirectory -Force | Out-Null
    $helperSource=@'
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
public final class AssemblyPlacementAudit {
  static final String SLOT="com/sorrowmist/useless/stretcher/mixin/InstalledStretcherSlotMixin";
  static final String GUARD="com/sorrowmist/useless/stretcher/content/ae/InstalledStretcherPlacementGuard";
  static final String TARGET="com/sorrowmist/useless/inventory/slot/PatternSlotItemHandler";
  static final String ITEM="Lnet/minecraft/world/item/ItemStack;";
  static final String GUARD_DESC="(Lnet/neoforged/neoforge/items/SlotItemHandler;"+ITEM+")Ljava/lang/Boolean;";
  static void require(boolean pass,String message) { if(!pass) throw new IllegalStateException(message); }
  static ClassNode read(String jar,String name) throws Exception {
    try(ZipFile zip=new ZipFile(jar)) { var entry=zip.getEntry(name+".class");require(entry!=null,"Missing "+name);var node=new ClassNode();try(var in=zip.getInputStream(entry)){new ClassReader(in).accept(node,0);}return node; }
  }
  static AnnotationNode annotation(List<AnnotationNode> nodes,String desc) {
    var found=nodes==null?List.<AnnotationNode>of():nodes.stream().filter(a->a.desc.equals(desc)).toList();require(found.size()==1,"Expected exactly one annotation "+desc);return found.get(0);
  }
  static Object value(AnnotationNode annotation,String key) { for(int i=0;i<annotation.values.size();i+=2)if(annotation.values.get(i).equals(key))return annotation.values.get(i+1);return null; }
  static List<AnnotationNode> all(List<AnnotationNode> a,List<AnnotationNode> b){var result=new ArrayList<AnnotationNode>();if(a!=null)result.addAll(a);if(b!=null)result.addAll(b);return result;}
  public static void main(String[] args) throws Exception {
    ClassNode slot=read(args[0],SLOT),guard=read(args[0],GUARD);
    var mixin=annotation(all(slot.visibleAnnotations,slot.invisibleAnnotations),"Lorg/spongepowered/asm/mixin/Mixin;");
    require(value(mixin,"value") instanceof List<?> targets&&targets.size()==1&&targets.get(0).equals(Type.getObjectType(TARGET)),"Mixin target differs");
    var handlers=slot.methods.stream().filter(m->m.name.equals("uselessStretcher$accept")).toList();require(handlers.size()==1,"Unique handler missing");var handler=handlers.get(0);
    require(handler.desc.equals("("+ITEM+"Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V"),"Handler descriptor differs");
    var inject=annotation(all(handler.visibleAnnotations,handler.invisibleAnnotations),"Lorg/spongepowered/asm/mixin/injection/Inject;");
    require(value(inject,"method") instanceof List<?> methods&&methods.equals(List.of("mayPlace")),"Inject method differs");
    require(Boolean.TRUE.equals(value(inject,"cancellable")),"Inject must be cancellable");
    require(value(inject,"at") instanceof List<?> ats&&ats.size()==1&&ats.get(0) instanceof AnnotationNode at&&"HEAD".equals(value(at,"value")),"Inject must be exactly HEAD");
    int calls=0;for(var insn:handler.instructions)if(insn instanceof MethodInsnNode m&&m.owner.equals(GUARD)&&m.name.equals("mayPlace")){require(m.getOpcode()==Opcodes.INVOKESTATIC&&m.desc.equals(GUARD_DESC),"Guard call ABI differs");calls++;}require(calls==1,"Expected exactly one guard delegation");
    var guards=guard.methods.stream().filter(m->m.name.equals("mayPlace")).toList();require(guards.size()==1&&guards.get(0).desc.equals(GUARD_DESC)&&(guards.get(0).access&(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC))==(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC),"Public static guard ABI differs");
    System.out.println("candidate\tHEAD cancellable inject and guard ABI\tPASS");
    for(int i=1;i<args.length;i++){var target=read(args[i],TARGET);var methods=target.methods.stream().filter(m->m.name.equals("mayPlace")).toList();require(methods.size()==1&&methods.get(0).desc.equals("("+ITEM+")Z"),"Native mayPlace must be unique with exact ItemStack->boolean ABI");System.out.println(args[i]+"\tunique mayPlace(ItemStack)boolean\tPASS");}
  }
}
'@
    $helperFile=Join-Path $helperDirectory 'AssemblyPlacementAudit.java'
    [IO.File]::WriteAllText($helperFile,$helperSource,[Text.UTF8Encoding]::new($false))
    $asmRoot=Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1\org.ow2.asm'
    $asmCore=Get-ChildItem -LiteralPath (Join-Path $asmRoot 'asm\9.9.1') -Recurse -Filter 'asm-9.9.1.jar' | Select-Object -First 1
    $asmTree=Get-ChildItem -LiteralPath (Join-Path $asmRoot 'asm-tree\9.9.1') -Recurse -Filter 'asm-tree-9.9.1.jar' | Select-Object -First 1
    Assert-Audit ($null -ne $asmCore -and $null -ne $asmTree) 'Standalone ASM dependencies not available.'
    $asmClasspath=$asmCore.FullName+[IO.Path]::PathSeparator+$asmTree.FullName
    & javac "-J-Djava.io.tmpdir=$helperDirectory" -encoding UTF-8 --release 21 -cp $asmClasspath -d $helperDirectory $helperFile
    Assert-Audit ($LASTEXITCODE -eq 0) 'Standalone ASM helper compilation failed.'
    $staticOutput=& java "-Djava.io.tmpdir=$helperDirectory" -Xmx128m -cp ($helperDirectory+[IO.Path]::PathSeparator+$asmClasspath) AssemblyPlacementAudit $jar $paths.native246 $paths.native2462 2>&1
    $staticExit=$LASTEXITCODE; $staticOutput | Set-Content -LiteralPath (Join-Path $runDirectory 'mixin-target-audit.tsv') -Encoding utf8
    Assert-Audit ($staticExit -eq 0 -and @($staticOutput).Count -eq 3) "Standalone Mixin target/ABI verification failed: $staticOutput"
    foreach ($label in @('native246','native2462')) {
        $javapOutput=& javap -p -s -classpath $paths[$label] 'com.sorrowmist.useless.inventory.slot.PatternSlotItemHandler' 2>&1
        $javapExit=$LASTEXITCODE; $javapOutput | Set-Content -LiteralPath (Join-Path $runDirectory ($label+'-PatternSlotItemHandler.javap.txt')) -Encoding utf8
        Assert-Audit ($javapExit -eq 0) "Native javap evidence failed: $label"
    }
    $after=[ordered]@{}
    foreach ($name in @($paths.Keys)) { $after[$name]=File-Hash $paths[$name];Assert-Audit ($after[$name] -ceq $before[$name]) "Reference changed during audit: $name" }
    $after.candidate=File-Hash $jar; Assert-Audit ($after.candidate -ceq $before.candidate) 'Candidate changed during audit.'
    $report.hashesAfter=$after; $report.tests=$tests; $report.expectedTests=$ExpectedTests; $report.testLogSha256=$testLogHash
    $report.compiledClasses=$compiled; $report.testCompiledClassCount=@($compiledTests).Count; $report.staticChecks=@($staticOutput)
    $report.buildResult=$BuildResult; $report.buildResultSha256=File-Hash $BuildResult; $report.auditToolSha256=File-Hash $PSCommandPath
    $report.inputRecordSha256=File-Hash $inputRecord; $report.performancePassed=$false; $report.runtimeMixinApplied=$false
    $report.pending='Real game menu click/shift/drag swap paths, persistence and full-pack runtime validation remain untested.'
    $report.status='FROZEN_OFFLINE_CHECKS_PASSED'
} catch {
    $report.status='FAIL'; $report.failure=$_.Exception.Message
} finally {
    $reportPath=Join-Path $runDirectory 'assembly-lock-audit-report.json'
    $report | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $reportPath -Encoding utf8
}
Write-Output ("{0}: {1}" -f $report.status,$reportPath)
if ($report.status -cne 'FROZEN_OFFLINE_CHECKS_PASSED') { Write-Output $report.failure; exit 1 }
$delivery=Split-Path $jar -Parent
$manifest=[ordered]@{
    status=$report.status;timestampUtc=$report.timestampUtc;gameLaunched=$false;version=$version;jar=$jar;sha256=$before.candidate;size=$build.size
    tests=$tests;testLogSha256=$testLogHash;entryCounts=$report.entryCounts;memberDiff=$report.memberDiff
    baselineSha256=$expectedHashes.baselineP4;referenceHashes=$after;compiledClasses=$compiled
    auditReport=$reportPath;auditReportSha256=(File-Hash $reportPath);auditToolSha256=(File-Hash $PSCommandPath)
    buildResult=$BuildResult;buildResultSha256=(File-Hash $BuildResult);performancePassed=$false;runtimeMixinApplied=$false;pending=$report.pending
}
$manifestPath=Join-Path $runDirectory 'assembly-lock-freeze-manifest.json'
$manifest | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $manifestPath -Encoding utf8
Copy-Item -LiteralPath $manifestPath -Destination (Join-Path $delivery 'assembly-lock-freeze-manifest.json')
$report.memberDiff | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $delivery 'assembly-lock-members-vs-P4.json') -Encoding utf8
[ordered]@{status=$report.status;jar=$jar;sha256=$before.candidate;size=$build.size;tests=$tests;entryCounts=$report.entryCounts;manifest=(Join-Path $delivery 'assembly-lock-freeze-manifest.json')} | ConvertTo-Json -Depth 6
