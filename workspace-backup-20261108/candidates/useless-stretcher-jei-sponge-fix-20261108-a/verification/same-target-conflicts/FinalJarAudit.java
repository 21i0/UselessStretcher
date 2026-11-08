import com.google.gson.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Independent member and normalized-class audit. No target class loading. */
public class FinalJarAudit {
    static final Gson G = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    static final String CLASS = "com/sorrowmist/useless/stretcher/mixin/CatalogJeiBuildTimingMixin.class";
    static final String META = "META-INF/neoforge.mods.toml";
    static Map<String,Object> m(Object... p) { Map<String,Object> r=new LinkedHashMap<>();for(int i=0;i<p.length;i+=2)r.put((String)p[i],p[i+1]);return r; }
    static String sha(byte[] b)throws Exception{return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
    static String sha(Path p)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");try(InputStream in=Files.newInputStream(p)){byte[] b=new byte[1024*1024];int n;while((n=in.read(b))>=0)md.update(b,0,n);}return HexFormat.of().withUpperCase().formatHex(md.digest());}
    static Map<String,byte[]> read(Path p)throws Exception{Map<String,byte[]> r=new TreeMap<>();try(ZipFile z=new ZipFile(p.toFile())){var en=z.entries();while(en.hasMoreElements()){ZipEntry e=en.nextElement();if(e.isDirectory())continue;try(InputStream in=z.getInputStream(e)){if(r.put(e.getName(),in.readAllBytes())!=null)throw new IllegalStateException("Duplicate member "+e.getName());}}}return r;}
    static byte[] normalize(byte[] bytes){ClassNode c=new ClassNode();new ClassReader(bytes).accept(c,ClassReader.SKIP_DEBUG);for(MethodNode method:c.methods){method.visibleAnnotations=null;method.invisibleAnnotations=null;}ClassWriter w=new ClassWriter(0);c.accept(w);return w.toByteArray();}
    static Object av(Object v){if(v instanceof AnnotationNode a)return ann(a);if(v instanceof Type t)return t.getDescriptor();if(v instanceof String[] a)return List.of(a);if(v instanceof List<?> l)return l.stream().map(FinalJarAudit::av).toList();return v;}
    static Map<String,Object> ann(AnnotationNode a){Map<String,Object> vals=new LinkedHashMap<>();if(a.values!=null)for(int i=0;i<a.values.size();i+=2)vals.put((String)a.values.get(i),av(a.values.get(i+1)));return m("descriptor",a.desc,"values",vals);}
    static List<Map<String,Object>> methods(byte[] bytes){ClassNode c=new ClassNode();new ClassReader(bytes).accept(c,ClassReader.SKIP_DEBUG);List<Map<String,Object>> r=new ArrayList<>();for(MethodNode method:c.methods){List<Object>a=new ArrayList<>();if(method.visibleAnnotations!=null)for(AnnotationNode n:method.visibleAnnotations)a.add(ann(n));if(method.invisibleAnnotations!=null)for(AnnotationNode n:method.invisibleAnnotations)a.add(ann(n));r.add(m("method",method.name+method.desc,"annotations",a));}return r;}
    static List<String> debug(byte[]bytes){ClassNode c=new ClassNode();new ClassReader(bytes).accept(c,0);List<String>r=new ArrayList<>();r.add("sourceFile="+c.sourceFile+",sourceDebug="+c.sourceDebug);for(MethodNode method:c.methods){List<Integer>lines=new ArrayList<>();for(AbstractInsnNode n=method.instructions.getFirst();n!=null;n=n.getNext())if(n instanceof LineNumberNode l)lines.add(l.line);r.add(method.name+method.desc+" lines="+lines+",locals="+(method.localVariables==null?0:method.localVariables.size()));}return r;}
    public static void main(String[] args)throws Exception{
        Path old=Path.of(args[0]),next=Path.of(args[1]),out=Path.of(args[2]);Files.createDirectories(out);
        Map<String,byte[]> a=read(old),b=read(next);List<String>added=new ArrayList<>(),removed=new ArrayList<>(),same=new ArrayList<>();List<Map<String,Object>>changed=new ArrayList<>();
        Set<String>names=new TreeSet<>();names.addAll(a.keySet());names.addAll(b.keySet());
        for(String n:names){if(!a.containsKey(n))added.add(n);else if(!b.containsKey(n))removed.add(n);else if(Arrays.equals(a.get(n),b.get(n)))same.add(n);else changed.add(m("member",n,"beforeBytes",a.get(n).length,"afterBytes",b.get(n).length,"beforeSha256",sha(a.get(n)),"afterSha256",sha(b.get(n))));}
        Set<String> actualChanges=new TreeSet<>();for(Map<String,Object>c:changed)actualChanges.add((String)c.get("member"));
        byte[] an=normalize(a.get(CLASS)),bn=normalize(b.get(CLASS));boolean normalizedEqual=Arrays.equals(an,bn);
        String oldMeta=new String(a.get(META),StandardCharsets.UTF_8),newMeta=new String(b.get(META),StandardCharsets.UTF_8);
        String oldVersion="1.21.1-1.6.4-catalog-P4-assembly-lock-zh-hud-base2465-jei1970";
        String newVersion=oldVersion+"-sponge1";
        boolean versionOnly=newMeta.equals(oldMeta.replace("version=\""+oldVersion+"\"","version=\""+newVersion+"\""));
        boolean pass=added.isEmpty()&&removed.isEmpty()&&actualChanges.equals(new TreeSet<>(List.of(CLASS,META)))&&normalizedEqual&&versionOnly&&same.size()==680;
        Files.write(out.resolve("baseline-timing.class"),a.get(CLASS));Files.write(out.resolve("candidate-timing.class"),b.get(CLASS));Files.write(out.resolve("baseline-timing-normalized.class"),an);Files.write(out.resolve("candidate-timing-normalized.class"),bn);
        Files.writeString(out.resolve("baseline-neoforge.mods.toml"),oldMeta,StandardCharsets.UTF_8);Files.writeString(out.resolve("candidate-neoforge.mods.toml"),newMeta,StandardCharsets.UTF_8);
        Map<String,Object>report=m("pass",pass,"method","Direct ZIP reads, SHA-256 per member, ASM canonical rewrite with SKIP_DEBUG and only method-level visible/invisible annotations removed; no class loading", "baseline",m("path",old.toString(),"sha256",sha(old),"bytes",Files.size(old),"members",a.size()),"candidate",m("path",next.toString(),"sha256",sha(next),"bytes",Files.size(next),"members",b.size()),"added",added,"removed",removed,"changed",changed,"unchangedCount",same.size(),"unchangedMembers",same,"normalizedTimingBytecodeEqual",normalizedEqual,"baselineNormalizedSha256",sha(an),"candidateNormalizedSha256",sha(bn),"metadataVersionOnly",versionOnly,"oldVersion",oldVersion,"newVersion",newVersion,"baselineMethods",methods(a.get(CLASS)),"candidateMethods",methods(b.get(CLASS)),"baselineDebug",debug(a.get(CLASS)),"candidateDebug",debug(b.get(CLASS)));
        Files.writeString(out.resolve("FINAL-JAR-AUDIT.json"),G.toJson(report)+"\n",StandardCharsets.UTF_8);
        System.out.println(G.toJson(m("pass",pass,"baselineSha256",sha(old),"candidateSha256",sha(next),"added",added.size(),"removed",removed.size(),"changed",actualChanges,"unchanged",same.size(),"normalizedTimingBytecodeEqual",normalizedEqual,"metadataVersionOnly",versionOnly,"reportSha256",sha(out.resolve("FINAL-JAR-AUDIT.json")))));
        if(!pass)throw new IllegalStateException("Final JAR did not satisfy strict two-member audit");
    }
}
