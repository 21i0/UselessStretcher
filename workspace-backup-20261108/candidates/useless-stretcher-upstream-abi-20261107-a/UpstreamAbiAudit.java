import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Offline bytecode audit only. Never loads or initializes a game/mod class. */
public final class UpstreamAbiAudit {
  static final String PREFIX="com/sorrowmist/useless/";
  static final String OWN=PREFIX+"stretcher/";
  static String TARGET=PREFIX;
  static final Map<String,ClassNode> OLD=new HashMap<>(), NEW=new HashMap<>();
  static final List<ZipFile> EXTRA=new ArrayList<>();
  static final Map<String,ClassNode> EXTERNAL=new HashMap<>();
  static ClassNode parse(byte[] b) { ClassNode n=new ClassNode();new ClassReader(b).accept(n,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);return n; }
  static Map<String,byte[]> read(Path path) throws Exception {
    Map<String,byte[]> out=new TreeMap<>();try(ZipFile z=new ZipFile(path.toFile())){var e=z.entries();while(e.hasMoreElements()){var x=e.nextElement();if(x.getName().endsWith(".class"))out.put(x.getName().replaceFirst("\\.class$",""),z.getInputStream(x).readAllBytes());}}return out;
  }
  static ClassNode find(String s,Map<String,ClassNode> local) throws Exception {
    if(local.containsKey(s))return local.get(s);if(EXTERNAL.containsKey(s))return EXTERNAL.get(s);
    if(s.startsWith(TARGET)&&!s.startsWith(OWN))return null;
    for(ZipFile z:EXTRA){var e=z.getEntry(s+".class");if(e!=null){var n=parse(z.getInputStream(e).readAllBytes());EXTERNAL.put(s,n);return n;}}
    try(var i=ClassLoader.getSystemResourceAsStream(s+".class")){if(i!=null){var n=parse(i.readAllBytes());EXTERNAL.put(s,n);return n;}}return null;
  }
  record Res(String owner,int access) {}
  static Res resolve(String owner,String name,String desc,boolean field,Map<String,ClassNode> local,Set<String> seen) throws Exception {
    if(owner==null||!seen.add(owner))return null;var n=find(owner,local);if(n==null)return null;
    if(field){for(var m:n.fields)if(m.name.equals(name)&&m.desc.equals(desc))return new Res(owner,m.access);}
    else {for(var m:n.methods)if(m.name.equals(name)&&m.desc.equals(desc))return new Res(owner,m.access);if(name.equals("<init>"))return null;}
    var r=resolve(n.superName,name,desc,field,local,seen);if(r!=null)return r;
    for(String i:n.interfaces){r=resolve(i,name,desc,field,local,seen);if(r!=null)return r;}return null;
  }
  static String text(MethodNode m){Textifier p=new Textifier();m.accept(new TraceMethodVisitor(p));StringWriter w=new StringWriter();p.print(new PrintWriter(w));return w.toString();}
  static Map<String,MethodNode> methods(ClassNode n){Map<String,MethodNode> o=new TreeMap<>();for(var m:n.methods)o.put(m.name+m.desc,m);return o;}
  static void check(PrintWriter p,String name,boolean result,String detail){p.printf("%s\t%s\t%s%n",result?"PASS":"FAIL",name,detail);}
  static MethodNode method(String owner,String name,String desc){var n=NEW.get(PREFIX+owner);return n==null?null:n.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).findFirst().orElse(null);}
  static boolean catches(MethodNode m,AbstractInsnNode call,String type){int at=m.instructions.indexOf(call);return m.tryCatchBlocks.stream().anyMatch(t->type.equals(t.type)&&m.instructions.indexOf(t.start)<=at&&at<m.instructions.indexOf(t.end));}
  static void reference(PrintWriter out,String caller,String owner,String name,String desc,int opcode,boolean field)throws Exception{
    if(!owner.startsWith(TARGET)||owner.startsWith(OWN))return;
    Res old=resolve(owner,name,desc,field,OLD,new HashSet<>()), now=resolve(owner,name,desc,field,NEW,new HashSet<>());
    boolean wantStatic=opcode==Opcodes.INVOKESTATIC||opcode==Opcodes.GETSTATIC||opcode==Opcodes.PUTSTATIC;
    String status=now==null?"MISSING":(((now.access&Opcodes.ACC_STATIC)!=0)!=wantStatic?"STATIC_MISMATCH":"RESOLVED");
    out.printf("%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s%n",status,caller,field?"field":"method",owner,name,desc,old==null?"UNRESOLVED_OLD":old.owner,now==null?"-":now.owner);
  }
  static void value(PrintWriter out,String caller,Object v)throws Exception{
    if(v instanceof Handle h){int t=h.getTag();boolean field=t<=Opcodes.H_PUTSTATIC;int op=switch(t){case Opcodes.H_GETFIELD->Opcodes.GETFIELD;case Opcodes.H_GETSTATIC->Opcodes.GETSTATIC;case Opcodes.H_PUTFIELD->Opcodes.PUTFIELD;case Opcodes.H_PUTSTATIC->Opcodes.PUTSTATIC;case Opcodes.H_INVOKESTATIC->Opcodes.INVOKESTATIC;default->Opcodes.INVOKEVIRTUAL;};reference(out,caller,h.getOwner(),h.getName(),h.getDesc(),op,field);}
    else if(v instanceof ConstantDynamic d){value(out,caller,d.getBootstrapMethod());for(int i=0;i<d.getBootstrapMethodArgumentCount();i++)value(out,caller,d.getBootstrapMethodArgument(i));}
  }
  public static void main(String[] a)throws Exception{
    if(a.length>5)TARGET=a[5];
    Path oldJar=Path.of(a[0]),newJar=Path.of(a[1]),candidate=Path.of(a[2]),outDir=Path.of(a[3]);Files.createDirectories(outDir);
    var oldBytes=read(oldJar);var newBytes=read(newJar);for(var e:oldBytes.entrySet())OLD.put(e.getKey(),parse(e.getValue()));for(var e:newBytes.entrySet())NEW.put(e.getKey(),parse(e.getValue()));
    if(a.length>4)for(String s:Files.readString(Path.of(a[4])).split(";")){Path p=Path.of(s.trim());if(Files.isRegularFile(p)&&s.endsWith(".jar")&&!p.equals(oldJar)&&!s.contains("useless_mod"))try{EXTRA.add(new ZipFile(p.toFile()));}catch(IOException ignored){}}
    Set<String> compared=new TreeSet<>();for(String n:oldBytes.keySet())if(n.startsWith(PREFIX+"content/recipe/AlloyFurnaceRecipeCatalog")||n.equals(PREFIX+"init/ClientSetup")||n.contains("/integration/jei/")||n.contains("/compat/jei/")||n.contains("/integration/dataenergistics/")||n.contains("MePatternAssembly")||n.contains("PassiveCraftingHatch")||n.contains("AdvancedAlloyFurnaceBlockEntity")||n.contains("MultiblockAlloyFurnaceCoreBlockEntity")||n.contains("PatternSlotItemHandler")||n.contains("AdvancedAlloyFurnaceMenu")||n.contains("RecoverableItemStackHandler")||n.contains("MultiblockPartData"))compared.add(n);
    if(TARGET.equals("mezz/jei/"))for(String n:oldBytes.keySet())if(n.equals("mezz/jei/library/recipes/RecipeLookup")||n.equals("mezz/jei/gui/recipes/RecipesGui")||n.equals("mezz/jei/library/util/IngredientSupplierHelper")||n.equals("mezz/jei/library/recipes/RecipeManagerInternal")||n.equals("mezz/jei/library/recipes/collect/RecipeMap"))compared.add(n);
    if(TARGET.equals(PREFIX))for(String n:oldBytes.keySet())if(n.startsWith(PREFIX+"content/recipe/adapters/enderio/VatFermentingRecipeAdapter")||n.startsWith(PREFIX+"content/recipe/adapters/justdirethings/jdte/InfusionRecipeAdapter"))compared.add(n);
    try(PrintWriter summary=new PrintWriter(Files.newBufferedWriter(outDir.resolve("method-comparison.tsv")))){
      summary.println("class\tmethod\tstatus");for(String n:compared){if(!NEW.containsKey(n)){summary.printf("%s\t-\tCLASS_REMOVED%n",n);continue;}var om=methods(OLD.get(n));var nm=methods(NEW.get(n));Set<String> keys=new TreeSet<>(om.keySet());keys.addAll(nm.keySet());for(String k:keys){String status=!om.containsKey(k)?"ADDED":!nm.containsKey(k)?"REMOVED":text(om.get(k)).equals(text(nm.get(k)))?"SEMANTICALLY_IDENTICAL":"CHANGED";summary.printf("%s\t%s\t%s%n",n,k,status);if(!status.equals("SEMANTICALLY_IDENTICAL")){String base=n.replace('/','_')+"_"+Integer.toUnsignedString(k.hashCode());if(om.containsKey(k))Files.writeString(outDir.resolve(base+"-old.txt"),k+"\n"+text(om.get(k)));if(nm.containsKey(k))Files.writeString(outDir.resolve(base+"-new.txt"),k+"\n"+text(nm.get(k)));}}}}
    try(PrintWriter out=new PrintWriter(Files.newBufferedWriter(outDir.resolve("reference-audit.tsv")))){
      out.println("status\tcaller\tkind\towner\tname\tdescriptor\tresolvedOldOwner\tresolvedNewOwner");
      for(var e:read(candidate).entrySet()){ClassNode c=parse(e.getValue());for(var m:c.methods){String caller=c.name+"."+m.name+m.desc;for(var i:m.instructions){if(i instanceof MethodInsnNode x)reference(out,caller,x.owner,x.name,x.desc,x.getOpcode(),false);else if(i instanceof FieldInsnNode x)reference(out,caller,x.owner,x.name,x.desc,x.getOpcode(),true);else if(i instanceof InvokeDynamicInsnNode x){value(out,caller,x.bsm);for(Object v:x.bsmArgs)value(out,caller,v);}else if(i instanceof LdcInsnNode x)value(out,caller,x.cst);}}}
    }
    if(TARGET.equals(PREFIX))try(PrintWriter p=new PrintWriter(Files.newBufferedWriter(outDir.resolve("focused-injection-audit.tsv")))){
      p.println("status\tcheck\tdetail");
      String assembly="content/blockentities/multiblock/MePatternAssemblyBlockEntity";
      MethodNode ctor=method(assembly,"<init>","(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V");
      int news=0,calls=0;for(var i:ctor.instructions){if(i instanceof TypeInsnNode t&&t.getOpcode()==Opcodes.NEW&&t.desc.equals(PREFIX+"content/blockentities/RecoverableItemStackHandler"))news++;if(i instanceof MethodInsnNode c&&c.owner.equals(PREFIX+"content/blockentities/RecoverableItemStackHandler")&&c.name.equals("<init>")&&c.desc.equals("(Ljava/util/function/IntSupplier;Ljava/util/function/Predicate;Ljava/lang/Runnable;)V"))calls++;}
      check(p,"assembly_unique_recoverable_new",news==1,"NEW occurrences="+news);
      check(p,"assembly_unique_recoverable_constructor",calls==1,"Exact (IntSupplier,Predicate,Runnable)V occurrences="+calls);
      var recoverable=NEW.get(PREFIX+"content/blockentities/RecoverableItemStackHandler");check(p,"recoverable_not_final",(recoverable.access&Opcodes.ACC_FINAL)==0,"access="+recoverable.access);
      String snapshot=PREFIX+"content/recipe/AlloyFurnaceRecipeCatalog$Snapshot";
      check(p,"snapshot_constructor_descriptor",NEW.get(snapshot).methods.stream().anyMatch(m->m.name.equals("<init>")&&m.desc.equals("(Ljava/util/List;Ljava/util/Map;Ljava/util/Map;Ljava/util/Map;Ljava/util/Map;JLjava/util/Map;Ljava/util/Map;Ljava/util/Set;)V")),"List,4Map,long,2Map,Set");
      String[] sameOwners={"content/blockentities/AdvancedAlloyFurnaceBlockEntity$2","content/blockentities/RecoverableItemStackHandler","inventory/slot/PatternSlotItemHandler","content/menus/AdvancedAlloyFurnaceMenu","content/blocks/multiblock/MePatternAssemblyBlock","core/component/MultiblockPartData","content/blockentities/multiblock/PassiveCraftingHatchBlockEntity","init/ClientSetup"};
      for(String owner:sameOwners){boolean same=true;var oldMethods=methods(OLD.get(PREFIX+owner));var newMethods=methods(NEW.get(PREFIX+owner));if(!oldMethods.keySet().equals(newMethods.keySet()))same=false;else for(String k:oldMethods.keySet())if(!text(oldMethods.get(k)).equals(text(newMethods.get(k))))same=false;check(p,"all_methods_semantically_identical:"+owner,same,"SKIP_DEBUG and SKIP_FRAMES; full method Textifier comparison");}
      for(String owner:new String[]{"content/blockentities/AdvancedAlloyFurnaceBlockEntity","content/blockentities/multiblock/MultiblockAlloyFurnaceCoreBlockEntity"}){var fields=NEW.get(PREFIX+owner).fields;check(p,"aeManager_shadow:"+owner,fields.stream().anyMatch(f->f.name.equals("aeManager")&&f.desc.equals("Lcom/sorrowmist/useless/content/machines/advanced_alloy_furnace/ae/AdvancedAlloyFurnaceAeManager;")&&(f.access&Opcodes.ACC_FINAL)!=0),"private final expected descriptor");}
      var menu=method("content/menus/AdvancedAlloyFurnaceMenu","tryMoveToMachine","(Lnet/minecraft/world/item/ItemStack;)Z");int patternCalls=0;for(var i:menu.instructions)if(i instanceof MethodInsnNode c&&c.owner.equals("appeng/api/crafting/PatternDetailsHelper")&&c.name.equals("isEncodedPattern")&&c.desc.equals("(Lnet/minecraft/world/item/ItemStack;)Z"))patternCalls++;
      check(p,"menu_encoded_pattern_redirect_count",patternCalls==1,"occurrences="+patternCalls);
    }
    if(TARGET.equals("mezz/jei/"))try(PrintWriter p=new PrintWriter(Files.newBufferedWriter(outDir.resolve("focused-injection-audit.tsv")))){
      p.println("status\tcheck\tdetail");var lookup=NEW.get("mezz/jei/library/recipes/RecipeLookup");
      check(p,"lookup_recipeType_shadow",lookup.fields.stream().anyMatch(f->f.name.equals("recipeType")&&f.desc.equals("Lmezz/jei/api/recipe/RecipeType;")&&(f.access&Opcodes.ACC_FINAL)!=0&&(f.access&Opcodes.ACC_PRIVATE)!=0),"private final RecipeType");
      check(p,"lookup_get_target",lookup.methods.stream().filter(m->m.name.equals("get")&&m.desc.equals("()Ljava/util/stream/Stream;")).count()==1,"get()Stream unique");
      var gui=NEW.get("mezz/jei/gui/recipes/RecipesGui");for(String name:new String[]{"show","showTypes","showRecipes","back","forward","open","updateLayout"}){var targets=gui.methods.stream().filter(m->m.name.equals(name)).toList();check(p,"gui_HEAD_"+name,targets.size()==1&&targets.getFirst().desc.endsWith(")V"),targets.stream().map(m->m.desc).toList().toString());}
      var helper=NEW.get("mezz/jei/library/util/IngredientSupplierHelper");int setCalls=0;boolean catchesBoth=false;for(var m:helper.methods)if(m.name.equals("getIngredientSupplier")){int count=0;for(var i:m.instructions)if(i instanceof MethodInsnNode c&&c.owner.equals("mezz/jei/api/recipe/category/IRecipeCategory")&&c.name.equals("setRecipe")&&c.desc.equals("(Lmezz/jei/api/gui/builder/IRecipeLayoutBuilder;Ljava/lang/Object;Lmezz/jei/api/recipe/IFocusGroup;)V")){count++;catchesBoth=catches(m,i,"java/lang/RuntimeException")&&catches(m,i,"java/lang/LinkageError");}setCalls+=count;p.printf("DETAIL\tsetRecipe_count_for_overload\t%s count=%d%n",m.desc,count);}
      check(p,"build_unique_setRecipe_redirect",setCalls==1,"all getIngredientSupplier overloads combined="+setCalls);check(p,"build_retains_native_catches",catchesBoth,"RuntimeException and LinkageError protect redirected call");
      var manager=NEW.get("mezz/jei/library/recipes/RecipeManagerInternal");int addCalls=0;boolean indexCatches=false;for(var m:manager.methods)if(m.name.equals("addRecipe"))for(var i:m.instructions)if(i instanceof MethodInsnNode c&&c.owner.equals("mezz/jei/library/recipes/collect/RecipeMap")&&c.name.equals("addRecipe")&&c.desc.equals("(Lmezz/jei/api/recipe/RecipeType;Ljava/lang/Object;Lmezz/jei/api/ingredients/IIngredientSupplier;)V")){addCalls++;indexCatches=catches(m,i,"java/lang/RuntimeException")&&catches(m,i,"java/lang/LinkageError");}
      check(p,"index_unique_addRecipe_redirect",addCalls==1,"addRecipe overloads combined="+addCalls);check(p,"index_retains_native_catches",indexCatches,"RuntimeException and LinkageError protect redirected call");
    }
    for(ZipFile z:EXTRA)z.close();System.out.println("Offline audit complete: "+outDir);
  }
}
