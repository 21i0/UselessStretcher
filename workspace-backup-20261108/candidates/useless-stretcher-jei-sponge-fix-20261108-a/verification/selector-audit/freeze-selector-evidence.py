import csv
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parent
BUILD=ROOT.parent/'selector-build.json'
build=json.loads(BUILD.read_text(encoding='utf-8-sig'))
override=Path(build['classesDirectory'])/'com/sorrowmist/useless/stretcher/mixin/CatalogJeiBuildTimingMixin.class'
sponge=Path('D:/AI/test-runtimes/DollChronicle-1.21.1-NeoForge21.1.265/libraries/net/fabricmc/sponge-mixin/0.16.4+mixin.0.8.7/sponge-mixin-0.16.4+mixin.0.8.7.jar')
old=Path('D:/AI/sources/DollChronicle/build/mygenesis-native-R-20261107-a/mods/jei-1.21.1-neoforge-19.57.0.451.jar')
new=Path('D:/AI/sources/DollChronicle/verification/mod-updates-20261107/staging/jei-1.21.1-neoforge-19.70.0.460.jar')
native=Path('D:/AI/candidates/useless-stretcher-upstream-abi-20261107-a/useless_mod-1.21.1-2.4.6.5-official.jar')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest().upper()
def rec(p):return {'path':str(p),'size':p.stat().st_size,'sha256':sha(p)}
def rows(p):
    with p.open(encoding='utf-8') as f:return list(csv.DictReader(f,delimiter='\t'))
assert sha(Path(build['baseline']))==build['baselineSha256']=='9556D497C721FF7227D3A1C160F8DD44572D5982011D09761B88C5DA0C334AE7'
assert sha(Path(build['source']))==build['sourceSha256']
bad=rows(ROOT/'9556-bad-baseline/actual-sponge-selector-results.tsv')
assert len(bad)==20
failed=[x for x in bad if int(x['actualInstructionHits'])==0]
assert len(failed)==1 and failed[0]['mixin']=='CatalogJeiBuildTimingMixin'
results={}
for label in ['old1957','new1970']:
    actual=rows(ROOT/label/'actual-sponge-selector-results.tsv')
    assert len(actual)==21
    fixed=[x for x in actual if x['mixin']=='CatalogJeiBuildTimingMixin']
    unaffected=[x for x in actual if x['mixin']!='CatalogJeiBuildTimingMixin']
    assert len(fixed)==2 and sum(int(x['actualInstructionHits']) for x in fixed)==1
    assert len(unaffected)==19 and all(int(x['actualInstructionHits'])>0 for x in unaffected)
    annotation=rows(ROOT/label/'fixed-build-annotation.tsv')
    assert len(annotation)==1 and annotation[0]['require']=='1' and annotation[0]['allow']=='1' and annotation[0]['staticHandler']=='true'
    results[label]={'status':'PASS_PARSER_MATCHER_LAYER','rows':len(actual),'fixedDualSelectorTotalInvokeHits':1,'unaffectedSelectorAtRows':19,'unaffectedRowsWithPositiveAtHits':19,'annotation':annotation[0],'report':rec(ROOT/label/'actual-sponge-selector-results.tsv')}
report={
 'status':'PASS_ACTUAL_SPONGE_0164_PARSER_MATCHER_BOTH_VERIFIED_JEI_VERSIONS',
 'checkedUtc':datetime.now(timezone.utc).isoformat(),
 'gameLaunched':False,'modLauncherStarted':False,'mixinTransformerRunByThisProbe':False,'gameOrModClassesLoaded':False,
 'spongeParserClassesLoaded':True,
 'inputs':{'sponge':rec(sponge),'oldJei':rec(old),'newJei':rec(new),'native':rec(native),'baselineJar':rec(Path(build['baseline'])),'compiledFix':rec(override),'source':rec(Path(build['source'])),'buildReceipt':rec(BUILD)},
 'baselineFailure':failed[0],
 'actualSelectorRule':'MemberInfo.parse(...).configure(SELECT_MEMBER) replaces default Quantifier with SINGLE(0..1). TargetSelectors.findRootTargets visits class-file method order and stops when maxMatchCount=1 is reached; it does not search later overloads for an @At match.',
 'auditGap':'The previous 13 checks counted matching instructions across every same-name overload. They did not run Sponge selector parsing and could not prove which method was selected. Runtime C exposed this gap. The old report stays frozen and is superseded for selector behavior by this addendum.',
 'fix':'Two complete getIngredientSupplier descriptors in one Redirect method array, one for three parameters and one for four; original static handler and exact setRecipe INVOKE retained; require=1 and allow=1 apply to the combined injection.',
 'results':results,
 'limits':['This invokes the actual Sponge 0.16.4 MemberInfo parser and ElementNode matcher, plus the verified SINGLE stop rule. It does not run the Mixin transformer, injection application, ModLauncher, or Minecraft. The separate reviewer transformer harness is required before calling transformed execution verified.','Missing four-parameter overload on old JEI is expected; combined injection count remains one. On new JEI, old overload exists but contributes zero, and the new overload contributes one.','Other 19 selector/At rows in all seven mixins remain positive. invalidate(Level) has two RETURN points, preserved as expected.'],
}
json_path=ROOT/'SELECTOR-AUDIT-REPORT.json'
json_path.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
md='''# Sponge 0.16.4 选择器补审

旧 9556 候选的真实故障已复现到选择器层：裸 `getIngredientSupplier` 经 Sponge 的 `SELECT_MEMBER` 配置变成最多匹配一个方法，按 class 文件顺序选中原三个参数的转发入口。该方法不再直接调用 `setRecipe`，因此实际注入点为零。之前跨全部同名重载累计调用的静态检查漏掉了选择器限制。

新修复保留同一静态 Redirect handler 和原始 INVOKE，明确列出三个参数、四个参数两个完整描述符，并保持 `require=1, allow=1`。实际加载 Sponge 0.16.4 的 MemberInfo parser 与 ElementNode matcher 验证：

| JEI | 三参数入口的 INVOKE | 四参数入口的 INVOKE | 合计 |
| --- | ---: | ---: | ---: |
| 19.57.0.451 | 1 | 不存在 | 1 |
| 19.70.0.460 | 0 | 1 | 1 |

七个 mixin 的其余 19 条 selector/At 记录全部在实际选中方法中命中；`invalidate(Level)` 的两个返回点均保留。9556 旧报告、旧故障字节码和新修复编译收据独立保存。

这是实际 Sponge parser/matcher 检查，没有启动 Mixin transformer、ModLauncher 或 Minecraft；完整变换执行由另一份 transformer harness 验证。本补充报告修正旧审计在选择器层的缺口，不能将原来的 13 项静态通过视为 Sponge 注入成功。
'''
md_path=ROOT/'SELECTOR-AUDIT-REPORT.md';md_path.write_text(md,encoding='utf-8')
files=[json_path,md_path,ROOT/'SelectorProbe.java',Path(__file__),BUILD,override]
for label in ['old1957','new1970']:
    for name in ['actual-sponge-selector-results.tsv','build-selector-correction.tsv','fixed-build-annotation.tsv']:files.append(ROOT/label/name)
for name in ['TargetSelectors-javap.txt','MemberInfo-javap.txt','Quantifier-javap.txt','selector-api-signatures.txt']:files.append(ROOT/name)
for name in ['actual-sponge-selector-results.tsv','build-selector-correction.tsv','9556-build-timing-mixin-javap-verbose.txt']:files.append(ROOT/'9556-bad-baseline'/name)
receipt={'status':'FROZEN_PARSER_MATCHER_EVIDENCE','checkedUtc':report['checkedUtc'],'inputs':report['inputs'],'evidence':[rec(p) for p in files],'mixinTransformerRunByThisProbe':False}
receipt_path=ROOT/'SELECTOR-AUDIT-RECEIPT.json';receipt_path.write_text(json.dumps(receipt,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'report':rec(json_path),'markdown':rec(md_path),'receipt':rec(receipt_path),'results':results},ensure_ascii=False))
