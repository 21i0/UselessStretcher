import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
public final class AssemblyPlacementAudit {
  static final String SLOT="com/sorrowmist/useless/stretcher/mixin/InstalledStretcherSlotMixin";
  static final String GUARD="com/sorrowmist/useless/stretcher/content/ae/InstalledStretcherPlacementGuard";
  static final String TARGET="com/sorrowmist/useless/inventory/slot/PatternSlotItemHandler";
  static final String ITEM="Lnet/minecraft/world/item/ItemStack;";
  static final String GUARD_DESC="(Lnet/neoforged/neoforge/items/SlotItemHandler;"+ITEM+")Ljava/lang/Boolean;";
  static void require(boolean pass,String message) { if(!pass) throw new IllegalStateException(message); }
  static ClassNode read(String jar,String name) throws Exception {
    try(ZipFile zip=new ZipFile(jar)) { var entry=zip.getEntry(name+".class");require(entry!=null,"Missing "+name);var node=new ClassNode();try(var in=zip.getInputStream(entry)){new ClassReader(in).accept(node,0);}return node; }
  }
  static AnnotationNode annotation(List<AnnotationNode> nodes,String desc) {
    var found=nodes==null?List.<AnnotationNode>of():nodes.stream().filter(a->a.desc.equals(desc)).toList();require(found.size()==1,"Expected exactly one annotation "+desc);return found.get(0);
  }
  static Object value(AnnotationNode annotation,String key) { for(int i=0;i<annotation.values.size();i+=2)if(annotation.values.get(i).equals(key))return annotation.values.get(i+1);return null; }
  static List<AnnotationNode> all(List<AnnotationNode> a,List<AnnotationNode> b){var result=new ArrayList<AnnotationNode>();if(a!=null)result.addAll(a);if(b!=null)result.addAll(b);return result;}
  public static void main(String[] args) throws Exception {
    ClassNode slot=read(args[0],SLOT),guard=read(args[0],GUARD);
    var mixin=annotation(all(slot.visibleAnnotations,slot.invisibleAnnotations),"Lorg/spongepowered/asm/mixin/Mixin;");
    require(value(mixin,"value") instanceof List<?> targets&&targets.size()==1&&targets.get(0).equals(Type.getObjectType(TARGET)),"Mixin target differs");
    var handlers=slot.methods.stream().filter(m->m.name.equals("uselessStretcher$accept")).toList();require(handlers.size()==1,"Unique handler missing");var handler=handlers.get(0);
    require(handler.desc.equals("("+ITEM+"Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V"),"Handler descriptor differs");
    var inject=annotation(all(handler.visibleAnnotations,handler.invisibleAnnotations),"Lorg/spongepowered/asm/mixin/injection/Inject;");
    require(value(inject,"method") instanceof List<?> methods&&methods.equals(List.of("mayPlace")),"Inject method differs");
    require(Boolean.TRUE.equals(value(inject,"cancellable")),"Inject must be cancellable");
    require(value(inject,"at") instanceof List<?> ats&&ats.size()==1&&ats.get(0) instanceof AnnotationNode at&&"HEAD".equals(value(at,"value")),"Inject must be exactly HEAD");
    int calls=0;for(var insn:handler.instructions)if(insn instanceof MethodInsnNode m&&m.owner.equals(GUARD)&&m.name.equals("mayPlace")){require(m.getOpcode()==Opcodes.INVOKESTATIC&&m.desc.equals(GUARD_DESC),"Guard call ABI differs");calls++;}require(calls==1,"Expected exactly one guard delegation");
    var guards=guard.methods.stream().filter(m->m.name.equals("mayPlace")).toList();require(guards.size()==1&&guards.get(0).desc.equals(GUARD_DESC)&&(guards.get(0).access&(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC))==(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC),"Public static guard ABI differs");
    System.out.println("candidate\tHEAD cancellable inject and guard ABI\tPASS");
    for(int i=1;i<args.length;i++){var target=read(args[i],TARGET);var methods=target.methods.stream().filter(m->m.name.equals("mayPlace")).toList();require(methods.size()==1&&methods.get(0).desc.equals("("+ITEM+")Z"),"Native mayPlace must be unique with exact ItemStack->boolean ABI");System.out.println(args[i]+"\tunique mayPlace(ItemStack)boolean\tPASS");}
  }
}