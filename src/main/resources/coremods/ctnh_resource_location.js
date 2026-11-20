var Opcodes = Java.type("org.objectweb.asm.Opcodes");
var MethodNode = Java.type("org.objectweb.asm.tree.MethodNode");
var TypeInsnNode = Java.type("org.objectweb.asm.tree.TypeInsnNode");
var InsnNode = Java.type("org.objectweb.asm.tree.InsnNode");
var VarInsnNode = Java.type("org.objectweb.asm.tree.VarInsnNode");
var MethodInsnNode = Java.type("org.objectweb.asm.tree.MethodInsnNode");

var RESOURCE_LOCATION = "net/minecraft/resources/ResourceLocation";
var PARSE_DESCRIPTOR = "(Ljava/lang/String;)L" + RESOURCE_LOCATION + ";";
var NAMESPACED_DESCRIPTOR = "(Ljava/lang/String;Ljava/lang/String;)L" + RESOURCE_LOCATION + ";";

function initializeCoreMod() {
    return {
        "ctnh_resource_location": {
            "target": {
                "type": "CLASS",
                "name": "net.minecraft.resources.ResourceLocation"
            },
            "transformer": function(classNode) {
                if (classNode.name !== RESOURCE_LOCATION) {
                    return classNode;
                }
                var hasStringConstructor = false;
                var hasNamespacedConstructor = false;
                var hasParser = false;
                var hasNamespacedFactory = false;
                for (var index = 0; index < classNode.methods.size(); index++) {
                    var method = classNode.methods.get(index);
                    if (method.name === "parse" && method.desc === PARSE_DESCRIPTOR) {
                        hasParser = true;
                    }
                    if (method.name === "fromNamespaceAndPath" && method.desc === NAMESPACED_DESCRIPTOR) {
                        hasNamespacedFactory = true;
                    }
                    if (method.name === "<init>" && method.desc === "(Ljava/lang/String;)V") {
                        hasStringConstructor = true;
                    }
                    if (method.name === "<init>" && method.desc === "(Ljava/lang/String;Ljava/lang/String;)V") {
                        hasNamespacedConstructor = true;
                    }
                }

                if (!hasParser && hasStringConstructor) {
                    var parse = new MethodNode(
                            Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                            "parse", PARSE_DESCRIPTOR, null, null);
                    parse.instructions.add(new TypeInsnNode(Opcodes.NEW, RESOURCE_LOCATION));
                    parse.instructions.add(new InsnNode(Opcodes.DUP));
                    parse.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    parse.instructions.add(new MethodInsnNode(
                            Opcodes.INVOKESPECIAL, RESOURCE_LOCATION, "<init>", "(Ljava/lang/String;)V", false));
                    parse.instructions.add(new InsnNode(Opcodes.ARETURN));
                    parse.maxStack = 3;
                    parse.maxLocals = 1;
                    classNode.methods.add(parse);
                }

                if (!hasNamespacedFactory && hasNamespacedConstructor) {
                    var fromNamespaceAndPath = new MethodNode(
                            Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                            "fromNamespaceAndPath", NAMESPACED_DESCRIPTOR, null, null);
                    fromNamespaceAndPath.instructions.add(new TypeInsnNode(Opcodes.NEW, RESOURCE_LOCATION));
                    fromNamespaceAndPath.instructions.add(new InsnNode(Opcodes.DUP));
                    fromNamespaceAndPath.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    fromNamespaceAndPath.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    fromNamespaceAndPath.instructions.add(new MethodInsnNode(
                            Opcodes.INVOKESPECIAL, RESOURCE_LOCATION, "<init>",
                            "(Ljava/lang/String;Ljava/lang/String;)V", false));
                    fromNamespaceAndPath.instructions.add(new InsnNode(Opcodes.ARETURN));
                    fromNamespaceAndPath.maxStack = 4;
                    fromNamespaceAndPath.maxLocals = 2;
                    classNode.methods.add(fromNamespaceAndPath);
                }
                return classNode;
            }
        }
    };
}
