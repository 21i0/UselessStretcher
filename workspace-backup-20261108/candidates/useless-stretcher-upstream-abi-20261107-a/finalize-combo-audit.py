"""Freeze already-completed focused bytecode evidence; no game classes are loaded."""
import csv
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parent
FINAL = ROOT/'final-candidate-ref-audit'
CANDIDATE = Path('D:/AI/candidates/useless-stretcher-upstream-2465-20261107-a/delivery/useless_stretcher-1.21.1-1.6.4-catalog-P4-assembly-lock-zh-hud-base2465-jei1970.jar')
BASELINE = Path('D:/AI/candidates/useless-stretcher-jei-hud-layout-20261107-a/delivery/useless_stretcher-1.21.1-1.6.4-catalog-P4-assembly-lock-zh-hud.jar')
STAGING = Path('D:/AI/sources/DollChronicle/verification/mod-updates-20261107/staging')
MODS = Path('D:/AI/candidates/MyGenesis-0.3.0-20261107-hotfix4-core-rc2-night/overrides/mods')
FILES = {
    'candidate': CANDIDATE,
    'baselineHud': BASELINE,
    'native2465': ROOT/'useless_mod-1.21.1-2.4.6.5-official.jar',
    'jei1970': STAGING/'jei-1.21.1-neoforge-19.70.0.460.jar',
    'data340': STAGING/'data_energistics-1.21.1-3.4.0.jar',
    'eaep1641': ROOT/'extendedae_plus-1.6.4.1.jar',
    'ae219218': MODS/'appliedenergistics2-19.2.18.jar',
}
def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest().upper()
def file_record(path):
    return {'path':str(path),'size':path.stat().st_size,'sha256':sha(path)}
def read_tsv(path):
    with path.open(encoding='utf-8') as f:
        return list(csv.DictReader(f,delimiter='\t'))
assert sha(CANDIDATE)=='9556D497C721FF7227D3A1C160F8DD44572D5982011D09761B88C5DA0C334AE7'
assert sha(BASELINE)=='E0E15336F1FFA8D1F9AC1839B7A29509BB2D6ECDC03C502AE92B1B4251048FB6'
assert sha(FILES['native2465'])=='84F957C02DFAC63DD3D9F3B269F59CB9704CC228725E9B044AE54C98718BDE31'
assert sha(FILES['jei1970'])=='097CDFAE99B208DFB94F8C55E813AF10E55688ECF4E42116FED1B7E26F0F4FCF'
assert sha(FILES['data340'])=='5FC69019F1068C9C333EFAEBF0D7F731DD663C38245A104AD597D91003299367'
checks={}
for target,expected in [('native',216),('jei',11),('ae2',150)]:
    p=FINAL/target/'reference-audit.tsv'
    refs=read_tsv(p)
    assert len(refs)==expected
    missing=[x for x in refs if x['status']!='RESOLVED']
    assert not missing
    checks[target]={'status':'PASS','referenceOccurrences':len(refs),'uniqueReferences':len({(x['kind'],x['owner'],x['name'],x['descriptor']) for x in refs}),'unresolved':missing,'report':file_record(p)}
for target,expected in [('native',15),('jei',13)]:
    p=FINAL/target/'focused-injection-audit.tsv'
    rows=read_tsv(p)
    asserted=[x for x in rows if x['status']!='DETAIL']
    assert len(asserted)==expected and all(x['status']=='PASS' for x in asserted)
    checks[target]['focusedChecks']=rows
    checks[target]['focusedPassed']=len(asserted)
    checks[target]['focusedReport']=file_record(p)
native_refs=read_tsv(FINAL/'native'/'reference-audit.tsv')
guard_refs=[x for x in native_refs if x['owner']=='com/sorrowmist/useless/content/recipe/AdapterGuards']
assert {x['name'] for x in guard_refs}=={'call','callEach','guard','isDisabled','rememberSource'}
native_data=read_tsv(ROOT/'native-to-data-340-audit'/'reference-audit.tsv')
assert len(native_data)==45 and all(x['status']=='RESOLVED' for x in native_data)
type_report=json.loads((FINAL/'jei-data-type-audit.json').read_text(encoding='utf-8'))
assert type_report['candidate']==str(CANDIDATE)
assert not type_report['results']['jei']['missing']
assert type_report['results']['data']['uniqueReferencedClasses']==0
deps={}
for label in ('native2465','eaep1641','data340','jei1970','ae219218'):
    with zipfile.ZipFile(FILES[label]) as z:
        deps[label]=tomllib.loads(z.read('META-INF/neoforge.mods.toml').decode('utf-8')).get('dependencies',{})
report={
    'status':'PASS_FOCUSED_STATIC_ABI_AND_EXACT_INJECTION_TARGETS',
    'checkedUtc':datetime.now(timezone.utc).isoformat(),
    'gameLaunched':False,'modClassesLoaded':False,'oldJarsAllowedToFillMissingTargetMembers':False,
    'inputs':{k:file_record(v) for k,v in FILES.items()},
    'memberResolution':checks,
    'newAdapterGuardsReferences':guard_refs,
    'nativeToData340':{'status':'PASS','referenceOccurrences':45,'report':file_record(ROOT/'native-to-data-340-audit'/'reference-audit.tsv')},
    'types':type_report,
    'dependenciesFromOfficialToml':deps,
    'exactFindings':[
        'Native 2.4.6.5 catalog private constructor/fields and focused inventory Mixin targets remain compatible; native catalog guard behavior changed and requires the new bridge.',
        'All five new AdapterGuards calls in the final candidate resolve to exact native 2.4.6.5 methods.',
        'JEI getIngredientSupplier three-argument overload now delegates to a new four-argument overload. Exactly one category.setRecipe call remains across both; both native exception handlers cover the redirected call.',
        'RecipeManagerInternal.addRecipe still invokes the exact RecipeMap.addRecipe(RecipeType,Object,IIngredientSupplier)V once, inside RuntimeException and LinkageError handlers. New role-set input remains native.',
        'RecipeLookup recipeType stays private final RecipeType. get() reads the same focus/hidden state and calls getRecipesStream. All seven GUI HEAD targets remain unique void methods.',
        'RecipesGui open now captures keyboard selection; updateLayout uses grid/scrollbar logic. HEAD cancellation retains the original body when not blocked.',
        'DATA 3.4.0 PatternProviderMetadata constructor takes ObjectList,ObjectList. Native 2.4.6.5 already calls this descriptor; applying an old List-widen patch would be incorrect.',
        'DATA 3.4.0 requires AE2 >=19.2.18, LDLib2 >=2.2.44 and AE2WTLib. EAEP 1.6.4.1 requires installed JEI >=19.70.0.460. These are official metadata facts, not runtime assertions.',
        'JDTE InfusionRecipeAdapter exact SHA remains A10245FBCB26DE8E194E726676409DA2BC0BA15902DEEAEE73D32638FE59DD45; GeneratedRecipeSlices exact-byte gate remains applicable.',
        'VAT adapter and PairResult full bytes differ, but every method matches after debug/frame removal. SlicedRecipeConversions uses a reflective profile without an exact SHA gate.',
    ],
    'limits':[
        'This is an offline focused ABI/bytecode audit, not a game, server, rendering, total-pack compatibility or performance run.',
        'Exact target existence/counts do not themselves prove transformed Mixin execution; candidate build/targeted checks and subsequent runtime verification are separate evidence.',
        'Parent-reported 147 targeted checks were not rerun by this audit.',
        'Native physical-slot pattern repair skips stretcher items, preserving installed UUID/snapshot; automatic repair of old patterns inside the snapshot is not established.',
        'All unrelated baseline source and prior audit outputs were preserved. No production mod directory or running client was modified.',
    ],
}
json_path=ROOT/'FINAL-COMBO-ABI-REPORT.json'
json_path.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
md=f'''# 最终候选的依赖与 ABI 审计

结论：静态成员解析和精确注入点检查通过。验证对象为最终候选 `{sha(CANDIDATE)}`，旧 HUD 基线 `{sha(BASELINE)}` 的审计独立保留。

| 目标 | 成员调用解析 | 聚焦检查 |
| --- | ---: | ---: |
| 无用之物 2.4.6.5 | 216 / 216 | 15 / 15 |
| JEI 19.70.0.460 | 11 / 11 | 13 / 13 |
| AE2 19.2.18 | 150 / 150 | — |
| 本体调用 DATA 3.4.0 | 45 / 45 | — |

最终包新增的 `AdapterGuards.call/callEach/guard/isDisabled/rememberSource` 五个调用均与官方方法描述符匹配。担架实际引用的 13 个 JEI 类全部存在，直接及反射类型字符串中没有 DATA 类引用。目标命名空间缺失成员时禁止通过旧 JAR 或旧散装 class 补齐。

JEI 的配方构建调用已经移到新增的四参数 helper，三个参数的原入口保留并转发；两个入口合计仅一个 `setRecipe` 注入点。索引入口仍只有一个匹配的 `RecipeMap.addRecipe`。两个调用均保留原生的 RuntimeException 和 LinkageError 捕获范围。Lookup 字段及 get 方法、七个 GUI HEAD 入口仍匹配；原生新增加的网格、滚动条与键盘选区逻辑不被替换。

DATA 3.4.0 的 `PatternProviderMetadata` 构造参数为 `ObjectList,ObjectList`，本体 2.4.6.5 的调用已经匹配。旧的 List 参数扩宽补丁不能套到这一组合。DATA 的元数据要求 LDLib2 ≥2.2.44、AE2 ≥19.2.18，并将 AE2WTLib 设为必需依赖；EAEP 1.6.4.1 要求已安装的 JEI ≥19.70.0.460。

JDTE 生成配方适配器的精确 SHA 未变化，因此原来的切片白名单继续命中。VAT 两个类的完整字节存在差异，去除 debug/frame 后各方法逐指令一致；该切片实现没有精确 SHA 门控。

完整输入路径、SHA256、官方 TOML、每个解析项、28 项精确检查和限制保存在同名 JSON。`final-candidate-ref-audit` 中是最终包的独立报告，先前 HUD 基线报告未覆盖。

本轮没有启动 MC 或加载模组类。这里证明的是静态 ABI 与注入点形状；不替代实际 Mixin 变换、客户端、服务器或整包性能验证。根代理报告的 147 项检查未在本审计中重复执行。原生修复会跳过物理槽中的担架物品，不能据此声称担架内部的旧失效样板已自动修好。
'''
md_path=ROOT/'FINAL-COMBO-ABI-REPORT.md'
md_path.write_text(md,encoding='utf-8')
evidence=[json_path,md_path,ROOT/'UpstreamAbiAudit.java',ROOT/'jei-data-type-audit.py',Path(__file__),ROOT/'jdte-generated-slice-hash-audit.json',ROOT/'vat-sliced-conversion-hash-audit.json',ROOT/'DATA340-PatternProviderMetadata-signatures.txt',FINAL/'jei-data-type-audit.json']
for part in ('native','jei','ae2'):
    evidence.append(FINAL/part/'reference-audit.tsv')
    evidence.append(FINAL/part/'method-comparison.tsv')
for part in ('native','jei'):
    evidence.append(FINAL/part/'focused-injection-audit.tsv')
receipt={'status':'FROZEN_FOCUSED_OFFLINE_ABI_AUDIT','checkedUtc':report['checkedUtc'],'candidate':file_record(CANDIDATE),'baselineHud':file_record(BASELINE),'evidence':[file_record(p) for p in evidence],'gameLaunched':False,'modClassesLoaded':False}
receipt_path=ROOT/'FINAL-COMBO-ABI-RECEIPT.json'
receipt_path.write_text(json.dumps(receipt,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'report':file_record(json_path),'markdown':file_record(md_path),'receipt':file_record(receipt_path),'memberCounts':{k:v['referenceOccurrences'] for k,v in checks.items()},'focusedPassed':28},ensure_ascii=False))
