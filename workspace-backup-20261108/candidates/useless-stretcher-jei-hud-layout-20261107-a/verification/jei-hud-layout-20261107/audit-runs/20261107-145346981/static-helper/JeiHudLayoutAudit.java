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