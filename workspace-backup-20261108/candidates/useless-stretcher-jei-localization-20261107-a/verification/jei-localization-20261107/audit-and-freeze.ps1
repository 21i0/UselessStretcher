[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidateRange(1,2147483647)][int]$ExpectedChecks,
    [string]$BuildResult=(Join-Path $PSScriptRoot 'build-result.json')
)
# Standalone file/ZIP/ASM audit only. Does not start Minecraft or run the 184-test suite.
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$workspace=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path.TrimEnd('\')
if ([IO.Path]::GetPathRoot($workspace) -ine 'D:\') { throw 'Audit outputs must remain on D:.' }
$version='1.21.1-1.6.4-catalog-P4-assembly-lock-zh'
$package='com/sorrowmist/useless/stretcher/client/'
$outer=@('CatalogJeiPublication.class','ClientCatalogLifecycle.class') | ForEach-Object {$package+$_}
$inner=@('CatalogJeiPublication$1.class','CatalogJeiPublication$RuntimeChangedAfterCall.class','CatalogJeiPublication$RuntimeFailure.class','CatalogJeiPublication$StaleRevision.class') | ForEach-Object {$package+$_}
$compiledExpected=@($outer)+@($inner)
$languageMembers=@('assets/useless_stretcher/lang/zh_cn.json','assets/useless_stretcher/lang/en_us.json')
$changedExpected=@($outer)+@($languageMembers)+@('META-INF/neoforge.mods.toml')
$languagePrefix='gui.useless_stretcher.jei_publication.'
$newKeys=@('waiting_runtime','waiting_catalog','preparing','organizing','updating','adding','restoring','finishing','ready','stopped') | ForEach-Object { $languagePrefix+$_ }
$references=[ordered]@{
    baseline='D:\AI\candidates\useless-stretcher-assembly-lock-20261107-a\delivery\useless_stretcher-1.21.1-1.6.4-catalog-P4-assembly-lock.jar'
    p4='D:\AI\candidates\useless-stretcher-catalog-P4-20261107-a\delivery\useless_stretcher-1.21.1-1.6.4-catalog-P4.jar'
    p3='D:\AI\candidates\useless-stretcher-catalog-P3-20261107-a\delivery\useless_stretcher-1.21.1-1.6.4-catalog-P3.jar'
    published163='D:\AI\useless_stretcher-1.21.1-1.6.3.jar'
}
$referenceHashes=@{
    baseline='28317D3E41034B13E1EFFCB75C5C76E71CEFD29AC60437E089DF4E4D5F0A495F'
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
    Require ($build.gameLaunched -eq $false -and $build.compilerExitCode -eq 0 -and $build.checkCompilerExitCode -eq 0 -and $build.checkExitCode -eq 0) 'Expected successful offline production compilation and focused checks.'
    Require ($null -eq $build.testSources -and $null -eq $build.testExitCode) 'Do not associate a new full-suite test result with this focused-only build.'
    $jar=Local $build.jar;$classes=Local $build.classesDirectory;$checksDirectory=Local $build.checkClassesDirectory
    Require ($classes -cne $checksDirectory) 'Production and focused-check class directories overlap.'
    $before=[ordered]@{}
    foreach ($name in @($references.Keys)) {$before[$name]=Hash $references[$name];Require ($before[$name] -ceq $referenceHashes[$name]) "Frozen reference differs: $name"}
    $before.candidate=Hash $jar
    Require ($before.candidate -ceq $build.sha256 -and (Get-Item -LiteralPath $jar).Length -eq $build.size) 'Candidate changed after build.'
    Require ($build.baselineSha256 -ceq $referenceHashes.baseline) 'Build did not use the exact frozen P4-lock baseline.'
    $mainExpected=@((Join-Path $workspace 'src\main\java\com\sorrowmist\useless\stretcher\client\CatalogJeiPublication.java'),(Join-Path $workspace 'src\main\java\com\sorrowmist\useless\stretcher\client\ClientCatalogLifecycle.java'))
    Exact-Set @(Get-ChildItem -LiteralPath (Join-Path $workspace 'src\main\java') -Filter '*.java' -Recurse | ForEach-Object FullName) $mainExpected 'production source inventory'
    Exact-Set @($build.mainSources | ForEach-Object path) $mainExpected 'recorded production sources'
    $checkExpected=@((Join-Path $PSScriptRoot 'JeiLocalizationChecks.java'))
    Exact-Set @($build.checkSources | ForEach-Object path) $checkExpected 'focused-check sources'
    $resources=@($languageMembers | ForEach-Object {Join-Path $workspace ('src/main/resources/'+$_)})+@((Join-Path $workspace 'src\main\resources\META-INF\neoforge.mods.toml'))
    Exact-Set @($build.inputs | ForEach-Object path) (@($mainExpected)+@($checkExpected)+@($resources)) 'build input inventory'
    foreach ($source in @($build.inputs)+@($build.mainSources)+@($build.checkSources)) {
        Require ((Hash (Local $source.path)) -ceq $source.sha256) "Input changed after checks: $($source.path)"
        $record=@($build.inputs | Where-Object path -CEQ $source.path);Require ($record.Count -eq 1 -and $record[0].sha256 -ceq $source.sha256) 'Input/source hash disagreement.'
    }
    $inputsBefore=Join-Path (Split-Path $classes -Parent) 'inputs-before-build.json'
    Require (((Get-Content -LiteralPath $inputsBefore -Raw | ConvertFrom-Json | ConvertTo-Json -Depth 6 -Compress)) -ceq ($build.inputs | ConvertTo-Json -Depth 6 -Compress)) 'Inputs-before-build differ from checked source record.'
    Compiler-Args (Args $build.mainCompilerArguments) $mainExpected $classes
    Compiler-Args (Args $build.checkCompilerArguments) $checkExpected $checksDirectory
    $runArgs=Args $build.checkArguments;$cpIndex=[Array]::IndexOf($runArgs,'-cp')
    Require ($cpIndex -ge 0) 'No focused-check runtime classpath.'
    $cp=@($runArgs[$cpIndex+1].Split(';'));Require ($cp[0] -ceq $classes -and $cp[1] -ceq $checksDirectory) 'Focused checks did not prioritize newly compiled production/check outputs.'
    $checkResult=Local $build.checkResult;$checkLog=Local $build.checkLog
    Require ($runArgs[-4] -ceq 'com.sorrowmist.useless.stretcher.client.JeiLocalizationChecks') 'Unexpected focused-check main class.'
    Require ($runArgs[-3] -ceq $resources[0] -and $runArgs[-2] -ceq $resources[1] -and $runArgs[-1] -ceq $checkResult) 'Focused checks used different language resources or result path.'
    Require ((Hash $checkResult) -ceq $build.checkResultSha256 -and (Hash $checkLog) -ceq $build.checkLogSha256) 'Focused check result or log changed.'
    $check=Get-Content -LiteralPath $checkResult -Raw | ConvertFrom-Json
    Require ($check.status -ceq 'PASS' -and $check.gameLaunched -eq $false -and $check.checksFound -eq $ExpectedChecks -and $check.checksStarted -eq $ExpectedChecks -and $check.checksPassed -eq $ExpectedChecks -and $check.checksFailed -eq 0) 'Focused checks did not execute and pass the expected total.'
    $compiled=Classes $build.compiledClasses $classes;Exact-Set @($compiled | ForEach-Object name) $compiledExpected 'production compiled members'
    $compiledChecks=Classes $build.checkCompiledClasses $checksDirectory
    $checkMembers=@('JeiLocalizationChecks.class','JeiLocalizationChecks$1.class','JeiLocalizationChecks$TableLanguage.class','JeiLocalizationChecks$Throwing.class') | ForEach-Object {$package+$_}
    Exact-Set @($compiledChecks | ForEach-Object name) $checkMembers 'focused-check compiled members'
    $base=Zip $references.baseline;$candidate=Zip $jar
    $added=@($candidate.Keys | Where-Object {-not $base.ContainsKey($_)} | Sort-Object)
    $removed=@($base.Keys | Where-Object {-not $candidate.ContainsKey($_)} | Sort-Object)
    $changed=@($candidate.Keys | Where-Object {$base.ContainsKey($_) -and $candidate[$_].sha256 -cne $base[$_].sha256} | Sort-Object)
    $debugCandidates=@($changed | Where-Object {$_ -cin $inner})
    Exact-Set $added @() 'added JAR members';Exact-Set $removed @() 'removed JAR members';Exact-Set $changed (@($changedExpected)+@($debugCandidates)) 'changed JAR members'
    foreach ($entry in $compiled) {Require ($candidate[$entry.name].sha256 -ceq $entry.sha256) "Packaged class differs from checked compilation: $($entry.name)"}
    foreach ($member in $candidate.Keys) {
        Require (-not ($member -match '(?i)(^|/)(regression|storytest|guidetest|test|tests)(/|\.)|(?:Test|Tests|Checks)(?:\$[^/]*)?\.class$|useless_stretcher_test|org/(junit|mockito)/|net/bytebuddy/')) "Test/check member packaged: $member"
        Require (-not $member.EndsWith('.jar',[StringComparison]::OrdinalIgnoreCase)) "Nested JAR packaged: $member"
        if ($member.EndsWith('.class')) {Require ($member.StartsWith('com/sorrowmist/useless/stretcher/',[StringComparison]::Ordinal)) "Third-party class packaged: $member"}
    }
    $languageAudit=@()
    foreach ($member in $languageMembers) {
        $old=Language (Zip-Text $base $member);$new=Language (Zip-Text $candidate $member)
        foreach ($key in $old.Keys) {Require ($new.ContainsKey($key) -and $new[$key] -ceq $old[$key]) "Old language entry changed/deleted: $member $key"}
        $keys=@($new.Keys | Where-Object {-not $old.ContainsKey($_)});Exact-Set $keys $newKeys "new translation keys in $member"
        Require ((Zip-Text $candidate $member) -ceq (Get-Content -LiteralPath (Join-Path $workspace ('src/main/resources/'+$member)) -Raw)) "Language source differs from packaged resource: $member"
        $languageAudit+=[ordered]@{member=$member;oldCount=$old.Count;newCount=$new.Count;added=@($keys | Sort-Object);oldValuesUnchanged=$true}
    }
    $toml=Zip-Text $candidate 'META-INF/neoforge.mods.toml'
    $expectedToml=(Zip-Text $base 'META-INF/neoforge.mods.toml') -replace '(?m)^version\s*=\s*"1\.21\.1-1\.6\.4-catalog-P4-assembly-lock"',('version="'+$version+'"')
    Require ($toml -ceq $expectedToml -and $toml -ceq (Get-Content -LiteralPath $resources[2] -Raw)) 'Mod metadata changed beyond the exact version or differs from hashed source.'

    $helperDirectory=Join-Path $runDirectory 'static-helper';New-Item -ItemType Directory -Path $helperDirectory -Force | Out-Null
    $helperSource=@'
import java.util.*;import java.util.zip.*;import org.objectweb.asm.*;import org.objectweb.asm.tree.*;
public final class JeiLocalizationAudit {
  static final String ROOT="com/sorrowmist/useless/stretcher/client/";
  static void require(boolean p,String m){if(!p)throw new IllegalStateException(m);}
  static ClassNode read(String jar,String name)throws Exception{try(ZipFile z=new ZipFile(jar)){var e=z.getEntry(ROOT+name+".class");require(e!=null,"missing "+name);var n=new ClassNode();try(var in=z.getInputStream(e)){new ClassReader(in).accept(n,0);}return n;}}
  static byte[] noDebug(ClassNode n){n.sourceFile=null;n.sourceDebug=null;for(var m:n.methods){m.localVariables=null;for(var i:m.instructions.toArray())if(i instanceof LineNumberNode)m.instructions.remove(i);}var w=new ClassWriter(0);n.accept(w);return w.toByteArray();}
  static MethodNode method(ClassNode c,String name,String desc){var ms=c.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).toList();require(ms.size()==1,"unique method missing "+name+desc);return ms.get(0);}
  static void publicStatic(MethodNode m){require((m.access&(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC))==(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC),"public static ABI changed");}
  public static void main(String[] a)throws Exception{
    var publication=read(a[0],"CatalogJeiPublication");publicStatic(method(publication,"progress","()Ljava/lang/String;"));publicStatic(method(publication,"progressComponent","()Lnet/minecraft/network/chat/Component;"));
    var accessor=method(publication,"progressComponent","()Lnet/minecraft/network/chat/Component;");int copies=0;for(var n:accessor.instructions)if(n instanceof MethodInsnNode m&&m.name.equals("copy")&&m.desc.equals("()Lnet/minecraft/network/chat/MutableComponent;"))copies++;require(copies==1,"Component accessor must return one detached copy");
    var lifecycle=read(a[0],"ClientCatalogLifecycle");var hud=method(lifecycle,"progress","(Lnet/neoforged/neoforge/client/event/RenderGuiEvent$Post;)V");int localized=0;
    for(var n:hud.instructions)if(n instanceof MethodInsnNode m){if(m.owner.equals(ROOT+"CatalogJeiPublication")){require(!m.name.equals("progress"),"HUD still reads diagnostic String");if(m.name.equals("progressComponent")){require(m.desc.equals("()Lnet/minecraft/network/chat/Component;"),"localized call ABI changed");localized++;}}require(!(m.owner.equals("net/minecraft/network/chat/Component")&&m.name.equals("literal")),"HUD still wraps raw literal status");}require(localized==1,"HUD must read localized Component exactly once");
    System.out.println("progress()String and progressComponent()Component ABI\tPASS");System.out.println("Component detached-copy accessor\tPASS");System.out.println("HUD uses localized Component, no diagnostic literal\tPASS");
    for(int i=2;i<a.length;i++){String member=a[i];require(member.startsWith(ROOT)&&member.endsWith(".class"),"bad inner member");String name=member.substring(ROOT.length(),member.length()-6);require(Arrays.equals(noDebug(read(a[0],name)),noDebug(read(a[1],name))),"Inner changed beyond SourceFile/SourceDebug/LineNumber/local-variable debug tables: "+member);System.out.println(member+"\tdebug-only normalized class equality\tPASS");}
  }
}
'@
    $helperFile=Join-Path $helperDirectory 'JeiLocalizationAudit.java';[IO.File]::WriteAllText($helperFile,$helperSource,[Text.UTF8Encoding]::new($false))
    $asmRoot=Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1\org.ow2.asm'
    $asmCore=Get-ChildItem -LiteralPath (Join-Path $asmRoot 'asm\9.9.1') -Recurse -Filter 'asm-9.9.1.jar' | Select-Object -First 1
    $asmTree=Get-ChildItem -LiteralPath (Join-Path $asmRoot 'asm-tree\9.9.1') -Recurse -Filter 'asm-tree-9.9.1.jar' | Select-Object -First 1
    Require ($null -ne $asmCore -and $null -ne $asmTree) 'Standalone ASM dependency missing.'
    $asmClasspath=$asmCore.FullName+[IO.Path]::PathSeparator+$asmTree.FullName
    & javac "-J-Djava.io.tmpdir=$helperDirectory" -encoding UTF-8 --release 21 -cp $asmClasspath -d $helperDirectory $helperFile
    Require ($LASTEXITCODE -eq 0) 'Standalone ASM helper compilation failed.'
    $static=& java "-Djava.io.tmpdir=$helperDirectory" -Xmx128m -cp ($helperDirectory+[IO.Path]::PathSeparator+$asmClasspath) JeiLocalizationAudit $jar $references.baseline @debugCandidates 2>&1
    $staticExit=$LASTEXITCODE;$static | Set-Content -LiteralPath (Join-Path $runDirectory 'localization-abi-audit.tsv') -Encoding utf8
    Require ($staticExit -eq 0 -and @($static).Count -eq (3+$debugCandidates.Count)) "Localization bytecode audit failed: $static"
    $after=[ordered]@{};foreach ($name in @($references.Keys)) {$after[$name]=Hash $references[$name];Require ($after[$name] -ceq $before[$name]) "Frozen reference changed: $name"}
    $after.candidate=Hash $jar;Require ($after.candidate -ceq $before.candidate) 'Candidate changed during audit.'
    $report.references=$references;$report.hashesBefore=$before;$report.hashesAfter=$after;$report.jar=$jar;$report.size=$build.size
    $report.checks=$check;$report.checkLog=$checkLog;$report.checkLogSha256=Hash $checkLog;$report.checkResultSha256=Hash $checkResult
    $report.languageAudit=$languageAudit;$report.staticChecks=@($static);$report.compiledClasses=$compiled;$report.debugOnlyInnerClasses=$debugCandidates
    $report.entryCounts=[ordered]@{baseline=$base.Count;candidate=$candidate.Count;unchanged=$base.Count-$changed.Count;added=$added.Count;changed=$changed.Count;removed=$removed.Count}
    $report.memberDiff=@($changed | ForEach-Object {[ordered]@{name=$_;oldSha256=$base[$_].sha256;sha256=$candidate[$_].sha256}})
    $report.buildResult=$BuildResult;$report.buildResultSha256=Hash $BuildResult;$report.auditToolSha256=Hash $PSCommandPath
    $report.performancePassed=$false;$report.runtimeMixinApplied=$false;$report.pending='HUD display and live language switching need actual client verification; no new 184-test or JEI performance run.'
    $report.status='FROZEN_FOCUSED_OFFLINE_CHECKS_PASSED'
} catch {$report.status='FAIL';$report.failure=$_.Exception.Message}
finally {$reportPath=Join-Path $runDirectory 'jei-localization-audit-report.json';$report | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $reportPath -Encoding utf8}
Write-Output ("{0}: {1}" -f $report.status,$reportPath)
if ($report.status -cne 'FROZEN_FOCUSED_OFFLINE_CHECKS_PASSED') {Write-Output $report.failure;exit 1}
$manifest=[ordered]@{status=$report.status;timestampUtc=$report.timestampUtc;jar=$jar;sha256=$before.candidate;size=$build.size;version=$version;baselineSha256=$referenceHashes.baseline;gameLaunched=$false;full184SuiteRerun=$false;checks=$check;checkLogSha256=$report.checkLogSha256;entryCounts=$report.entryCounts;memberDiff=$report.memberDiff;debugOnlyInnerClasses=$debugCandidates;auditReport=$reportPath;auditReportSha256=(Hash $reportPath);auditToolSha256=(Hash $PSCommandPath);performancePassed=$false;runtimeMixinApplied=$false;pending=$report.pending}
$manifestPath=Join-Path $runDirectory 'jei-localization-freeze-manifest.json';$manifest | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $manifestPath -Encoding utf8
$delivery=Split-Path $jar -Parent;Copy-Item -LiteralPath $manifestPath -Destination (Join-Path $delivery 'jei-localization-freeze-manifest.json')
$report.memberDiff | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $delivery 'jei-localization-members-vs-P4-lock.json') -Encoding utf8
@"
# JEI 延迟加载通知本地化：离线候选报告

版本：$version。冻结状态：$($report.status)。

JAR：$jar

SHA256：$($before.candidate)。大小：$($build.size) 字节。

本轮只改两项生产外部类、zh_cn/en_us 两语言资源和版本元数据。语言文件各新增十个 gui.useless_stretcher.jei_publication.* 键，旧翻译保持原值。另有 $($debugCandidates.Count) 项精确 inner class 只发生调试信息变化，已通过仅移除 SourceFile/SourceDebug/LineNumber/local-variable 调试表的 ASM 归一比较，保留方法描述符、参数元数据、代码、frames、注解和其它结构。其余包内成员保持 P4-lock 原字节。保留 progress()String 诊断 ABI，HUD 使用 progressComponent()Component；失败提示引导查看日志，完整诊断继续留在日志。

本轮执行并通过 $ExpectedChecks 项 focused checks；没有重跑 184 项完整回归，也没有启动 Minecraft、服务端或模组加载器。生产源码、语言资源、编译参数、编译类、focused 日志/结果和 JAR 成员哈希已关联。静态检查确认两个 API 描述符、Component copy 以及 HUD 的本地化调用。

审计证据：$reportPath。原 P4-lock/P4/P3/生产 JAR 哈希保持不变。没有提交、推送、发布或改动运行实例。实际 HUD 绘制、运行中语言切换与 JEI 整合包性能仍待客户端测试。
"@ | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'REPORT.md') -Encoding utf8
[ordered]@{status=$report.status;jar=$jar;sha256=$before.candidate;size=$build.size;focusedChecks=$ExpectedChecks;full184SuiteRerun=$false;entryCounts=$report.entryCounts} | ConvertTo-Json -Depth 6
