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