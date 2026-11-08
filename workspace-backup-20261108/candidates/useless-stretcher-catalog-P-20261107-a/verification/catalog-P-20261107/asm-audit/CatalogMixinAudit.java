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