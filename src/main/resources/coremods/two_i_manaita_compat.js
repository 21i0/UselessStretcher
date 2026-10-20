/*
 * ManaitaPlus-Legacy 0.1.0 excludes Mixins on its sen.* classes via its
 * LaunchPluginHandler. Forge coremods are ITransformers in TransformStore,
 * not launch plugins. Target ONLY its three public damage/removal entry classes;
 * never patch the launcher, exempt the mod globally, or scan/resurrect entities.
 */
function initializeCoreMod() {
    var Opcodes = Java.type('org.objectweb.asm.Opcodes');
    var InsnList = Java.type('org.objectweb.asm.tree.InsnList');
    var VarInsnNode = Java.type('org.objectweb.asm.tree.VarInsnNode');
    var TypeInsnNode = Java.type('org.objectweb.asm.tree.TypeInsnNode');
    var FieldInsnNode = Java.type('org.objectweb.asm.tree.FieldInsnNode');
    var JumpInsnNode = Java.type('org.objectweb.asm.tree.JumpInsnNode');
    var InsnNode = Java.type('org.objectweb.asm.tree.InsnNode');
    var LabelNode = Java.type('org.objectweb.asm.tree.LabelNode');
    var FrameNode = Java.type('org.objectweb.asm.tree.FrameNode');
    var ASMAPI = Java.type('net.minecraftforge.coremod.api.ASMAPI');
    var doll = 'com/sorrowmist/useless/stretcher/content/entity/TwoIDollEntity';
    var entity = 'Lnet/minecraft/world/entity/Entity;';
    var player = 'Lnet/minecraft/world/entity/player/Player;';
    var markerClass = 'sen/manaita_plus_legacy/common/util/entity/ManaitaPlusLegacyEntityData';

    function guard(method, entityLocal, returnBoolean, onlyDestructiveMarks) {
        var code = new InsnList();
        var proceed = new LabelNode();
        code.add(new VarInsnNode(Opcodes.ALOAD, entityLocal));
        code.add(new TypeInsnNode(Opcodes.INSTANCEOF, doll));
        code.add(new JumpInsnNode(Opcodes.IFEQ, proceed));
        if (onlyDestructiveMarks) {
            // Leave manaita/anti flags untouched, even on the doll itself.
            var deny = new LabelNode();
            code.add(new VarInsnNode(Opcodes.ALOAD, 0));
            code.add(new FieldInsnNode(Opcodes.GETSTATIC, markerClass, 'death', 'L' + markerClass + ';'));
            code.add(new JumpInsnNode(Opcodes.IF_ACMPEQ, deny));
            code.add(new VarInsnNode(Opcodes.ALOAD, 0));
            code.add(new FieldInsnNode(Opcodes.GETSTATIC, markerClass, 'remove', 'L' + markerClass + ';'));
            code.add(new JumpInsnNode(Opcodes.IF_ACMPNE, proceed));
            code.add(deny);
            code.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        }
        if (returnBoolean) code.add(new InsnNode(Opcodes.ICONST_0));
        code.add(new InsnNode(returnBoolean ? Opcodes.IRETURN : Opcodes.RETURN));
        code.add(proceed);
        code.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        method.instructions.insert(code);
        method.maxStack = Math.max(method.maxStack, 2);
    }

    function protectUtilities(node) {
        var patched = 0;
        for (var i = 0; i < node.methods.size(); i++) {
            var method = node.methods.get(i);
            if ((method.access & Opcodes.ACC_STATIC) === 0) continue;
            var name = String(method.name), desc = String(method.desc);
            var attack = name === 'attack' && (desc === '(' + entity + player + 'Z)V'
                    || desc === '(' + entity + player + 'I)V');
            var removal = (name === 'removeOnServer' || name === 'removeOnClient' || name === 'killOnClient')
                    && desc === '(' + entity + ')V';
            if (attack || removal) {
                guard(method, 0, false, false);
                patched++;
            }
        }
        ASMAPI.log(patched > 0 ? 'INFO' : 'WARN', '[2i compatibility] Manaita utility guards: ' + node.name + ' = ' + patched);
        return node;
    }

    function protectMarks(node) {
        var patched = 0;
        for (var i = 0; i < node.methods.size(); i++) {
            var method = node.methods.get(i);
            if ((method.access & Opcodes.ACC_STATIC) !== 0) continue;
            var name = String(method.name), desc = String(method.desc);
            var write = (name === 'add' && desc === '(' + entity + ')V')
                    || (name === 'putInt' && desc === '(' + entity + 'I)V');
            var read = (name === 'accept' || name === 'acceptSide') && desc === '(' + entity + ')Z';
            if (write || read) {
                guard(method, 1, read, true);
                patched++;
            }
        }
        ASMAPI.log(patched > 0 ? 'INFO' : 'WARN', '[2i compatibility] Manaita destructive-marker guards = ' + patched);
        return node;
    }

    return {
        'two_i_server_entry_guards': {
            'target': { 'type': 'CLASS', 'name': 'sen.manaita_plus_legacy.common.util.ManaitaPlusUtils' },
            'transformer': protectUtilities
        },
        'two_i_client_entry_guards': {
            'target': { 'type': 'CLASS', 'name': 'sen.manaita_plus_legacy.common.util.ManaitaPlusUtils$Client' },
            'transformer': protectUtilities
        },
        'two_i_destructive_mark_guards': {
            'target': { 'type': 'CLASS', 'name': 'sen.manaita_plus_legacy.common.util.entity.ManaitaPlusLegacyEntityData' },
            'transformer': protectMarks
        }
    };
}
