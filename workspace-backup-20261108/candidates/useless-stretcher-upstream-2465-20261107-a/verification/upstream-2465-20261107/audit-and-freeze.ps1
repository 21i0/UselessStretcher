[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidateRange(1,2147483647)][int]$ExpectedChecks,
    [string]$BuildResult=(Join-Path $PSScriptRoot 'build-result.json')
)
# File/ZIP/standalone ASM verification only. No game or full regression launch.
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$workspace=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path.TrimEnd('\')
if ([IO.Path]::GetPathRoot($workspace) -ine 'D:\') {throw 'All outputs must remain on D:.'}
$version='1.21.1-1.6.4-catalog-P4-assembly-lock-zh-hud-base2465-jei1970'
$prefix='com/sorrowmist/useless/stretcher/'
$catalog=$prefix+'content/catalog/'
$budgeted='BudgetedRecipeCatalog'
$family=@('BudgetedRecipeCatalog.class','BudgetedRecipeCatalog$Build.class','BudgetedRecipeCatalog$Collected.class','BudgetedRecipeCatalog$Adapter.class','BudgetedRecipeCatalog$Stage.class','BudgetedRecipeCatalog$PendingRequest.class','BudgetedRecipeCatalog$Timing.class','BudgetedRecipeCatalog$ClientOwner.class','BudgetedRecipeCatalog$Status.class') | ForEach-Object {$catalog+$_}
$business=@(($catalog+'BudgetedRecipeCatalog.class'),($catalog+'BudgetedRecipeCatalog$Build.class'),($catalog+'CatalogPatchSupport.class'),($prefix+'mixin/UselessStretcherMixinPlugin.class'))
$debugMembers=@($family | Where-Object {$_ -cnotin $business})
$newMembers=@('NativeAdapterGuards.class','NativeAdapterGuards$Native.class') | ForEach-Object {$catalog+$_}
$compiledExpected=@($family)+@(($catalog+'CatalogPatchSupport.class'),($prefix+'mixin/UselessStretcherMixinPlugin.class'))+@($newMembers)
$references=[ordered]@{
    baseline='D:\AI\candidates\useless-stretcher-jei-hud-layout-20261107-a\delivery\useless_stretcher-1.21.1-1.6.4-catalog-P4-assembly-lock-zh-hud.jar'
    assemblyLock='D:\AI\candidates\useless-stretcher-assembly-lock-20261107-a\delivery\useless_stretcher-1.21.1-1.6.4-catalog-P4-assembly-lock.jar'
    p4='D:\AI\candidates\useless-stretcher-catalog-P4-20261107-a\delivery\useless_stretcher-1.21.1-1.6.4-catalog-P4.jar'
    p3='D:\AI\candidates\useless-stretcher-catalog-P3-20261107-a\delivery\useless_stretcher-1.21.1-1.6.4-catalog-P3.jar'
    published163='D:\AI\useless_stretcher-1.21.1-1.6.3.jar'
}
$referenceHashes=@{
    baseline='E0E15336F1FFA8D1F9AC1839B7A29509BB2D6ECDC03C502AE92B1B4251048FB6'
    assemblyLock='28317D3E41034B13E1EFFCB75C5C76E71CEFD29AC60437E089DF4E4D5F0A495F'
    p4='9C57CBB3B1343C8837B773003AC906C630378786A784B7490F1F4E943A5021FC'
    p3='0D2CCCBD03E95EEC6AC1688F00028372D92313D90189BA8B5EBCA4DC3EE6832A'
    published163='00A10F69575B941C6A7545D28B43749362CB02FC9E30288ABC271520537B5BFC'
}
$runDirectory=Join-Path $PSScriptRoot ('audit-runs\'+[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmssfff'))
New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null
$report=[ordered]@{status='RUNNING';timestampUtc=[DateTime]::UtcNow.ToString('o');gameLaunched=$false;version=$version;full184SuiteRerun=$false}
function Require([bool]$Pass,[string]$Message) { if (-not $Pass) { throw $Message } }
function Local([string]$Path) {
    $full=[IO.Path]::GetFullPath($Path)
    Require ($full.StartsWith($script:workspace+'\',[StringComparison]::OrdinalIgnoreCase)) "Path escapes candidate workspace: $full"
    return $full
}
function Hash([string]$Path) { return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash }
function Exact-Set($Actual,$Expected,[string]$Purpose) {
    $a=@($Actual);$e=@($Expected)
    Require ($a.Count -eq @($a | Select-Object -Unique).Count) "Duplicate $Purpose"
    Require ($a.Count -eq $e.Count -and (@($a | Sort-Object)-join "`n") -ceq (@($e | Sort-Object)-join "`n")) "Unexpected $Purpose"
}
function Zip([string]$Path) {
    $items=@{};$archive=[IO.Compression.ZipFile]::OpenRead($Path)
    try { foreach ($entry in $archive.Entries) {
        if ($entry.FullName.EndsWith('/')) { continue }
        Require (-not $items.ContainsKey($entry.FullName)) "Duplicate ZIP member: $($entry.FullName)"
        $stream=$entry.Open();$memory=[IO.MemoryStream]::new();$hash=[Security.Cryptography.SHA256]::Create()
        try {$stream.CopyTo($memory);$bytes=$memory.ToArray();$items[$entry.FullName]=[pscustomobject]@{bytes=$bytes;sha256=[Convert]::ToHexString($hash.ComputeHash($bytes));size=$bytes.Length}}
        finally {$stream.Dispose();$memory.Dispose();$hash.Dispose()}
    }} finally {$archive.Dispose()}
    return ,$items
}
function Zip-Text($Archive,[string]$Member) { Require ($Archive.ContainsKey($Member)) "Missing member: $Member";return [Text.Encoding]::UTF8.GetString($Archive[$Member].bytes) }
function Language([string]$Json) {
    $values=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
    $document=[Text.Json.JsonDocument]::Parse($Json)
    try {
        Require ($document.RootElement.ValueKind -eq [Text.Json.JsonValueKind]::Object) 'Language JSON must be an object.'
        foreach ($property in $document.RootElement.EnumerateObject()) {
            Require (-not $values.ContainsKey($property.Name)) "Duplicate language key: $($property.Name)"
            Require ($property.Value.ValueKind -eq [Text.Json.JsonValueKind]::String) "Language value must be a string: $($property.Name)"
            $values.Add($property.Name,$property.Value.GetString())
        }
    } finally {$document.Dispose()}
    return ,$values
}
function Args([string]$Path) { return @(Get-Content -LiteralPath (Local $Path) | ForEach-Object {$_.Trim('"').Replace('/','\')}) }
function Compiler-Args($Arguments,$Sources,[string]$Destination) {
    $a=@($Arguments);$index=[Array]::IndexOf($a,'-d')
    Require ($index -ge 0 -and $a[$index+1] -ceq $Destination) 'Compiler output differs from build record.'
    Exact-Set @($a | Where-Object {$_.EndsWith('.java',[StringComparison]::OrdinalIgnoreCase)}) $Sources 'compiler sources'
    Require ($a -ccontains '-proc:none') 'Unexpected annotation processing.'
}
function Classes($Recorded,[string]$Directory) {
    $actual=@(Get-ChildItem -LiteralPath $Directory -Filter '*.class' -Recurse | ForEach-Object {[pscustomobject]@{name=$_.FullName.Substring($Directory.Length+1).Replace('\','/');sha256=(Hash $_.FullName)}})
    Exact-Set @($Recorded | ForEach-Object name) @($actual | ForEach-Object name) 'compiled class inventory'
    foreach ($entry in $actual) {$record=@($Recorded | Where-Object name -CEQ $entry.name);Require ($record.Count -eq 1 -and $record[0].sha256 -ceq $entry.sha256) "Compiled class changed: $($entry.name)"}
    return @($actual)
}
try {
    $BuildResult=Local $BuildResult;$build=Get-Content -LiteralPath $BuildResult -Raw | ConvertFrom-Json
    Require ($build.gameLaunched -eq $false -and $build.full184SuiteRerun -eq $false -and $build.compilerExitCode -eq 0 -and $build.checkCompilerExitCode -eq 0) 'Expected successful offline compilation without full regression.'
    $jar=Local $build.jar;$classes=Local $build.classesDirectory;$checksDirectory=Local $build.checkClassesDirectory
    Require ($classes -cne $checksDirectory) 'Production and focused check classes overlap.'
    $before=[ordered]@{};foreach ($name in @($references.Keys)) {$before[$name]=Hash $references[$name];Require ($before[$name] -ceq $referenceHashes[$name]) "Frozen reference changed: $name"}
    $before.candidate=Hash $jar
    Require ($before.candidate -ceq $build.sha256 -and (Get-Item -LiteralPath $jar).Length -eq $build.size) 'Candidate changed after build.'
    Require ($build.baselineSha256 -ceq $referenceHashes.baseline) 'Build did not use precise HUD baseline.'
    $mainExpected=@('content/catalog/BudgetedRecipeCatalog.java','content/catalog/NativeAdapterGuards.java','content/catalog/CatalogPatchSupport.java','mixin/UselessStretcherMixinPlugin.java') | ForEach-Object {Join-Path $workspace ('src/main/java/com/sorrowmist/useless/stretcher/'+$_)}
    Exact-Set @(Get-ChildItem -LiteralPath (Join-Path $workspace 'src/main/java') -Filter '*.java' -Recurse | ForEach-Object FullName) $mainExpected 'production source files'
    Exact-Set @($build.mainSources | ForEach-Object path) $mainExpected 'recorded production sources'
    $checkExpected=@((Join-Path $PSScriptRoot 'NativeAdapterGuardChecks.java'),'D:\AI\candidates\useless-stretcher-upstream-abi-20261107-a\verification\upstream-abi\VersionGateChecks.java')
    Exact-Set @($build.checkSources | ForEach-Object path) $checkExpected 'focused check source files'
    Require (@($build.inputs).Count -eq @($build.inputs | ForEach-Object path | Select-Object -Unique).Count) 'Duplicate build inputs.'
    foreach ($input in @($build.inputs)+@($build.mainSources)+@($build.checkSources)) {
        Require ((Hash $input.path) -ceq $input.sha256) "Build input changed: $($input.path)"
        $source=@($build.inputs | Where-Object path -CEQ $input.path);Require ($source.Count -eq 1 -and $source[0].sha256 -ceq $input.sha256) 'Source/input record mismatch.'
    }
    $inputsBefore=Join-Path (Split-Path $classes -Parent) 'inputs-before-build.json'
    Require (((Get-Content -LiteralPath $inputsBefore -Raw | ConvertFrom-Json | ConvertTo-Json -Depth 8 -Compress)) -ceq ($build.inputs | ConvertTo-Json -Depth 8 -Compress)) 'Build input snapshot differs from final record.'
    Compiler-Args (Args $build.mainCompilerArguments) $mainExpected $classes
    Compiler-Args (Args $build.checkCompilerArguments) $checkExpected $checksDirectory
    foreach ($pair in @(@('mainCompilerArguments','mainCompilerArgumentsSha256'),@('checkCompilerArguments','checkCompilerArgumentsSha256'),@('mainCompilerLog','mainCompilerLogSha256'),@('checkCompilerLog','checkCompilerLogSha256'),@('classpath','classpathSha256'),@('checkClasspath','checkClasspathSha256'))) {Require ((Hash (Local $build.($pair[0]))) -ceq $build.($pair[1])) "Build tool record changed: $($pair[0])"}
    $compiled=Classes $build.compiledClasses $classes;Exact-Set @($compiled | ForEach-Object name) $compiledExpected 'compiled production inventory'
    $compiledChecks=Classes $build.checkCompiledClasses $checksDirectory
    $checkMembers=@('content/catalog/NativeAdapterGuardChecks.class','content/catalog/NativeAdapterGuardChecks$1.class','content/catalog/NativeAdapterGuardChecks$OtherMarker.class','content/catalog/NativeAdapterGuardChecks$Throwing.class','verification/VersionGateChecks.class','verification/VersionGateChecks$Checked.class','verification/VersionGateChecks$LoaderFixture.class','verification/VersionGateChecks$Route.class') | ForEach-Object {$prefix+$_}
    Exact-Set @($compiledChecks | ForEach-Object name) $checkMembers 'compiled focused checks'
    $expectedDeps=@{base='84F957C02DFAC63DD3D9F3B269F59CB9704CC228725E9B044AE54C98718BDE31';jei='097CDFAE99B208DFB94F8C55E813AF10E55688ECF4E42116FED1B7E26F0F4FCF';eaep='1BFE8540493DD73C6DB106937209ABC6791F5D2E0E81A2BE478CC8B337E77237';data='5FC69019F1068C9C333EFAEBF0D7F731DD663C38245A104AD597D91003299367';ae2='DF15A07F86CA1CA93AA66373F9D5D84DD3D4586631FCEB38F4FBC85DAD591E39';appflux='3FA19F02317A795334805D67318708C5025911B55C24AE273125F1A0652311B8';ldlib2='301E115072E99B69BC8E9B7BC55C3093BE66E92D275ACFB62A3EDC9831D28ADC';ae2wtlib='407E7300351353CB4CB7698286E180AEF2BD6E7A97EC7EFC6493F9DAA4DBFFE5'}
    Exact-Set @($build.dependencies | ForEach-Object id) @($expectedDeps.Keys) 'official dependency ids'
    foreach ($dependency in $build.dependencies) {Require ($dependency.sha256 -ceq $expectedDeps[$dependency.id] -and (Hash $dependency.path) -ceq $expectedDeps[$dependency.id]) "Official dependency changed: $($dependency.id)"}
    $newBase=@($build.dependencies | Where-Object id -CEQ 'base')[0]
    $checksPlan=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'checks.json') -Raw | ConvertFrom-Json
    Exact-Set @($build.checks | ForEach-Object id) @('version-gates','native-guards','legacy-guards') 'focused check receipts'
    $expectedCounts=@{'version-gates'=127;'native-guards'=14;'legacy-guards'=6}
    $providers=@{};$total=0;$receipts=@()
    foreach ($receipt in $build.checks) {
        Require ($receipt.exitCode -eq 0) "Focused check failed: $($receipt.id)"
        foreach ($pair in @(@('arguments','argumentsSha256'),@('runtimeClasspath','runtimeClasspathSha256'),@('log','logSha256'),@('result','resultSha256'))) {Require ((Hash (Local $receipt.($pair[0]))) -ceq $receipt.($pair[1])) "Focused artifact changed: $($receipt.id) $($pair[0])"}
        $result=Get-Content -LiteralPath $receipt.result -Raw | ConvertFrom-Json
        if ($receipt.id -ceq 'version-gates') {
            Require ($result.status -ceq 'PASSED' -and $result.passed -eq 127 -and $result.failed -eq 0 -and @($result.checks).Count -eq 127 -and @($result.checks | Where-Object status -CEQ 'PASSED').Count -eq 127) 'Version gate result rows failed.'
        } else {Require ($result.status -ceq 'PASS' -and $result.gameLaunched -eq $false -and $result.checksFound -eq $expectedCounts[$receipt.id] -and $result.checksStarted -eq $result.checksFound -and $result.checksPassed -eq $result.checksFound -and $result.checksFailed -eq 0) "Guard result failed: $($receipt.id)"}
        Require ($receipt.checksFound -eq $expectedCounts[$receipt.id] -and $receipt.checksPassed -eq $receipt.checksFound -and $receipt.checksStarted -eq $receipt.checksFound -and $receipt.checksFailed -eq 0) 'Receipt count differs from actual result.'
        $a=Args $receipt.arguments;$cpIndex=[Array]::IndexOf($a,'-cp');Require ($cpIndex -ge 0) 'No focused runtime classpath.'
        $cp=@($a[$cpIndex+1].Split(';'));Require ($cp[0] -ceq $checksDirectory -and $cp[1] -ceq $classes -and $cp[2] -ceq $references.baseline) 'Focused checks did not prioritize checked output and frozen baseline.'
        Require (([IO.File]::ReadAllText($receipt.runtimeClasspath)).Replace('/','\') -ceq $a[$cpIndex+1]) 'Runtime classpath record and Java arguments differ.'
        $plan=@($checksPlan | Where-Object id -CEQ $receipt.id)[0]
        $tail=@($plan.main)+@($plan.arguments | ForEach-Object {$_.Replace('{result}',$receipt.result)})
        Require ((@($a[($cpIndex+2)..($a.Count-1)]) -join "`n") -ceq (@($tail) -join "`n")) 'Focused main and ordered arguments differ from check plan.'
        $guardProviders=@();$catalogProviders=@()
        foreach ($entry in $cp | Select-Object -Unique) {
            if ([IO.Path]::GetExtension($entry) -ine '.jar') {continue}
            if (-not $providers.ContainsKey($entry)) {
                $probe=[IO.Compression.ZipFile]::OpenRead($entry)
                try {$providers[$entry]=[pscustomobject]@{guard=($null -ne $probe.GetEntry('com/sorrowmist/useless/content/recipe/AdapterGuards.class'));catalog=($null -ne $probe.GetEntry('com/sorrowmist/useless/content/recipe/AlloyFurnaceRecipeCatalog.class'))}}
                finally {$probe.Dispose()}
            }
            if ($providers[$entry].guard) {$guardProviders+=$entry};if ($providers[$entry].catalog) {$catalogProviders+=$entry}
        }
        if ($receipt.id -ceq 'legacy-guards') {
            Require ($guardProviders.Count -eq 0) 'Legacy check still had the new guard class on its classpath.'
            Require ($catalogProviders.Count -eq 1 -and $catalogProviders[0] -ceq $receipt.legacyBase.path -and (Hash $receipt.legacyBase.path) -ceq '9F6F54AF76AA255123B354C8725D55C2ACE4525B8683E1136ACAEBCCC8813B17') 'Legacy check did not run with exact real old base.'
        } else {Require ($guardProviders.Count -eq 1 -and $guardProviders[0] -ceq $newBase.path -and $catalogProviders.Count -eq 1 -and $catalogProviders[0] -ceq $newBase.path) 'New check classpath contains missing/shadow native base.'}
        $total+=$receipt.checksFound;$receipts+=$receipt
    }
    Require ($total -eq $ExpectedChecks -and $total -eq 147) 'Expected 147 focused checks, not a new full-regression result.'
    $base=Zip $references.baseline;$candidate=Zip $jar
    $added=@($candidate.Keys | Where-Object {-not $base.ContainsKey($_)} | Sort-Object);$removed=@($base.Keys | Where-Object {-not $candidate.ContainsKey($_)} | Sort-Object)
    $changed=@($candidate.Keys | Where-Object {$base.ContainsKey($_) -and $candidate[$_].sha256 -cne $base[$_].sha256} | Sort-Object)
    $debugChanged=@($changed | Where-Object {$_ -cin $debugMembers})
    Exact-Set $added $newMembers 'added production JAR members';Exact-Set $removed @() 'removed JAR members'
    Exact-Set $changed (@($business)+@('META-INF/neoforge.mods.toml')+@($debugChanged)) 'changed production JAR members'
    foreach ($entry in $compiled) {Require ($candidate[$entry.name].sha256 -ceq $entry.sha256) "JAR member differs from compiled/check-executed class: $($entry.name)"}
    foreach ($member in $candidate.Keys) {
        Require (-not ($member -match '(?i)(^|/)(regression|storytest|guidetest|test|tests)(/|\.)|(?:Test|Tests|Checks)(?:\$[^/]*)?\.class$|useless_stretcher_test|org/(junit|mockito)/|net/bytebuddy/')) "Test/check member packaged: $member"
        Require (-not $member.EndsWith('.jar',[StringComparison]::OrdinalIgnoreCase)) "Nested JAR packaged: $member"
        if ($member.EndsWith('.class')) {Require ($member.StartsWith($prefix,[StringComparison]::Ordinal)) "Third-party class packaged: $member"}
    }
    $metadata=Join-Path $workspace 'src/main/resources/META-INF/neoforge.mods.toml'
    $toml=Zip-Text $candidate 'META-INF/neoforge.mods.toml';$expectedToml=(Zip-Text $base 'META-INF/neoforge.mods.toml') -replace '(?m)^version\s*=\s*"1\.21\.1-1\.6\.4-catalog-P4-assembly-lock-zh-hud"',('version="'+$version+'"')
    Require ($toml -ceq $expectedToml -and $toml -ceq (Get-Content -LiteralPath $metadata -Raw)) 'Metadata changed beyond exact version or differs from input.'
    $helperDirectory=Join-Path $runDirectory 'static-helper';New-Item -ItemType Directory -Path $helperDirectory -Force | Out-Null
    $helperSource=@'
import java.util.*;import java.util.zip.*;import org.objectweb.asm.*;import org.objectweb.asm.tree.*;
public final class UpstreamCandidateAudit {
 static final String ROOT="com/sorrowmist/useless/stretcher/",NATIVE="com/sorrowmist/useless/content/recipe/AdapterGuards";
 static void require(boolean p,String m){if(!p)throw new IllegalStateException(m);}
 static ClassNode read(String jar,String name)throws Exception{try(ZipFile z=new ZipFile(jar)){var e=z.getEntry(name+".class");require(e!=null,"Missing "+name);var n=new ClassNode();try(var in=z.getInputStream(e)){new ClassReader(in).accept(n,0);}return n;}}
 static byte[] noDebug(ClassNode n){n.sourceFile=null;n.sourceDebug=null;for(var m:n.methods){m.localVariables=null;for(var i:m.instructions.toArray())if(i instanceof LineNumberNode)m.instructions.remove(i);}var w=new ClassWriter(0);n.accept(w);return w.toByteArray();}
 static void outside(String candidate,String baseline,String name,Set<String> methods)throws Exception{
  var c=read(candidate,ROOT+name);var b=read(baseline,ROOT+name);
  require(c.methods.removeIf(m->methods.contains(m.name))&&b.methods.removeIf(m->methods.contains(m.name)),"Allowed gate method missing");
  require(Arrays.equals(noDebug(c),noDebug(b)),"Class changed outside allowed gate methods: "+name);System.out.println(name+"\toutside-gate normalized equality\tPASS");
 }
 public static void main(String[] a)throws Exception {
  outside(a[0],a[1],"content/catalog/CatalogPatchSupport",Set.of("verifiedVersion","lambda$jeiPublicationEnabled$1"));
  outside(a[0],a[1],"mixin/UselessStretcherMixinPlugin",Set.of("shouldApplyMixin"));
  var direct=read(a[0],ROOT+"content/catalog/NativeAdapterGuards$Native");var target=read(a[2],NATIVE);int calls=0;
  var expected=Map.of("call","(Ljava/lang/Object;Ljava/lang/String;Ljava/util/function/Supplier;Ljava/lang/Object;)Ljava/lang/Object;","callEach","(Ljava/lang/Object;Ljava/lang/String;Ljava/util/function/Supplier;Ljava/lang/Object;)Ljava/lang/Object;","guard","(Ljava/lang/String;Ljava/util/function/Supplier;Ljava/lang/Object;)Ljava/lang/Object;","isDisabled","(Ljava/lang/Object;Ljava/lang/String;)Z","rememberSource","(Ljava/lang/Object;Ljava/lang/String;)V");
  for(var method:direct.methods)for(var node:method.instructions)if(node instanceof MethodInsnNode m&&m.owner.equals(NATIVE)){require(expected.containsKey(m.name)&&expected.get(m.name).equals(m.desc)&&m.getOpcode()==Opcodes.INVOKESTATIC,"Unexpected native guard invocation");var matches=target.methods.stream().filter(t->t.name.equals(m.name)&&t.desc.equals(m.desc)).toList();require(matches.size()==1&&(matches.get(0).access&(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC))==(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC),"Native guard target not unique public static");calls++;System.out.println("AdapterGuards."+m.name+m.desc+"\texact ABI\tPASS");}
  require(calls==5,"Expected five official native guard calls");
  for(int i=3;i<a.length;i++){String member=a[i];String name=member.substring(0,member.length()-6);require(Arrays.equals(noDebug(read(a[0],name)),noDebug(read(a[1],name))),"Inner changed beyond debug metadata: "+member);System.out.println(member+"\tdebug-only normalized equality\tPASS");}
 }
}
'@
    $helperFile=Join-Path $helperDirectory 'UpstreamCandidateAudit.java';[IO.File]::WriteAllText($helperFile,$helperSource,[Text.UTF8Encoding]::new($false))
    $asmRoot=Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1/org.ow2.asm'
    $asmCore=Get-ChildItem -LiteralPath (Join-Path $asmRoot 'asm/9.9.1') -Recurse -Filter 'asm-9.9.1.jar' | Select-Object -First 1
    $asmTree=Get-ChildItem -LiteralPath (Join-Path $asmRoot 'asm-tree/9.9.1') -Recurse -Filter 'asm-tree-9.9.1.jar' | Select-Object -First 1
    Require ($null -ne $asmCore -and $null -ne $asmTree) 'Standalone ASM dependency missing.'
    $asmClasspath=$asmCore.FullName+[IO.Path]::PathSeparator+$asmTree.FullName
    & javac "-J-Djava.io.tmpdir=$helperDirectory" -encoding UTF-8 --release 21 -cp $asmClasspath -d $helperDirectory $helperFile
    Require ($LASTEXITCODE -eq 0) 'Standalone ASM helper compilation failed.'
    $static=& java "-Djava.io.tmpdir=$helperDirectory" -Xmx128m -cp ($helperDirectory+[IO.Path]::PathSeparator+$asmClasspath) UpstreamCandidateAudit $jar $references.baseline $newBase.path @debugChanged 2>&1
    $staticExit=$LASTEXITCODE;$static | Set-Content -LiteralPath (Join-Path $runDirectory 'upstream-bytecode-audit.tsv') -Encoding utf8
    Require ($staticExit -eq 0 -and @($static).Count -eq (7+$debugChanged.Count)) "Upstream static audit failed: $static"
    $abiReport='D:\AI\candidates\useless-stretcher-upstream-abi-20261107-a\FINAL-COMBO-ABI-REPORT.json'
    $abiReceipt='D:\AI\candidates\useless-stretcher-upstream-abi-20261107-a\FINAL-COMBO-ABI-RECEIPT.json'
    Require ((Hash $abiReport) -ceq 'E0FEAACEA05441761E7B0E190A4A7777EA4758618C39C92C05F8A24F484AD9C9' -and (Hash $abiReceipt) -ceq 'CCD9206009E991D101B97A4A51C3E10B0801F3E0C30999DF28EA93FDC6339F40') 'Frozen external ABI evidence changed.'
    $abi=Get-Content -LiteralPath $abiReport -Raw | ConvertFrom-Json
    $abiFrozen=Get-Content -LiteralPath $abiReceipt -Raw | ConvertFrom-Json
    Require ($abi.status -ceq 'PASS_FOCUSED_STATIC_ABI_AND_EXACT_INJECTION_TARGETS' -and $abi.gameLaunched -eq $false -and $abi.inputs.candidate.sha256 -ceq $before.candidate -and $abiFrozen.candidate.sha256 -ceq $before.candidate -and $abiFrozen.baselineHud.sha256 -ceq $referenceHashes.baseline) 'External ABI evidence belongs to another artifact or failed.'
    foreach ($kind in @('native','jei','ae2')) {Require ($abi.memberResolution.$kind.status -ceq 'PASS' -and @($abi.memberResolution.$kind.unresolved).Count -eq 0) "Unresolved new-group members: $kind"}
    $externalAbi=[ordered]@{report=$abiReport;reportSha256=(Hash $abiReport);receipt=$abiReceipt;receiptSha256=(Hash $abiReceipt);nativeReferences=$abi.memberResolution.native.referenceOccurrences;jeiReferences=$abi.memberResolution.jei.referenceOccurrences;ae2References=$abi.memberResolution.ae2.referenceOccurrences}
    $after=[ordered]@{};foreach ($name in @($references.Keys)) {$after[$name]=Hash $references[$name];Require ($after[$name] -ceq $before[$name]) "Frozen reference changed: $name"}
    $after.candidate=Hash $jar;Require ($after.candidate -ceq $before.candidate) 'Candidate changed during audit.'
    $report.references=$references;$report.hashesBefore=$before;$report.hashesAfter=$after;$report.jar=$jar;$report.size=$build.size
    $report.checks=$receipts;$report.focusedCount=$total;$report.staticChecks=@($static);$report.debugOnlyInnerClasses=$debugChanged;$report.compiledClasses=$compiled;$report.dependencies=$build.dependencies;$report.externalAbi=$externalAbi
    $report.entryCounts=[ordered]@{baseline=$base.Count;candidate=$candidate.Count;unchanged=$base.Count-$changed.Count;added=$added.Count;changed=$changed.Count;removed=$removed.Count}
    $report.memberDiff=[ordered]@{added=@($added | ForEach-Object {[ordered]@{name=$_;sha256=$candidate[$_].sha256}});changed=@($changed | ForEach-Object {[ordered]@{name=$_;oldSha256=$base[$_].sha256;sha256=$candidate[$_].sha256;debugOnly=($_ -cin $debugChanged)}});removed=$removed}
    $report.buildResult=$BuildResult;$report.buildResultSha256=Hash $BuildResult;$report.auditToolSha256=Hash $PSCommandPath
    $report.performancePassed=$false;$report.runtimeMixinApplied=$false;$report.pending='New full-pack loading, actual Mixin application and gameplay/performance validation remain pending; no new full 184-test regression.'
    $report.status='FROZEN_FOCUSED_OFFLINE_CHECKS_PASSED'
} catch {$report.status='FAIL';$report.failure=$_.Exception.Message}
finally {$reportPath=Join-Path $runDirectory 'upstream-candidate-audit-report.json';$report | ConvertTo-Json -Depth 14 | Set-Content -LiteralPath $reportPath -Encoding utf8}
Write-Output ("{0}: {1}" -f $report.status,$reportPath)
if ($report.status -cne 'FROZEN_FOCUSED_OFFLINE_CHECKS_PASSED') {Write-Output $report.failure;exit 1}
$manifest=[ordered]@{status=$report.status;timestampUtc=$report.timestampUtc;jar=$jar;sha256=$before.candidate;size=$build.size;version=$version;baselineSha256=$referenceHashes.baseline;gameLaunched=$false;full184SuiteRerun=$false;focusedCount=$total;checks=$receipts;dependencies=$build.dependencies;entryCounts=$report.entryCounts;memberDiff=$report.memberDiff;debugOnlyInnerClasses=$debugChanged;externalAbi=$externalAbi;auditReport=$reportPath;auditReportSha256=(Hash $reportPath);auditToolSha256=(Hash $PSCommandPath);performancePassed=$false;runtimeMixinApplied=$false;pending=$report.pending}
$manifestPath=Join-Path $runDirectory 'upstream-freeze-manifest.json';$manifest | ConvertTo-Json -Depth 14 | Set-Content -LiteralPath $manifestPath -Encoding utf8
$delivery=Split-Path $jar -Parent;Copy-Item -LiteralPath $manifestPath -Destination (Join-Path $delivery 'upstream-freeze-manifest.json')
$report.memberDiff | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $delivery 'upstream-members-vs-HUD.json') -Encoding utf8
[ordered]@{status=$report.status;jar=$jar;sha256=$before.candidate;size=$build.size;focusedChecks=$total;full184SuiteRerun=$false;entryCounts=$report.entryCounts;debugOnlyInnerClasses=$debugChanged} | ConvertTo-Json -Depth 6

