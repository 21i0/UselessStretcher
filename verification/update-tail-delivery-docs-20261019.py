"""Prepend this delivery identity without rewriting historical evidence."""
from pathlib import Path
import hashlib, json, shutil

repo = Path(__file__).resolve().parents[1]
root = Path('D:/AI')
audit = json.loads((repo / 'verification/jar-20261019-tail-lively-delivered-audit.json').read_text(encoding='utf-8-sig'))
headline = f'''万象担架 1.6.0 柔软尾巴与尾根连接（2026-10-19，当前交付）
正式文件：D:/AI/useless_stretcher-1.21.1-1.6.0.jar；{audit['bytes']:,} 字节。
SHA256：{audit['sha256']}。
版本 1.21.1-1.6.0，网络协议 21，客户端与服务端同时更新。

'''
changes = '''- 尾巴宽面细分并按七段骨骼混合弯曲，新增交替扫尾、放松停顿、舒展卷尖与尾尖轻颤；坐姿去掉固定偏左，手持时尾巴偶尔从左右两侧探出。
- 尾根嵌入衣服后侧，连接处保持固定并逐渐过渡到柔软尾段；避地只柔和压缩下缘，不平移尾根或拉长尾巴。侧后视、坐姿及左右扫尾已用真实客户端连续画面检查。
- 奶白尾巴加入像素毛束、暖色底绒和碎边三角斑纹；浅蓝蝴蝶结改为折叠双环、细结心、菱形饰点和燕尾垂带。
- 起跳、下坠、落地、奔跑与转身有独立尾部惯性和回弹，不将新弹簧传给玩家手臂；保留空闲手部稳定与双持互换抖动。仅客户端局部计算，同载体每 tick 采样一次，没有新增服务端扫描或网络包。
- 实体、手持、头戴、Curios、摆件、物品与 UI 共用同一尾巴；新皮肤和真实挖掘/物品粒子保持一致，旧皮肤无残留。GuideME 已同步外观与动作说明。
'''
validation = '''- 完整 26 个外部依赖客户端于 12:11:54 通过最终检查：960 时刻尾根/双向摆动/连续性/支撑面、尾长、跳跃/下落/转身弹簧、皮肤/真实粒子/眨眼、1200 次手部同步、100 次双持换手、144 组伞柄姿态。96 帧动图和对照在 verification/tail-lively-20261019。
- 12:13:11 GuideME 九页编译/渲染和四入口通过。普通无测试参数 build 与构建/根包双审计通过：247 生产源码、427 class、137 资源，86 JSON/MCMeta，测试/第三方禁止项 0。不是重跑所有服务端玩法或完整 ATM10 的声明。
- 旧包已核验并归档 D:/AI/jar归档/20261019-before-two-i-lively-tail/；根目录只保留本项目这一新 JAR，其它项目文件保留。源码沿用授权同步 GitHub main，不创建 Release/标签，待实测。
完整证据：verification/1.6.0-two-i-lively-tail-20261019.md。
'''
usage = '''这次更新不需要配置或重置存档。拿在手上、戴在头上或饰品栏、摆放与展开实体都会使用新尾巴。静止时交替轻扫并停顿，走跑、转身、起跳和落地时有延迟与回弹；尾根留在衣服后侧，坐姿居中上翘。双持互换的原抖动演示保留。
本地动图：D:/AI/2i-尾巴动态-20261019.gif；对照：D:/AI/2i-尾巴动作对照-20261019.png。
旧包可从 D:/AI/jar归档/20261019-before-two-i-lively-tail/ 恢复；源码同步 GitHub main，正式 Release 等用户实测后上传。
'''

sections = {
    '更新.txt': headline + changes + validation,
    '万象担架使用说明.txt': headline + usage + validation,
    '万象担架-项目交接文档.md': '## ' + headline + changes + validation + '''
原源码/纹理/文档备份 D:/AI/archive/1.6.0-before-two-i-lively-tail-20261019/。保留旧日志及中间失败证据；attached-final 轮数值通过但截图出现拉长，已舍弃，最终以 contact-client 为准。历史未跟踪 $dir/、$new/、net/ 保留且不提交，不重置。
''',
}
for name, section in sections.items():
    path = repo / name
    old = path.read_text(encoding='utf-8-sig')
    path.write_text(section + '\n以下为历史记录，旧包身份和“当前”标题以本节为准。\n\n' + old, encoding='utf-8')
    shutil.copy2(path, root / name)
for name, text in [('changelog-1.6.0.txt', headline + changes + validation),
                   ('usage-1.6.0.txt', headline + usage + validation)]:
    path = root / name
    old = path.read_text(encoding='utf-8-sig')
    path.write_text(text + '\n以下为历史记录。\n\n' + old, encoding='utf-8')

fixture = json.loads((repo / 'verification/skin-tail-fixture-20261019.json').read_text(encoding='utf-8-sig'))
for directory in ('build/story-tail-contact-20261019/mods', 'build/guide-tail-lively-20261019/mods'):
    actual = {p.name: p for p in (repo / directory).glob('*.jar')}
    assert set(actual) == {j['name'] for j in fixture['jars']}, directory
    for j in fixture['jars']:
        p = actual[j['name']]
        assert p.stat().st_size == j['bytes'] and hashlib.sha256(p.read_bytes()).hexdigest().upper() == j['sha256'].upper(), p
fixture['verified_directories'] = ['build/story-tail-contact-20261019/mods', 'build/guide-tail-lively-20261019/mods']
(repo / 'verification/tail-lively-fixture-20261019.json').write_text(json.dumps(fixture,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

archive = root / 'jar归档/20261019-before-two-i-lively-tail/useless_stretcher-1.21.1-1.6.0.jar'
(repo / 'verification/tail-lively-old-jar-archive-20261019.json').write_text(json.dumps({
    'path': str(archive), 'bytes': archive.stat().st_size,
    'sha256': hashlib.sha256(archive.read_bytes()).hexdigest().upper(),
},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Documents synchronized; both client fixtures match all 26 dependency hashes.')
