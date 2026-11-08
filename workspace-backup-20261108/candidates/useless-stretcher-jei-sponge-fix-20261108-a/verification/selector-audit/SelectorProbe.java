import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.injection.struct.MemberInfo;
import org.spongepowered.asm.mixin.injection.selectors.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Uses Sponge's real MemberInfo parser/matcher. Does not bootstrap Mixin or ModLauncher. */
public final class SelectorProbe {
  static Map<String,ClassNode> read(String file)throws Exception{Map<String,ClassNode> out=new HashMap<>();try(var z=new ZipFile(file)){var es=z.entries();while(es.hasMoreElements()){var e=es.nextElement();if(!e.getName().endsWith(".class"))continue;ClassNode c=new ClassNode();new ClassReader(z.getInputStream(e).readAllBytes()).accept(c,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);out.put(c.name,c);}}return out;}
  static Object attr(AnnotationNode a,String key){if(a.values!=null)for(int i=0;i<a.values.size();i+=2)if(a.values.get(i).equals(key))return a.values.get(i+1);return null;}
  static List<AnnotationNode> anns(ClassNode c){List<AnnotationNode> a=new ArrayList<>();if(c.visibleAnnotations!=null)a.addAll(c.visibleAnnotations);if(c.invisibleAnnotations!=null)a.addAll(c.invisibleAnnotations);return a;}
  static List<MethodNode> select(ClassNode c,String input){ITargetSelector s=MemberInfo.parse(input,null).configure(ITargetSelector.Configure.SELECT_MEMBER);List<MethodNode> out=new ArrayList<>();int found=0;for(var m:c.methods)if(s.match(ElementNode.of(c,m)).isExactMatch()){out.add(m);if(++found>=s.getMaxMatchCount())break;}return out;}
  static List<AnnotationNode> ats(Object value){if(value instanceof AnnotationNode a)return List.of(a);if(value instanceof List<?> a)return a.stream().filter(v->v instanceof AnnotationNode).map(v->(AnnotationNode)v).toList();return List.of();}
  static int atCount(MethodNode m,AnnotationNode at){String kind=(String)attr(at,"value"),target=(String)attr(at,"target");if(kind.equals("HEAD"))return m.instructions.size()>0?1:0;if(kind.equals("RETURN")){int count=0;for(var i:m.instructions)if(i.getOpcode()>=Opcodes.IRETURN&&i.getOpcode()<=Opcodes.RETURN)count++;return count;}if(kind.equals("INVOKE")){ITargetSelector s=MemberInfo.parse(target,null).configure(ITargetSelector.Configure.SELECT_INSTRUCTION);int count=0;for(var i:m.instructions)if(i instanceof MethodInsnNode&&s.match(ElementNode.of(i)).isExactMatch())count++;return count;}return -1;}
  public static void main(String[] a)throws Exception{
    var candidate=read(a[0]);var targets=read(a[1]);targets.putAll(read(a[2]));Path out=Path.of(a[3]);Files.createDirectories(out);
    if(a.length>4){ClassNode override=new ClassNode();new ClassReader(Files.readAllBytes(Path.of(a[4]))).accept(override,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);if(!override.name.equals("com/sorrowmist/useless/stretcher/mixin/CatalogJeiBuildTimingMixin"))throw new IllegalArgumentException("Unexpected override class "+override.name);candidate.put(override.name,override);}
    var build=candidate.get("com/sorrowmist/useless/stretcher/mixin/CatalogJeiBuildTimingMixin");
    try(PrintWriter p=new PrintWriter(Files.newBufferedWriter(out.resolve("fixed-build-annotation.tsv")))){p.println("handler\trequire\tallow\tselectors\tstaticHandler");for(var m:build.methods)if(m.visibleAnnotations!=null)for(var an:m.visibleAnnotations)if(an.desc.equals("Lorg/spongepowered/asm/mixin/injection/Redirect;")){p.printf("%s\t%s\t%s\t%s\t%s%n",m.name+m.desc,attr(an,"require"),attr(an,"allow"),attr(an,"method"),(m.access&Opcodes.ACC_STATIC)!=0);}}
    try(PrintWriter p=new PrintWriter(Files.newBufferedWriter(out.resolve("actual-sponge-selector-results.tsv")))){
      p.println("mixin\thandler\ttargetClass\tselector\tmaxMatchCount\tselectedMethod\tat\tatTarget\tactualInstructionHits");
      for(String shortName:List.of("BudgetedRecipeCatalogMixin","BudgetedClientCatalogMixin","CatalogJeiTimingMixin","CatalogJeiBuildTimingMixin","CatalogJeiIndexTimingMixin","CatalogJeiLookupMixin","CatalogJeiGuiMixin")){
        var c=candidate.get("com/sorrowmist/useless/stretcher/mixin/"+shortName);List<String> owners=new ArrayList<>();for(var an:anns(c))if(an.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")){Object v=attr(an,"targets");if(v instanceof List<?> l)for(Object x:l)owners.add(x.toString().replace('.','/'));v=attr(an,"value");if(v instanceof List<?> l)for(Object x:l)owners.add(((Type)x).getInternalName());}
        for(var handler:c.methods){if(handler.visibleAnnotations==null)continue;for(var injection:handler.visibleAnnotations){if(!injection.desc.startsWith("Lorg/spongepowered/asm/mixin/injection/"))continue;Object value=attr(injection,"method");if(!(value instanceof List<?> selectors))continue;for(String owner:owners){ClassNode target=targets.get(owner);if(target==null)throw new IllegalStateException("No exact target class "+owner);for(Object raw:selectors){String selector=raw.toString();var selected=select(target,selector);var parsed=MemberInfo.parse(selector,null).configure(ITargetSelector.Configure.SELECT_MEMBER);for(var at:ats(attr(injection,"at"))){if(selected.isEmpty())p.printf("%s\t%s\t%s\t%s\t%d\tNO_METHOD\t%s\t%s\t0%n",shortName,handler.name+handler.desc,owner,selector,parsed.getMaxMatchCount(),attr(at,"value"),attr(at,"target"));for(var m:selected)p.printf("%s\t%s\t%s\t%s\t%d\t%s\t%s\t%s\t%d%n",shortName,handler.name+handler.desc,owner,selector,parsed.getMaxMatchCount(),m.name+m.desc,attr(at,"value"),attr(at,"target"),atCount(m,at));}}}}}
      }
    }
    String oldSelector="getIngredientSupplier(Ljava/lang/Object;Lmezz/jei/api/recipe/category/IRecipeCategory;Lmezz/jei/api/runtime/IIngredientManager;)Lmezz/jei/library/ingredients/RecipeIngredientSupplier;";
    String newSelector="getIngredientSupplier(Ljava/lang/Object;Lmezz/jei/api/recipe/category/IRecipeCategory;Lmezz/jei/api/runtime/IIngredientManager;Ljava/util/Set;)Lmezz/jei/library/ingredients/RecipeIngredientSupplier;";
    try(PrintWriter p=new PrintWriter(Files.newBufferedWriter(out.resolve("build-selector-correction.tsv")))){p.println("selector\tmaxMatchCount\tselectedMethod\tsetRecipeCalls");var target=targets.get("mezz/jei/library/util/IngredientSupplierHelper");for(String s:List.of("getIngredientSupplier",oldSelector,newSelector)){var parsed=MemberInfo.parse(s,null).configure(ITargetSelector.Configure.SELECT_MEMBER);for(var m:select(target,s)){int count=0;for(var i:m.instructions)if(i instanceof MethodInsnNode call&&call.name.equals("setRecipe")&&call.owner.equals("mezz/jei/api/recipe/category/IRecipeCategory"))count++;p.printf("%s\t%d\t%s\t%d%n",s,parsed.getMaxMatchCount(),m.name+m.desc,count);}}}
    System.out.println("Parsed actual Sponge 0.16.4 selectors without bootstrapping Mixin: "+out);
  }
}
