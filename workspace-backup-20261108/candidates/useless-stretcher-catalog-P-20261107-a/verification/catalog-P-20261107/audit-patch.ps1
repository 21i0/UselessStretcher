[CmdletBinding()]
param(
    [string]$JarToAudit = (Join-Path $PSScriptRoot '..\..\delivery\useless_stretcher-1.21.1-1.6.4-catalog-P1.jar'),
    [string]$BaselineJar = 'D:\AI\1.6.4-候选\useless_stretcher-1.21.1-1.6.4.jar',
    [string]$Published163Jar = 'D:\AI\useless_stretcher-1.21.1-1.6.3.jar',
    [string]$Native246 = (Join-Path $PSScriptRoot '..\..\libs\compile\useless_mod-1.21.1-2.4.6.jar'),
    [string]$Native2462 = 'D:\AI\sources\DollChronicle\build\mygenesis-longhuan-r6-D\mods\useless_mod-1.21.1-2.4.6.2.jar'
)
# Read-only with respect to every JAR and game directory. Only audit reports and
# this script's standalone ASM helper are written beside this script. No game,
# server, mod loader, Gradle task, attach command or target class is launched.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$expectedBaseline = 'AC78F41EAA7883434F6845CF7323829D77E66B994C82EDCF2B81AEC4AFCD9220'
$expected163 = '00A10F69575B941C6A7545D28B43749362CB02FC9E30288ABC271520537B5BFC'
$expected246 = 'B86A12E8BD6417FD3C89F7C723CA195D68D1F9F9F099480850FFD3C93E5897BC'
$expected2462 = '9F6F54AF76AA255123B354C8725D55C2ACE4525B8683E1136ACAEBCCC8813B17'
$expectedVersion = '1.21.1-1.6.4-catalog-P1'
$reportDirectory = Join-Path $PSScriptRoot ('audit-runs\' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmssfff'))
New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null
$report = [ordered]@{ timestampUtc = [DateTime]::UtcNow.ToString('o'); status = 'RUNNING'; gameLaunched = $false }
$failures = [System.Collections.Generic.List[string]]::new()
function Assert-Audit([bool]$Condition, [string]$Message) {
    if (-not $Condition) { $failures.Add($Message) }
}
function Read-Zip([string]$Path) {
    $items = @{}
    $zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $Path).Path)
    try {
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName.EndsWith('/')) { continue }
            if ($items.ContainsKey($entry.FullName)) { throw "Duplicate ZIP entry: $($entry.FullName)" }
            $stream = $entry.Open(); $memory = [System.IO.MemoryStream]::new()
            try { $stream.CopyTo($memory); $bytes = $memory.ToArray() } finally { $stream.Dispose(); $memory.Dispose() }
            $hasher = [System.Security.Cryptography.SHA256]::Create()
            try { $hash = [Convert]::ToHexString($hasher.ComputeHash($bytes)) } finally { $hasher.Dispose() }
            $items[$entry.FullName] = [pscustomobject]@{ Name=$entry.FullName; Size=$bytes.Length; Sha256=$hash; Bytes=$bytes }
        }
    } finally { $zip.Dispose() }
    return ,$items
}
function Text-Entry($Archive, [string]$Name) {
    if (-not $Archive.ContainsKey($Name)) { throw "Missing ZIP entry: $Name" }
    return [System.Text.Encoding]::UTF8.GetString($Archive[$Name].Bytes)
}
try {
    $paths = [ordered]@{ candidate=$JarToAudit; baseline164=$BaselineJar; published163=$Published163Jar; native246=$Native246; native2462=$Native2462 }
    $before = [ordered]@{}
    foreach ($label in @($paths.Keys)) {
        $paths[$label] = (Resolve-Path -LiteralPath $paths[$label]).Path
        $before[$label] = (Get-FileHash -LiteralPath $paths[$label] -Algorithm SHA256).Hash
    }
    $report.paths = $paths; $report.hashesBefore = $before
    Assert-Audit ($before.baseline164 -eq $expectedBaseline) 'The 1.6.4 baseline differs from the approved exact bytes.'
    Assert-Audit ($before.published163 -eq $expected163) 'Published 1.6.3 differs from its recorded bytes.'
    Assert-Audit ($before.native246 -eq $expected246) 'Native 2.4.6 differs from the audited exact bytes.'
    Assert-Audit ($before.native2462 -eq $expected2462) 'Native 2.4.6.2 differs from the audited exact bytes.'
    if ($failures.Count) { throw 'Input integrity failed.' }
    $base = Read-Zip $paths.baseline164; $patch = Read-Zip $paths.candidate
    $added = @(); $changed = @(); $removed = @(); $unchanged = 0
    foreach ($name in ($patch.Keys | Sort-Object)) {
        if (-not $base.ContainsKey($name)) { $added += $name }
        elseif ($patch[$name].Sha256 -ne $base[$name].Sha256) { $changed += $name }
        else { $unchanged++ }
    }
    foreach ($name in $base.Keys) { if (-not $patch.ContainsKey($name)) { $removed += $name } }
    $report.entryCounts = [ordered]@{ baseline=$base.Count; candidate=$patch.Count; unchanged=$unchanged; added=$added.Count; changed=$changed.Count; removed=$removed.Count }
    $report.added = @($added | ForEach-Object { [ordered]@{name=$_; size=$patch[$_].Size; sha256=$patch[$_].Sha256} })
    $report.changed = @($changed | ForEach-Object { [ordered]@{name=$_; oldSha256=$base[$_].Sha256; sha256=$patch[$_].Sha256} })
    $report.removed = @($removed)
    Assert-Audit ($removed.Count -eq 0) 'Baseline entries were removed.'
    $prefix = 'com/sorrowmist/useless/stretcher/'
    $newRoots = @(
        'content/catalog/BudgetedRecipeCatalog', 'content/catalog/CatalogPatchSupport',
        'content/catalog/CatalogSortWork', 'content/catalog/CatalogSyncGate', 'content/catalog/NativeCatalogAccess',
        'client/ClientCatalogLifecycle', 'client/CatalogJeiTiming', 'event/BudgetedCatalogEvents',
        'mixin/BudgetedRecipeCatalogMixin', 'mixin/BudgetedClientCatalogMixin', 'mixin/CatalogJeiTimingMixin'
    )
    $newRootRegex = '^' + [regex]::Escape($prefix) + '(?:' + (($newRoots | ForEach-Object { [regex]::Escape($_) }) -join '|') + ')(?:\$[^/]*)?\.class$'
    foreach ($name in $added) { Assert-Audit ($name -cmatch $newRootRegex) "Unexpected added entry: $name" }
    $allowedChanged = @('META-INF/neoforge.mods.toml', 'useless_stretcher.mixins.json', ($prefix + 'mixin/UselessStretcherMixinPlugin.class'))
    foreach ($name in $changed) { Assert-Audit ($name -cin $allowedChanged) "Unexpected changed entry: $name" }
    foreach ($root in $newRoots) { Assert-Audit ($patch.ContainsKey($prefix + $root + '.class')) "Missing patch class: $root" }
    foreach ($name in $patch.Keys) {
        Assert-Audit (-not ($name -match '(?i)(^|/)(regression|storytest|guidetest|test|tests)(/|\.)|(?:Test|Tests|Checks)(?:\$[^/]*)?\.class$|useless_stretcher_test|org/(junit|mockito)/|net/bytebuddy/')) "Test entry found: $name"
        Assert-Audit (-not $name.EndsWith('.jar', [StringComparison]::OrdinalIgnoreCase)) "Nested JAR found: $name"
        if ($name.EndsWith('.class')) { Assert-Audit ($name.StartsWith($prefix, [StringComparison]::Ordinal)) "Third-party class found: $name" }
    }
    foreach ($name in ($patch.Keys | Where-Object { $_ -match 'lang/[^/]+\.json$' })) {
        Assert-Audit ($base.ContainsKey($name) -and $patch[$name].Sha256 -eq $base[$name].Sha256) "Language/test-language entry changed: $name"
    }
    $toml = Text-Entry $patch 'META-INF/neoforge.mods.toml'
    Assert-Audit ($toml -match ('(?m)^version\s*=\s*"' + [regex]::Escape($expectedVersion) + '"\s*$')) 'Wrong candidate mod metadata version.'
    $baselineToml = Text-Entry $base 'META-INF/neoforge.mods.toml'
    $expectedToml = $baselineToml -replace '(?m)^version\s*=\s*"1\.21\.1-1\.6\.4"', ('version="' + $expectedVersion + '"')
    Assert-Audit ($toml.Replace("`r`n","`n") -ceq $expectedToml.Replace("`r`n","`n")) 'Mod metadata changed beyond the expected version.'
    $report.version = $expectedVersion
    $oldMixins = (Text-Entry $base 'useless_stretcher.mixins.json') | ConvertFrom-Json
    $newMixins = (Text-Entry $patch 'useless_stretcher.mixins.json') | ConvertFrom-Json
    foreach ($group in @('mixins','client','server')) {
        $expected = @($oldMixins.$group)
        if ($group -eq 'mixins') { $expected += 'BudgetedRecipeCatalogMixin' }
        if ($group -eq 'client') { $expected += 'BudgetedClientCatalogMixin','CatalogJeiTimingMixin' }
        $actual = @($newMixins.$group)
        Assert-Audit ((@($actual | Sort-Object) -join "`n") -ceq (@($expected | Sort-Object) -join "`n")) "Unexpected mixin registrations: $group"
        Assert-Audit (($actual | Select-Object -Unique).Count -eq $actual.Count) "Duplicate mixin registrations: $group"
    }
    foreach ($property in @('required','minVersion','package','plugin','compatibilityLevel')) {
        Assert-Audit ($newMixins.$property -ceq $oldMixins.$property) "Mixin config property changed: $property"
    }
    $helper = Join-Path $PSScriptRoot 'asm-audit'
    New-Item -ItemType Directory -Path $helper -Force | Out-Null
    $javaSource = @'
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

// Reads bytecode as data. Does not load target or Minecraft classes.
public class CatalogMixinAudit {
    static Object value(AnnotationNode a,String key) {
        if(a == null || a.values == null) return null;
        for(int i=0;i<a.values.size();i+=2) if(key.equals(a.values.get(i))) return a.values.get(i+1);
        return null;
    }
    static AnnotationNode annotation(List<AnnotationNode> list,String suffix) {
        if(list != null) for(var a:list) if(a.desc.endsWith(suffix)) return a;
        return null;
    }
    static ClassNode read(JarFile jar,String name) throws Exception {
        var e=jar.getJarEntry(name+".class"); if(e==null) throw new IllegalStateException("Missing class "+name);
        var c=new ClassNode(); try(var in=jar.getInputStream(e)){new ClassReader(in).accept(c,0);} return c;
    }
    static List<?> list(Object o){return o instanceof List<?> l?l:List.of(o);}
    static void require(boolean b,String message){if(!b) throw new IllegalStateException(message);}
    public static void main(String[] args) throws Exception {
        String p="com/sorrowmist/useless/stretcher/mixin/";
        var rows=new ArrayList<String>(); rows.add("native\tmixin\thandler\ttarget\tselector\tat\tmatches\tcallbackShape\trequire");
        try(var patch=new JarFile(args[0])) {
            for(int n=1;n<args.length;n++) try(var nativeJar=new JarFile(args[n])) {
                int checked=0;
                for(String mixin:List.of("BudgetedRecipeCatalogMixin","BudgetedClientCatalogMixin","CatalogJeiTimingMixin")) {
                    var c=read(patch,p+mixin); var mix=annotation(c.invisibleAnnotations,"/Mixin;");
                    if(mix==null) mix=annotation(c.visibleAnnotations,"/Mixin;");
                    require(mix!=null,"Missing @Mixin "+mixin);
                    Object targetValue=value(mix,"value");
                    var targets=targetValue!=null?list(targetValue):list(value(mix,"targets"));
                    require(targets.size()==1,"Expected one target "+mixin);
                    Object target=targets.get(0); String targetName=target instanceof Type t?t.getInternalName():target.toString().replace('.','/');
                    var nativeClass=read(nativeJar,targetName);
                    for(var handler:c.methods) {
                        var inject=annotation(handler.visibleAnnotations,"/Inject;");
                        if(inject==null) inject=annotation(handler.invisibleAnnotations,"/Inject;");
                        if(inject==null) continue;
                        require(value(inject,"require") instanceof Integer r && r>=1,"Missing fail-closed require "+handler.name);
                        for(Object selectorObject:list(value(inject,"method"))) {
                            String selector=selectorObject.toString(); int paren=selector.indexOf('(');
                            String name=paren<0?selector:selector.substring(0,paren), desc=paren<0?null:selector.substring(paren);
                            var selected=nativeClass.methods.stream().filter(m->m.name.equals(name)&&(desc==null||m.desc.equals(desc))).toList();
                            require(selected.size()==1,"Ambiguous/missing target "+targetName+"."+selector+" count="+selected.size());
                            var method=selected.get(0);
                            require((handler.access&Opcodes.ACC_STATIC)==(method.access&Opcodes.ACC_STATIC),"Static mismatch "+handler.name);
                            require(Type.getReturnType(handler.desc).equals(Type.VOID_TYPE),"Handler must return void "+handler.name);
                            var parameters=Type.getArgumentTypes(handler.desc); var targetArgs=Type.getArgumentTypes(method.desc);
                            require(parameters.length>0,"Missing callback "+handler.name);
                            String callback=Type.getReturnType(method.desc).equals(Type.VOID_TYPE)?"org/spongepowered/asm/mixin/injection/callback/CallbackInfo":"org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable";
                            require(parameters[parameters.length-1].getInternalName().equals(callback),"Wrong callback "+handler.name);
                            require(parameters.length==1 || (parameters.length==targetArgs.length+1 && Arrays.equals(Arrays.copyOf(parameters,parameters.length-1),targetArgs)),"Callback argument mismatch "+handler.name);
                            for(Object atObject:list(value(inject,"at"))) {
                                var at=(AnnotationNode)atObject; String kind=(String)value(at,"value"); int matches=0; String description=kind;
                                if("HEAD".equals(kind)) matches=method.instructions.size()>0?1:0;
                                else if("RETURN".equals(kind)) for(var insn:method.instructions) {if(insn.getOpcode()>=Opcodes.IRETURN&&insn.getOpcode()<=Opcodes.RETURN) matches++;}
                                else if("INVOKE".equals(kind)) {
                                    String call=(String)value(at,"target"); require(call!=null&&call.startsWith("L"),"Missing invoke target");
                                    int semi=call.indexOf(';'), open=call.indexOf('(',semi);
                                    String owner=call.substring(1,semi), called=call.substring(semi+1,open), callDesc=call.substring(open);
                                    for(var insn:method.instructions) if(insn instanceof MethodInsnNode i&&i.owner.equals(owner)&&i.name.equals(called)&&i.desc.equals(callDesc)) matches++;
                                    require(matches==1,"Invoke must match exactly once: "+call+" count="+matches);
                                    description=kind+":"+call;
                                    if(value(at,"shift") instanceof String[] shift) description+=" shift="+shift[1];
                                } else throw new IllegalStateException("Unsupported injection point "+kind);
                                require(matches>=((Integer)value(inject,"require")),"No injection point "+handler.name);
                                rows.add(Path.of(args[n]).getFileName()+"\t"+mixin+"\t"+handler.name+"\t"+targetName+"."+method.name+method.desc+"\t"+selector+"\t"+description+"\t"+matches+"\t"+(parameters.length==1?"callback-only":"all-target-args")+"\t"+value(inject,"require")); checked++;
                            }
                        }
                    }
                }
                require(checked==11,"Expected 11 injection checks per native version, got "+checked);
            }
        }
        for(var row:rows) System.out.println(row);
    }
}
'@
    $javaFile = Join-Path $helper 'CatalogMixinAudit.java'
    [IO.File]::WriteAllText($javaFile, $javaSource, [Text.UTF8Encoding]::new($false))
    $asmCache = Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1\org.ow2.asm'
    $asmCore = Get-ChildItem -LiteralPath (Join-Path $asmCache 'asm\9.9.1') -Recurse -Filter 'asm-9.9.1.jar' | Select-Object -First 1
    $asmTree = Get-ChildItem -LiteralPath (Join-Path $asmCache 'asm-tree\9.9.1') -Recurse -Filter 'asm-tree-9.9.1.jar' | Select-Object -First 1
    if (-not $asmCore -or -not $asmTree) { throw 'ASM 9.9.1 core/tree official cached JARs are required.' }
    $asmClasspath = $asmCore.FullName + [IO.Path]::PathSeparator + $asmTree.FullName
    $report.asmDependencies = @($asmCore,$asmTree | ForEach-Object { [ordered]@{ path=$_.FullName; sha256=(Get-FileHash -LiteralPath $_.FullName).Hash } })
    & javac -encoding UTF-8 --release 21 -cp $asmClasspath -d $helper $javaFile
    if ($LASTEXITCODE -ne 0) { throw 'Standalone ASM helper compilation failed.' }
    $asmOutput = & java -Xmx128m -cp ($helper + [IO.Path]::PathSeparator + $asmClasspath) CatalogMixinAudit $paths.candidate $paths.native246 $paths.native2462 2>&1
    $asmExit = $LASTEXITCODE
    $asmOutput | Set-Content -LiteralPath (Join-Path $reportDirectory 'mixin-target-audit.tsv') -Encoding utf8
    Copy-Item -LiteralPath (Join-Path $reportDirectory 'mixin-target-audit.tsv') -Destination (Join-Path $PSScriptRoot 'mixin-target-audit.tsv')
    if ($asmExit -ne 0) { throw "Mixin ABI/target audit failed: $asmOutput" }
    $report.mixinChecks = @($asmOutput | ConvertFrom-Csv -Delimiter "`t")
    Assert-Audit ($report.mixinChecks.Count -eq 22) 'Expected 22 injection checks across both native versions.'
    $after = [ordered]@{}
    foreach ($label in @($paths.Keys)) {
        $after[$label] = (Get-FileHash -LiteralPath $paths[$label] -Algorithm SHA256).Hash
        Assert-Audit ($after[$label] -eq $before[$label]) "Input changed during audit: $label"
    }
    $report.hashesAfter = $after
    $report.status = if ($failures.Count) { 'FAIL' } else { 'PASS' }
} catch {
    $failures.Add($_.Exception.Message)
    $report.status = 'FAIL'
} finally {
    $report.failures = @($failures)
    $report.limits = 'Static ZIP and bytecode audit only. Does not prove runtime Mixin application, JEI behavior, gameplay correctness or performance.'
    $report | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $reportDirectory 'audit-patch-report.json') -Encoding utf8
    Copy-Item -LiteralPath (Join-Path $reportDirectory 'audit-patch-report.json') -Destination (Join-Path $PSScriptRoot 'audit-patch-report.json')
}
Write-Output ("Audit {0}: {1}" -f $report.status, (Join-Path $reportDirectory 'audit-patch-report.json'))
if ($failures.Count) { $failures | Write-Output; exit 1 }
