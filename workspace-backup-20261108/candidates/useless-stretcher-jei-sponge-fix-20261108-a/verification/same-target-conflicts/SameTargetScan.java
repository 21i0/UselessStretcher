import com.google.gson.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Offline ZIP/ASM inspection only. Never loads a class from a scanned mod. */
public class SameTargetScan {
    static final List<String> TARGETS = List.of(
        "mezz.jei.library.util.IngredientSupplierHelper",
        "mezz.jei.library.recipes.RecipeManagerInternal",
        "mezz.jei.library.recipes.RecipeLookup",
        "mezz.jei.gui.recipes.RecipesGui",
        "com.sorrowmist.useless.compat.jei.JEIPlugin");
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    static final List<Map<String,Object>> jars = new ArrayList<>();
    static final List<Map<String,Object>> configs = new ArrayList<>();
    static final List<Map<String,Object>> mixins = new ArrayList<>();
    static final List<Map<String,Object>> matches = new ArrayList<>();
    static final List<Map<String,Object>> references = new ArrayList<>();
    static final List<Map<String,Object>> coremods = new ArrayList<>();
    static final List<Map<String,Object>> errors = new ArrayList<>();
    static Path output;
    static long classCount, textCount;

    static Map<String,Object> map(Object... pairs) {
        Map<String,Object> m = new LinkedHashMap<>();
        for (int i=0; i<pairs.length; i+=2) m.put((String)pairs[i], pairs[i+1]);
        return m;
    }
    static String sha(byte[] data) throws Exception {
        return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
    static String sha(Path p) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(p)) {
            byte[] b = new byte[1024*1024]; int n;
            while ((n=in.read(b))>=0) md.update(b,0,n);
        }
        return HexFormat.of().withUpperCase().formatHex(md.digest());
    }
    static void save(String name,Object value) throws Exception {
        Files.writeString(output.resolve(name), GSON.toJson(value)+"\n", StandardCharsets.UTF_8);
    }
    static List<String> targetRefs(String s) {
        List<String> found = new ArrayList<>();
        for (String t : TARGETS) if (s.contains(t)||s.contains(t.replace('.','/'))) found.add(t);
        return found;
    }
    static Object value(Object v) {
        if (v instanceof AnnotationNode a) return annotation(a);
        if (v instanceof Type t) return map("type",t.getClassName(),"descriptor",t.getDescriptor());
        if (v instanceof String[] e) return map("enumDescriptor",e[0],"value",e[1]);
        if (v instanceof List<?> l) return l.stream().map(SameTargetScan::value).toList();
        return v;
    }
    static Map<String,Object> annotation(AnnotationNode a) {
        Map<String,Object> vals = new LinkedHashMap<>();
        if (a.values!=null) for (int i=0;i<a.values.size();i+=2) vals.put((String)a.values.get(i),value(a.values.get(i+1)));
        return map("descriptor",a.desc,"values",vals);
    }
    static List<AnnotationNode> annotations(List<AnnotationNode> a,List<AnnotationNode> b) {
        List<AnnotationNode> result = new ArrayList<>();
        if(a!=null) result.addAll(a); if(b!=null) result.addAll(b); return result;
    }
    static List<String> mixinTargets(AnnotationNode a) {
        List<String> result = new ArrayList<>();
        if(a.values!=null) for(int i=0;i<a.values.size();i+=2) {
            if (!List.of("value","targets").contains(a.values.get(i))) continue;
            Object v = a.values.get(i+1);
            List<?> values = v instanceof List<?> l ? l : List.of(v);
            for(Object x : values) result.add(x instanceof Type t ? t.getClassName() : x.toString().replace('/','.'));
        }
        return result;
    }
    static int priority(AnnotationNode a) {
        if(a.values!=null) for(int i=0;i<a.values.size();i+=2) if("priority".equals(a.values.get(i))) return ((Number)a.values.get(i+1)).intValue();
        return 1000;
    }
    static void process(String jar,String entry,byte[] bytes) throws Exception {
        if(entry.endsWith(".class")) {
            classCount++;
            String cp = new String(bytes,StandardCharsets.ISO_8859_1);
            List<String> refs = targetRefs(cp);
            boolean couldMixin = cp.contains("org/spongepowered/asm/mixin/Mixin");
            if(refs.isEmpty()&&!couldMixin) return;
            ClassNode cn = new ClassNode();
            new ClassReader(bytes).accept(cn,ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
            List<AnnotationNode> anns = annotations(cn.visibleAnnotations,cn.invisibleAnnotations);
            AnnotationNode mixin = anns.stream().filter(a->a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst().orElse(null);
            if(!refs.isEmpty()) references.add(map("jar",jar,"entry",entry,"class",cn.name.replace('/','.'),"isMixin",mixin!=null,"targetsReferenced",refs));
            if(mixin==null) return;
            List<String> targets = mixinTargets(mixin);
            Map<String,Object> rec = map("jar",jar,"entry",entry,"class",cn.name.replace('/','.'),"mixinTargets",targets,"priority",priority(mixin),"mixinAnnotation",annotation(mixin));
            mixins.add(rec);
            if(targets.stream().noneMatch(TARGETS::contains)) return;
            List<Map<String,Object>> methods = new ArrayList<>();
            for(MethodNode mn : cn.methods) {
                List<AnnotationNode> methodAnns = annotations(mn.visibleAnnotations,mn.invisibleAnnotations);
                if(methodAnns.isEmpty()) continue;
                methods.add(map("name",mn.name,"descriptor",mn.desc,"access",mn.access,"annotations",methodAnns.stream().map(SameTargetScan::annotation).toList()));
            }
            List<Map<String,Object>> fields = new ArrayList<>();
            for(FieldNode fn : cn.fields) {
                List<AnnotationNode> fieldAnns = annotations(fn.visibleAnnotations,fn.invisibleAnnotations);
                if(!fieldAnns.isEmpty()) fields.add(map("name",fn.name,"descriptor",fn.desc,"access",fn.access,"annotations",fieldAnns.stream().map(SameTargetScan::annotation).toList()));
            }
            rec.put("allClassAnnotations",anns.stream().map(SameTargetScan::annotation).toList());
            rec.put("methods",methods);rec.put("fields",fields);rec.put("classSha256",sha(bytes));
            String evidenceKey = sha(jar.getBytes(StandardCharsets.UTF_8)).substring(0,12)+"/"+entry;
            Path p = output.resolve("matched-classes").resolve(evidenceKey);
            Files.createDirectories(p.getParent());Files.write(p,bytes);
            rec.put("classEvidence",output.relativize(p).toString());
            matches.add(rec);
        } else if(entry.endsWith(".json")||entry.endsWith(".js")||entry.endsWith(".toml")||entry.endsWith(".cfg")||entry.endsWith("MANIFEST.MF")) {
            textCount++;
            String s = new String(bytes,StandardCharsets.UTF_8);
            List<String> refs = targetRefs(s);
            if(!refs.isEmpty()) references.add(map("jar",jar,"entry",entry,"isClass",false,"targetsReferenced",refs,"content",s));
            if(entry.equals("META-INF/coremods.json")||entry.endsWith(".js")) coremods.add(map("jar",jar,"entry",entry,"targetsReferenced",refs,"content",s));
            if(!entry.endsWith(".json")) return;
            try {
                JsonElement el = JsonParser.parseString(s);
                if(el.isJsonObject()) {
                    JsonObject o=el.getAsJsonObject();
                    if(o.has("package")&&(o.has("mixins")||o.has("client")||o.has("server"))) configs.add(map("jar",jar,"entry",entry,"sha256",sha(bytes),"configuration",o,"content",s));
                }
            } catch(JsonParseException ignored) {}
        }
    }
    static void scanNested(String name,byte[] data,int depth) {
        if(depth>4) { errors.add(map("jar",name,"error","Nested JAR depth exceeds 4"));return; }
        try {
            jars.add(map("jar",name,"bytes",data.length,"sha256",sha(data),"nested",true));
            try(ZipInputStream zis=new ZipInputStream(new ByteArrayInputStream(data))) {
                ZipEntry e;
                while((e=zis.getNextEntry())!=null) {
                    if(e.isDirectory()) continue;
                    byte[] b=zis.readAllBytes();
                    if(e.getName().endsWith(".jar")) scanNested(name+"!/"+e.getName(),b,depth+1);
                    else try {process(name,e.getName(),b);}catch(Throwable ex){errors.add(map("jar",name,"entry",e.getName(),"error",ex.toString()));}
                }
            }
        }catch(Throwable ex){errors.add(map("jar",name,"error",ex.toString()));}
    }
    static void scan(Path path) throws Exception {
        String name=path.getFileName().toString();
        jars.add(map("jar",name,"path",path.toAbsolutePath().toString(),"bytes",Files.size(path),"sha256",sha(path),"nested",false));
        try(ZipFile zip=new ZipFile(path.toFile())) {
            Enumeration<? extends ZipEntry> ens=zip.entries();
            while(ens.hasMoreElements()) {
                ZipEntry e=ens.nextElement();if(e.isDirectory())continue;
                String en=e.getName();
                if(!(en.endsWith(".jar")||en.endsWith(".class")||en.endsWith(".json")||en.endsWith(".js")||en.endsWith(".toml")||en.endsWith(".cfg")||en.endsWith("MANIFEST.MF")))continue;
                try(InputStream in=zip.getInputStream(e)) {
                    byte[] bytes=in.readAllBytes();
                    if(en.endsWith(".jar"))scanNested(name+"!/"+en,bytes,1);
                    else try{process(name,en,bytes);}catch(Throwable ex){errors.add(map("jar",name,"entry",en,"error",ex.toString()));}
                }
            }
        }
    }
    public static void main(String[] args) throws Exception {
        Path source=Path.of(args[0]);output=Path.of(args[1]);Files.createDirectories(output);
        long start=System.nanoTime();
        List<Path> paths;
        try(var stream=Files.list(source)){paths=stream.filter(p->p.getFileName().toString().endsWith(".jar")).sorted().toList();}
        int n=0;for(Path p:paths){scan(p);if(++n%50==0)System.out.println("Scanned "+n+" / "+paths.size()+" primary JARs; exact Mixins="+matches.size());}
        for(Map<String,Object> m:matches) {
            String c=(String)m.get("class"),jar=(String)m.get("jar");
            List<Map<String,Object>> uses=new ArrayList<>();
            for(Map<String,Object> cfg:configs) {
                if(!jar.equals(cfg.get("jar")))continue;
                JsonObject o=(JsonObject)cfg.get("configuration");String pkg=o.get("package").getAsString();
                for(String side:List.of("mixins","client","server"))if(o.has(side)&&o.get(side).isJsonArray())for(JsonElement e:o.getAsJsonArray(side)) {
                    String full=pkg+"."+e.getAsString();
                    if(full.equals(c))uses.add(map("entry",cfg.get("entry"),"side",side,"configPriority",o.has("priority")?o.get("priority").getAsInt():1000,"plugin",o.has("plugin")?o.get("plugin").getAsString():null,"configuration",o));
                }
            }
            m.put("configuredIn",uses);
        }
        save("jar-inventory.json",jars);save("mixin-configurations.json",configs);save("all-mixin-targets.json",mixins);save("same-target-mixins.json",matches);save("target-references.json",references);save("coremod-inventory.json",coremods);save("scan-errors.json",errors);
        Map<String,Object> summary=map("source",source.toAbsolutePath().toString(),"targets",TARGETS,"primaryJars",paths.size(),"totalJarsIncludingNested",jars.size(),"classesScanned",classCount,"textEntriesScanned",textCount,"mixinConfigurations",configs.size(),"mixinClasses",mixins.size(),"matchingMixinClasses",matches.size(),"targetReferenceEntries",references.size(),"coremodAndJsEntries",coremods.size(),"errors",errors.size(),"seconds",(System.nanoTime()-start)/1e9,"method","Direct ZIP reads, Gson JSON parsing, ASM class metadata; no mod class loading or launch");
        save("SUMMARY.json",summary);System.out.println(GSON.toJson(summary));
    }
}
