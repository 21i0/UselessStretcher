import hashlib, json, os, pathlib, subprocess, sys, zipfile
sys.stdout.reconfigure(encoding='utf-8')
ROOT = pathlib.Path(__file__).resolve().parent
P2 = pathlib.Path(r'D:\AI\candidates\useless-stretcher-catalog-P2-20261107-a\delivery\useless_stretcher-1.21.1-1.6.4-catalog-P2.jar')
JAVA = pathlib.Path(r'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin')
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(P2) == '161d9cf701f2c3060dfc17416f9e557111d325c4c11767e0460ba2cbed76eea4'
RUN = ROOT / 'reentry-partial-throw-run-2'
RUN.mkdir(exist_ok=False)
for n in ('sources','classes','tmp','home'): (RUN/n).mkdir()
(RUN/'executed-reproduce.py').write_bytes(pathlib.Path(__file__).read_bytes())
sources = {
'org/slf4j/Logger.java': '''package org.slf4j; public interface Logger { default void info(String s,Object... a){} default void error(String s,Object... a){} }''',
'com/mojang/logging/LogUtils.java': '''package com.mojang.logging; public final class LogUtils { public static org.slf4j.Logger getLogger(){return new org.slf4j.Logger(){};} }''',
'net/minecraft/world/item/crafting/RecipeManager.java': '''package net.minecraft.world.item.crafting; public class RecipeManager {}''',
'net/minecraft/world/level/Level.java': '''package net.minecraft.world.level; public class Level {}''',
'net/minecraft/client/multiplayer/ClientLevel.java': '''package net.minecraft.client.multiplayer; public class ClientLevel extends net.minecraft.world.level.Level { private final net.minecraft.world.item.crafting.RecipeManager manager = new net.minecraft.world.item.crafting.RecipeManager(); public net.minecraft.world.item.crafting.RecipeManager getRecipeManager(){return manager;} }''',
'net/minecraft/client/gui/screens/Screen.java': '''package net.minecraft.client.gui.screens; public class Screen { public void onClose(){} }''',
'net/minecraft/client/Minecraft.java': '''package net.minecraft.client; public class Minecraft { private static final Minecraft INSTANCE=new Minecraft(); public net.minecraft.client.multiplayer.ClientLevel level = new net.minecraft.client.multiplayer.ClientLevel(); public net.minecraft.client.gui.screens.Screen screen; public static Minecraft getInstance(){return INSTANCE;} public boolean isSameThread(){return true;} public void execute(Runnable r){r.run();} public void setScreen(net.minecraft.client.gui.screens.Screen s){screen=s;} }''',
'mezz/jei/api/recipe/RecipeType.java': '''package mezz.jei.api.recipe; public class RecipeType<T> {}''',
'mezz/jei/api/recipe/IRecipeManager.java': '''package mezz.jei.api.recipe; public interface IRecipeManager { <T> void addRecipes(RecipeType<T> type, java.util.List<T> recipes); <T> void hideRecipes(RecipeType<T> type, java.util.List<T> recipes); <T> void unhideRecipes(RecipeType<T> type, java.util.List<T> recipes); }''',
'mezz/jei/api/runtime/IRecipesGui.java': '''package mezz.jei.api.runtime; public interface IRecipesGui { java.util.Optional<net.minecraft.client.gui.screens.Screen> getParentScreen(); }''',
'mezz/jei/api/runtime/IJeiRuntime.java': '''package mezz.jei.api.runtime; public interface IJeiRuntime { mezz.jei.api.recipe.IRecipeManager getRecipeManager(); IRecipesGui getRecipesGui(); }''',
'com/sorrowmist/useless/content/recipe/AlloyFurnaceRecipeIdentity.java': '''package com.sorrowmist.useless.content.recipe; public record AlloyFurnaceRecipeIdentity(String id) {}''',
'com/sorrowmist/useless/content/recipe/AlloyFurnaceRecipeCatalog.java': '''package com.sorrowmist.useless.content.recipe; public class AlloyFurnaceRecipeCatalog { public record Entry(AlloyFurnaceRecipeIdentity identity) {} public static java.util.List<Entry> input; public static boolean isReady(net.minecraft.world.level.Level l){return true;} public static java.util.List<Entry> entries(net.minecraft.world.level.Level l){return input;} }''',
'com/sorrowmist/useless/compat/jei/AdvancedAlloyFurnaceRecipeCategory.java': '''package com.sorrowmist.useless.compat.jei; public class AdvancedAlloyFurnaceRecipeCategory { public static final mezz.jei.api.recipe.RecipeType<com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog.Entry> TYPE = new mezz.jei.api.recipe.RecipeType<>(); }''',
'com/sorrowmist/useless/compat/jei/JEIPlugin.java': '''package com.sorrowmist.useless.compat.jei; public class JEIPlugin { public static mezz.jei.api.runtime.IJeiRuntime runtime; private static final java.util.Map<com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeIdentity,com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog.Entry> registeredAlloyFurnaceRecipes=new java.util.LinkedHashMap<>(); public static mezz.jei.api.runtime.IJeiRuntime getRuntime(){return runtime;} public static int ledgerSize(){return registeredAlloyFurnaceRecipes.size();} public static void clear(){registeredAlloyFurnaceRecipes.clear();} }''',
'ReentryPartialThrow.java': r'''import java.util.*;
import com.sorrowmist.useless.stretcher.client.CatalogJeiPublication;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog.Entry;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeIdentity;
import com.sorrowmist.useless.compat.jei.JEIPlugin;
import mezz.jei.api.recipe.*;
import mezz.jei.api.runtime.*;
public class ReentryPartialThrow {
  static Object field(String n)throws Exception{var f=CatalogJeiPublication.class.getDeclaredField(n);f.setAccessible(true);return f.get(null);}
  static final class Runtime implements IJeiRuntime,IRecipeManager {
    final List<Object> registered=new ArrayList<>(); int calls; boolean reenterRequest; boolean didThrow;
    Runtime(boolean request){reenterRequest=request;}
    public IRecipeManager getRecipeManager(){return this;}
    public IRecipesGui getRecipesGui(){return () -> Optional.empty();}
    public <T> void addRecipes(RecipeType<T> type,List<T> batch){
      calls++;
      if(!didThrow){
        registered.add(batch.getFirst());
        CatalogJeiPublication.invalidate();
        if(reenterRequest)CatalogJeiPublication.request();
        didThrow=true;
        throw new IllegalStateException("independent fixture: accepted first entry then reentered and threw");
      }
      registered.addAll(batch);
    }
    public <T> void hideRecipes(RecipeType<T> t,List<T> b){}
    public <T> void unhideRecipes(RecipeType<T> t,List<T> b){}
  }
  static void runCase(boolean request)throws Exception {
    CatalogJeiPublication.reset(); JEIPlugin.clear();
    var input=List.of(new Entry(new AlloyFurnaceRecipeIdentity("r0")),new Entry(new AlloyFurnaceRecipeIdentity("r1")),new Entry(new AlloyFurnaceRecipeIdentity("r2")));
    AlloyFurnaceRecipeCatalog.input=input;
    var target=new Runtime(request); JEIPlugin.runtime=target;
    CatalogJeiPublication.request();
    for(int i=0;i<10000&&target.calls==0;i++)CatalogJeiPublication.frame();
    if(target.calls!=1||target.registered.size()!=1)throw new AssertionError("Did not execute first partial throw exactly once");
    boolean unsafeAfter=(boolean)field("unsafeFailure");
    System.out.println("AFTER_PARTIAL_THROW request="+request+" calls="+target.calls+" accepted="+target.registered.size()+" ledger="+JEIPlugin.ledgerSize()+" unsafeFailure="+unsafeAfter+" task="+(field("task")==null?"null":"present")+" status="+CatalogJeiPublication.progress());
    for(int i=0;i<10000&&!CatalogJeiPublication.ready();i++)CatalogJeiPublication.frame();
    long copies=target.registered.stream().filter(e->e==input.getFirst()).count();
    boolean reproduced=!unsafeAfter&&target.calls==2&&copies==2&&CatalogJeiPublication.ready();
    System.out.println("FINAL request="+request+" ready="+CatalogJeiPublication.ready()+" calls="+target.calls+" accepted="+target.registered.size()+" duplicateFirst="+copies+" ledger="+JEIPlugin.ledgerSize()+" unsafeFailure="+field("unsafeFailure")+" BUG_REPRODUCED="+reproduced);
    if(!reproduced)throw new AssertionError("Counterexample not reproduced");
  }
  public static void main(String[] args)throws Exception{runCase(false);runCase(true);System.out.println("P2_REENTRY_PARTIAL_THROW_BLOCK_CONFIRMED_2_OF_2");}
}'''
}
for name, code in sources.items():
    out=RUN/'sources'/name;out.parent.mkdir(parents=True,exist_ok=True);out.write_text(code,encoding='utf-8')
extracted=[]
with zipfile.ZipFile(P2) as z:
    for n in z.namelist():
        if n.endswith('.class') and (n.startswith('com/sorrowmist/useless/stretcher/client/CatalogJeiPublication') or n.startswith('com/sorrowmist/useless/stretcher/content/catalog/OwnerRecipePublication')):
            b=z.read(n);out=RUN/'classes'/n;out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(b)
            extracted.append({'entry':n,'bytes':len(b),'sha256':hashlib.sha256(b).hexdigest()})
assert len(extracted)==6,extracted
env=os.environ.copy();env['TEMP']=env['TMP']=str(RUN/'tmp')
env['HOME']=env['USERPROFILE']=str(RUN/'home')
jvm=['-XX:-UsePerfData','-Djava.io.tmpdir='+str(RUN/'tmp'),'-Duser.home='+str(RUN/'home')]
compilecmd=[str(JAVA/'javac.exe'),*[('-J'+x) for x in jvm],'-encoding','UTF-8','-cp',str(RUN/'classes'),'-d',str(RUN/'classes'),*[str(p)for p in sorted((RUN/'sources').rglob('*.java'))]]
cp=subprocess.run(compilecmd,cwd=RUN,env=env,capture_output=True,text=True,encoding='utf-8')
(RUN/'compile.log').write_text(cp.stdout+cp.stderr,encoding='utf-8')
if cp.returncode: print(cp.stdout+cp.stderr);raise SystemExit(cp.returncode)
cmd=[str(JAVA/'java.exe'),*jvm,'-cp',str(RUN/'classes'),'ReentryPartialThrow']
r=subprocess.run(cmd,cwd=RUN,env=env,capture_output=True,text=True,encoding='utf-8')
(RUN/'reproduction.log').write_text(r.stdout+r.stderr,encoding='utf-8')
print(r.stdout+r.stderr)
post=sha(P2)
receipt={'candidate':str(P2),'sha256':post,'candidateUnchanged':post=='161d9cf701f2c3060dfc17416f9e557111d325c4c11767e0460ba2cbed76eea4','scope':'Actual extracted P2 CatalogJeiPublication and OwnerRecipePublication classes; isolated minimal external-ABI stubs. No game launch, no native JEI execution, no performance or gameplay acceptance.','extractedActualClasses':extracted,'compileCommand':compilecmd,'compileExit':cp.returncode,'runCommand':cmd,'runExit':r.returncode,'conclusion':'BLOCK' if r.returncode==0 and 'P2_REENTRY_PARTIAL_THROW_BLOCK_CONFIRMED_2_OF_2' in r.stdout else 'UNRESOLVED','artifacts':[]}
for p in sorted(RUN.rglob('*')):
    if p.is_file():receipt['artifacts'].append({'path':str(p.relative_to(ROOT)),'bytes':p.stat().st_size,'sha256':sha(p)})
(RUN/'receipt.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'conclusion':receipt['conclusion'],'receipt':str(RUN/'receipt.json'),'sha256':sha(RUN/'receipt.json')},ensure_ascii=False))
raise SystemExit(r.returncode)
