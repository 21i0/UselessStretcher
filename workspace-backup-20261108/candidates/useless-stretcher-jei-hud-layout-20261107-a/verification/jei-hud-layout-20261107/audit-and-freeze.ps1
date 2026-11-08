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
$version='1.21.1-1.6.4-catalog-P4-assembly-lock-zh-hud'
$package='com/sorrowmist/useless/stretcher/client/'
$lifecycle=$package+'ClientCatalogLifecycle.class'
$overlay=$package+'CatalogJeiProgressOverlay.class'
$layout=$package+'CatalogJeiProgressOverlay$Layout.class'
$compiledExpected=@($lifecycle,$overlay,$layout)
$references=[ordered]@{
    baseline='D:\AI\candidates\useless-stretcher-jei-localization-20261107-a\delivery\useless_stretcher-1.21.1-1.6.4-catalog-P4-assembly-lock-zh.jar'
    assemblyLock='D:\AI\candidates\useless-stretcher-assembly-lock-20261107-a\delivery\useless_stretcher-1.21.1-1.6.4-catalog-P4-assembly-lock.jar'
    p4='D:\AI\candidates\useless-stretcher-catalog-P4-20261107-a\delivery\useless_stretcher-1.21.1-1.6.4-catalog-P4.jar'
    p3='D:\AI\candidates\useless-stretcher-catalog-P3-20261107-a\delivery\useless_stretcher-1.21.1-1.6.4-catalog-P3.jar'
    published163='D:\AI\useless_stretcher-1.21.1-1.6.3.jar'
}
$referenceHashes=@{
    baseline='7D54D427EE4E8D19B62B94B9B8DA3630FE20D8207D21A872F2101596EC59127D'
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
    Require ($build.gameLaunched -eq $false -and $build.compilerExitCode -eq 0 -and $build.checkCompilerExitCode -eq 0 -and $build.checkExitCode -eq 0) 'Expected successful offline compilation and focused checks.'
    Require ($null -eq $build.testSources -and $null -eq $build.testExitCode) 'This build must not claim a new full 184-test result.'
    $jar=Local $build.jar;$classes=Local $build.classesDirectory;$checksDirectory=Local $build.checkClassesDirectory
    Require ($classes -cne $checksDirectory) 'Production and check class directories overlap.'
    $before=[ordered]@{};foreach ($name in @($references.Keys)) {$before[$name]=Hash $references[$name];Require ($before[$name] -ceq $referenceHashes[$name]) "Frozen reference changed: $name"}
    $before.candidate=Hash $jar
    Require ($before.candidate -ceq $build.sha256 -and (Get-Item -LiteralPath $jar).Length -eq $build.size) 'Candidate changed after build.'
    Require ($build.baselineSha256 -ceq $referenceHashes.baseline) 'Build did not use exact localized baseline.'
    $mainExpected=@((Join-Path $workspace 'src\main\java\com\sorrowmist\useless\stretcher\client\ClientCatalogLifecycle.java'),(Join-Path $workspace 'src\main\java\com\sorrowmist\useless\stretcher\client\CatalogJeiProgressOverlay.java'))
    Exact-Set @(Get-ChildItem -LiteralPath (Join-Path $workspace 'src\main\java') -Filter '*.java' -Recurse | ForEach-Object FullName) $mainExpected 'production source inventory'
    Exact-Set @($build.mainSources | ForEach-Object path) $mainExpected 'recorded production sources'
    $checkExpected=@((Join-Path $PSScriptRoot 'JeiHudLayoutChecks.java'))
    Exact-Set @($build.checkSources | ForEach-Object path) $checkExpected 'focused-check sources'
    $metadata=Join-Path $workspace 'src\main\resources\META-INF\neoforge.mods.toml'
    Exact-Set @($build.inputs | ForEach-Object path) (@($mainExpected)+@($checkExpected)+@($metadata)) 'build input inventory'
    foreach ($source in @($build.inputs)+@($build.mainSources)+@($build.checkSources)) {
        Require ((Hash (Local $source.path)) -ceq $source.sha256) "Input changed after checks: $($source.path)"
        $record=@($build.inputs | Where-Object path -CEQ $source.path);Require ($record.Count -eq 1 -and $record[0].sha256 -ceq $source.sha256) 'Input/source record disagreement.'
    }
    $inputsBefore=Join-Path (Split-Path $classes -Parent) 'inputs-before-build.json'
    Require (((Get-Content -LiteralPath $inputsBefore -Raw | ConvertFrom-Json | ConvertTo-Json -Depth 6 -Compress)) -ceq ($build.inputs | ConvertTo-Json -Depth 6 -Compress)) 'Inputs-before-build differ from checked source record.'
    Compiler-Args (Args $build.mainCompilerArguments) $mainExpected $classes
    Compiler-Args (Args $build.checkCompilerArguments) $checkExpected $checksDirectory
    $runArgs=Args $build.checkArguments;$cpIndex=[Array]::IndexOf($runArgs,'-cp')
    Require ($cpIndex -ge 0) 'No focused-check runtime classpath.'
    $cp=@($runArgs[$cpIndex+1].Split(';'));Require ($cp[0] -ceq $classes -and $cp[1] -ceq $checksDirectory) 'Checks did not prioritize new production/check outputs.'
    $checkResult=Local $build.checkResult;$checkLog=Local $build.checkLog
    Require ($runArgs[-2] -ceq 'com.sorrowmist.useless.stretcher.client.JeiHudLayoutChecks' -and $runArgs[-1] -ceq $checkResult) 'Focused check main/result path differs.'
    Require ((Hash $checkResult) -ceq $build.checkResultSha256 -and (Hash $checkLog) -ceq $build.checkLogSha256) 'Focused check result/log changed.'
    $check=Get-Content -LiteralPath $checkResult -Raw | ConvertFrom-Json
    Require ($check.status -ceq 'PASS' -and $check.gameLaunched -eq $false -and $check.checksFound -eq $ExpectedChecks -and $check.checksStarted -eq $ExpectedChecks -and $check.checksPassed -eq $ExpectedChecks -and $check.checksFailed -eq 0) 'Focused checks did not execute and pass expected total.'
    $compiled=Classes $build.compiledClasses $classes;Exact-Set @($compiled | ForEach-Object name) $compiledExpected 'production compiled members'
    $compiledChecks=Classes $build.checkCompiledClasses $checksDirectory
    # This exact list is reviewed against the final check source before freezing.
    $checkMembers=@('JeiHudLayoutChecks.class','JeiHudLayoutChecks$Throwing.class') | ForEach-Object {$package+$_}
    Exact-Set @($compiledChecks | ForEach-Object name) $checkMembers 'focused-check compiled members'
    $base=Zip $references.baseline;$candidate=Zip $jar
    $added=@($candidate.Keys | Where-Object {-not $base.ContainsKey($_)} | Sort-Object)
    $removed=@($base.Keys | Where-Object {-not $candidate.ContainsKey($_)} | Sort-Object)
    $changed=@($candidate.Keys | Where-Object {$base.ContainsKey($_) -and $candidate[$_].sha256 -cne $base[$_].sha256} | Sort-Object)
    Exact-Set $added @($overlay,$layout) 'added JAR members'
    Exact-Set $removed @() 'removed JAR members'
    Exact-Set $changed @($lifecycle,'META-INF/neoforge.mods.toml') 'changed JAR members'
    foreach ($entry in $compiled) {Require ($candidate[$entry.name].sha256 -ceq $entry.sha256) "Packaged class differs from checked compilation: $($entry.name)"}
    foreach ($member in $candidate.Keys) {
        Require (-not ($member -match '(?i)(^|/)(regression|storytest|guidetest|test|tests)(/|\.)|(?:Test|Tests|Checks)(?:\$[^/]*)?\.class$|useless_stretcher_test|org/(junit|mockito)/|net/bytebuddy/')) "Check/test member packaged: $member"
        Require (-not $member.EndsWith('.jar',[StringComparison]::OrdinalIgnoreCase)) "Nested JAR packaged: $member"
        if ($member.EndsWith('.class')) {Require ($member.StartsWith('com/sorrowmist/useless/stretcher/',[StringComparison]::Ordinal)) "Third-party class packaged: $member"}
    }
    $toml=Zip-Text $candidate 'META-INF/neoforge.mods.toml'
    $expectedToml=(Zip-Text $base 'META-INF/neoforge.mods.toml') -replace '(?m)^version\s*=\s*"1\.21\.1-1\.6\.4-catalog-P4-assembly-lock-zh"',('version="'+$version+'"')
    Require ($toml -ceq $expectedToml -and $toml -ceq (Get-Content -LiteralPath $metadata -Raw)) 'Metadata changed beyond exact version or differs from source.'
    $helperDirectory=Join-Path $runDirectory 'static-helper';New-Item -ItemType Directory -Path $helperDirectory -Force | Out-Null
    $helperSource=@'
import java.util.*;import java.util.zip.*;import org.objectweb.asm.*;import org.objectweb.asm.tree.*;
public final class JeiHudLayoutAudit {
  static final String ROOT="com/sorrowmist/useless/stretcher/client/";
  static final String GUI="net/minecraft/client/gui/GuiGraphics";
  static final String PROGRESS="(Lnet/neoforged/neoforge/client/event/RenderGuiEvent$Post;)V";
  static void require(boolean p,String m){if(!p)throw new IllegalStateException(m);}
  static ClassNode read(String jar,String name)throws Exception{try(ZipFile z=new ZipFile(jar)){var e=z.getEntry(ROOT+name+".class");require(e!=null,"missing "+name);var n=new ClassNode();try(var in=z.getInputStream(e)){new ClassReader(in).accept(n,0);}return n;}}
  static MethodNode method(ClassNode c,String name,String desc){var ms=c.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).toList();require(ms.size()==1,"unique method missing "+name+desc);return ms.get(0);}
  static byte[] noDebug(ClassNode n){n.sourceFile=null;n.sourceDebug=null;for(var m:n.methods){m.localVariables=null;for(var i:m.instructions.toArray())if(i instanceof LineNumberNode)m.instructions.remove(i);}var w=new ClassWriter(0);n.accept(w);return w.toByteArray();}
  static byte[] methodHeader(MethodNode original){var m=new MethodNode(Opcodes.ASM9,original.access,original.name,original.desc,original.signature,original.exceptions.toArray(String[]::new));original.accept(m);m.instructions.clear();m.instructions.add(new InsnNode(Opcodes.RETURN));m.tryCatchBlocks.clear();m.localVariables=null;m.visibleLocalVariableAnnotations=null;m.invisibleLocalVariableAnnotations=null;m.maxStack=0;m.maxLocals=0;var n=new ClassNode();n.version=Opcodes.V21;n.access=Opcodes.ACC_PUBLIC;n.name="AuditHeader";n.superName="java/lang/Object";n.methods.add(m);return noDebug(n);}
  public static void main(String[] a)throws Exception {
    var candidate=read(a[0],"ClientCatalogLifecycle");var baseline=read(a[1],"ClientCatalogLifecycle");
    var progress=method(candidate,"progress",PROGRESS);var oldProgress=method(baseline,"progress",PROGRESS);
    require(Arrays.equals(methodHeader(progress),methodHeader(oldProgress)),"progress method ABI/annotations changed");
    candidate.methods.remove(progress);baseline.methods.remove(oldProgress);
    require(Arrays.equals(noDebug(candidate),noDebug(baseline)),"Lifecycle changed outside progress after debug normalization");
    System.out.println("Lifecycle excluding progress: class/fields/annotations/other methods unchanged after debug normalization\tPASS");
    int renderCalls=0,componentCalls=0;
    for(var insn:progress.instructions)if(insn instanceof MethodInsnNode m){
      if(m.owner.equals(ROOT+"CatalogJeiProgressOverlay")&&m.name.equals("render")){require(m.getOpcode()==Opcodes.INVOKESTATIC&&m.desc.equals("(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;)V"),"render delegation ABI differs");renderCalls++;}
      if(m.owner.equals(ROOT+"CatalogJeiPublication")&&m.name.equals("progressComponent"))componentCalls++;
      require(!(m.owner.equals(GUI)&&Set.of("fill","drawString","enableScissor","disableScissor").contains(m.name)),"Lifecycle still draws the old corner notification");
      require(!(m.owner.equals(ROOT+"CatalogJeiPublication")&&m.name.equals("progress")),"Lifecycle reads diagnostic String");
    }
    require(renderCalls==1&&componentCalls==1,"Expected one renderer delegation and localized snapshot read");
    System.out.println("progress delegates once to overlay; no old fill/draw or diagnostic String\tPASS");
    var overlay=read(a[0],"CatalogJeiProgressOverlay");var render=method(overlay,"render","(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;)V");
    int enables=0,disables=0,draws=0;AbstractInsnNode draw=null;
    for(var insn:render.instructions)if(insn instanceof MethodInsnNode m){
      if(m.owner.equals(GUI)&&m.name.equals("enableScissor"))enables++;
      if(m.owner.equals(GUI)&&m.name.equals("disableScissor"))disables++;
      if(m.owner.equals(GUI)&&m.name.equals("drawString")){draws++;draw=insn;}
    }
    require(enables==1&&disables==2&&draws==1,"Expected one scissor enable, normal/finally disable, and draw call");
    boolean guarded=false;
    for(var handler:render.tryCatchBlocks)if(handler.type==null){
      int begin=render.instructions.indexOf(handler.start),end=render.instructions.indexOf(handler.end),position=render.instructions.indexOf(draw);
      if(begin<=position&&position<end){boolean disabled=false;for(AbstractInsnNode n=handler.handler;n!=null;n=n.getNext()){if(n instanceof MethodInsnNode m&&m.owner.equals(GUI)&&m.name.equals("disableScissor"))disabled=true;if(n.getOpcode()==Opcodes.ATHROW){guarded=disabled;break;}}}
    }
    require(guarded,"Draw is not protected by catch-all scissor restore then rethrow");
    System.out.println("Overlay scissor restores in normal and catch-all exceptional exits\tPASS");
    for(var m:overlay.methods)for(var n:m.instructions)if(n instanceof MethodInsnNode call)require(!call.owner.toLowerCase(Locale.ROOT).contains("xaero")&&!call.owner.startsWith("net/minecraft/world/level/"),"Overlay unexpectedly depends on minimap/world APIs");
    System.out.println("Overlay uses no minimap/world-level method APIs\tPASS");
  }
}
'@
    $helperFile=Join-Path $helperDirectory 'JeiHudLayoutAudit.java';[IO.File]::WriteAllText($helperFile,$helperSource,[Text.UTF8Encoding]::new($false))
    $asmRoot=Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1\org.ow2.asm'
    $asmCore=Get-ChildItem -LiteralPath (Join-Path $asmRoot 'asm\9.9.1') -Recurse -Filter 'asm-9.9.1.jar' | Select-Object -First 1
    $asmTree=Get-ChildItem -LiteralPath (Join-Path $asmRoot 'asm-tree\9.9.1') -Recurse -Filter 'asm-tree-9.9.1.jar' | Select-Object -First 1
    Require ($null -ne $asmCore -and $null -ne $asmTree) 'Standalone ASM dependencies missing.'
    $asmClasspath=$asmCore.FullName+[IO.Path]::PathSeparator+$asmTree.FullName
    & javac "-J-Djava.io.tmpdir=$helperDirectory" -encoding UTF-8 --release 21 -cp $asmClasspath -d $helperDirectory $helperFile
    Require ($LASTEXITCODE -eq 0) 'Standalone ASM helper compilation failed.'
    $static=& java "-Djava.io.tmpdir=$helperDirectory" -Xmx128m -cp ($helperDirectory+[IO.Path]::PathSeparator+$asmClasspath) JeiHudLayoutAudit $jar $references.baseline 2>&1
    $staticExit=$LASTEXITCODE;$static | Set-Content -LiteralPath (Join-Path $runDirectory 'layout-bytecode-audit.tsv') -Encoding utf8
    Require ($staticExit -eq 0 -and @($static).Count -eq 4) "Layout bytecode audit failed: $static"
    $after=[ordered]@{};foreach ($name in @($references.Keys)) {$after[$name]=Hash $references[$name];Require ($after[$name] -ceq $before[$name]) "Frozen reference changed: $name"}
    $after.candidate=Hash $jar;Require ($after.candidate -ceq $before.candidate) 'Candidate changed during audit.'
    $report.references=$references;$report.hashesBefore=$before;$report.hashesAfter=$after;$report.jar=$jar;$report.size=$build.size
    $report.checks=$check;$report.checkLog=$checkLog;$report.checkLogSha256=Hash $checkLog;$report.checkResultSha256=Hash $checkResult
    $report.staticChecks=@($static);$report.compiledClasses=$compiled
    $report.entryCounts=[ordered]@{baseline=$base.Count;candidate=$candidate.Count;unchanged=$base.Count-$changed.Count;added=$added.Count;changed=$changed.Count;removed=$removed.Count}
    $report.memberDiff=[ordered]@{added=@($added | ForEach-Object {[ordered]@{name=$_;sha256=$candidate[$_].sha256}});changed=@($changed | ForEach-Object {[ordered]@{name=$_;oldSha256=$base[$_].sha256;sha256=$candidate[$_].sha256}});removed=$removed}
    $report.buildResult=$BuildResult;$report.buildResultSha256=Hash $BuildResult;$report.auditToolSha256=Hash $PSCommandPath
    $report.performancePassed=$false;$report.runtimeMixinApplied=$false;$report.pending='Actual GUI scale 4 rendering, minimap overlap and live window/translation changes need client verification. No MC or new 184-test run.'
    $report.status='FROZEN_FOCUSED_OFFLINE_CHECKS_PASSED'
} catch {$report.status='FAIL';$report.failure=$_.Exception.Message}
finally {$reportPath=Join-Path $runDirectory 'jei-hud-layout-audit-report.json';$report | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $reportPath -Encoding utf8}
Write-Output ("{0}: {1}" -f $report.status,$reportPath)
if ($report.status -cne 'FROZEN_FOCUSED_OFFLINE_CHECKS_PASSED') {Write-Output $report.failure;exit 1}
$manifest=[ordered]@{status=$report.status;timestampUtc=$report.timestampUtc;jar=$jar;sha256=$before.candidate;size=$build.size;version=$version;baselineSha256=$referenceHashes.baseline;gameLaunched=$false;full184SuiteRerun=$false;checks=$check;checkLogSha256=$report.checkLogSha256;entryCounts=$report.entryCounts;memberDiff=$report.memberDiff;auditReport=$reportPath;auditReportSha256=(Hash $reportPath);auditToolSha256=(Hash $PSCommandPath);performancePassed=$false;runtimeMixinApplied=$false;pending=$report.pending}
$manifestPath=Join-Path $runDirectory 'jei-hud-layout-freeze-manifest.json';$manifest | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $manifestPath -Encoding utf8
$delivery=Split-Path $jar -Parent;Copy-Item -LiteralPath $manifestPath -Destination (Join-Path $delivery 'jei-hud-layout-freeze-manifest.json')
$report.memberDiff | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $delivery 'jei-hud-layout-members-vs-zh.json') -Encoding utf8
@"
# JEI HUD 布局局部候选：离线检查
版本：$version。冻结状态：$($report.status)。
JAR：$jar
SHA256：$($before.candidate)。大小：$($build.size) 字节。

本轮只改变通知布局：在上方居中，按逻辑GUI尺寸限制宽高，支持两行与省略。仅新增Overlay及Layout record、修改Lifecycle.progress和版本；Lifecycle其余类结构和方法在去调试信息后相等。Publication、语言、调度预算、样板总成及其它业务成员全部保持中文基线原字节。

本轮 $ExpectedChecks 项专项离线检查通过，输入/编译类/检查日志/结果/JAR哈希已关联。静态确认一次委托渲染、原角落绘制移除、正常与异常退出都恢复scissor，不调用Xaero或世界方法。没有重跑184项，没有启动Minecraft或更改运行实例。实际GUI4、窄屏和小地图重叠仍需客户端确认；本包不含性能修复。

VAT由来源任务删除，不是本候选修改。目录总数随输入变化，不固定155024，不能用VAT输入不同的旧P3作单变量性能比较。
审计：$reportPath
"@ | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'REPORT.md') -Encoding utf8
[ordered]@{status=$report.status;jar=$jar;sha256=$before.candidate;size=$build.size;focusedChecks=$ExpectedChecks;full184SuiteRerun=$false;entryCounts=$report.entryCounts} | ConvertTo-Json -Depth 6

