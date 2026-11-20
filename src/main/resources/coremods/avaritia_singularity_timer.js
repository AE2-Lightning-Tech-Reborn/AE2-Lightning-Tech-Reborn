var Opcodes = Java.type("org.objectweb.asm.Opcodes");
var AbstractInsnNode = Java.type("org.objectweb.asm.tree.AbstractInsnNode");
var InsnNode = Java.type("org.objectweb.asm.tree.InsnNode");

var SINGULARITY_ITEM = "committee/nova/mods/avaritia/common/item/singularity/SingularityItem";

function initializeCoreMod() {
    return {
        "avaritia_singularity_timer": {
            "target": {
                "type": "CLASS",
                "name": "committee.nova.mods.avaritia.common.item.singularity.SingularityItem"
            },
            "transformer": function(classNode) {
                if (classNode.name !== SINGULARITY_ITEM) {
                    return classNode;
                }
                for (var index = 0; index < classNode.methods.size(); index++) {
                    var method = classNode.methods.get(index);
                    if (method.name !== "<clinit>") {
                        continue;
                    }
                    var changed = false;
                    var instruction = method.instructions.getFirst();
                    while (instruction !== null) {
                        var previous = instruction.getPrevious();
                        if (instruction.getType() === AbstractInsnNode.METHOD_INSN
                                && instruction.getOpcode() === Opcodes.INVOKESPECIAL
                                && instruction.owner === "java/util/Timer"
                                && instruction.name === "<init>"
                                && instruction.desc === "(Ljava/lang/String;)V"
                                && previous !== null
                                && previous.getType() === AbstractInsnNode.LDC_INSN
                                && previous.cst === "Singularity Icon Timer") {
                            method.instructions.insertBefore(instruction, new InsnNode(Opcodes.ICONST_1));
                            instruction.desc = "(Ljava/lang/String;Z)V";
                            changed = true;
                        }
                        instruction = instruction.getNext();
                    }
                    if (changed) {
                        method.maxStack += 1;
                    }
                }
                return classNode;
            }
        }
    };
}
