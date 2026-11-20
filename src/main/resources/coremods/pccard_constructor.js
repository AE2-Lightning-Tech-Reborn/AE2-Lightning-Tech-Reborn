var Opcodes = Java.type("org.objectweb.asm.Opcodes");
var AbstractInsnNode = Java.type("org.objectweb.asm.tree.AbstractInsnNode");
var MethodNode = Java.type("org.objectweb.asm.tree.MethodNode");
var VarInsnNode = Java.type("org.objectweb.asm.tree.VarInsnNode");
var MethodInsnNode = Java.type("org.objectweb.asm.tree.MethodInsnNode");
var InsnNode = Java.type("org.objectweb.asm.tree.InsnNode");

var PCCARD_CLASS = "yuuki1293/pccard/PCCard";
var LOADING_CONTEXT = "net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext";
var LOADING_CONTEXT_DESCRIPTOR = "L" + LOADING_CONTEXT + ";";
var CONTEXT_CONSTRUCTOR = "(" + LOADING_CONTEXT_DESCRIPTOR + ")V";
var MOD_LOADING_CONTEXT = "net/minecraftforge/fml/ModLoadingContext";
var MOD_CONFIG_TYPE = "Lnet/minecraftforge/fml/config/ModConfig$Type;";
var CONFIG_SPEC = "Lnet/minecraftforge/fml/config/IConfigSpec;";
var REGISTER_CONFIG_DESCRIPTOR = "(" + MOD_CONFIG_TYPE + CONFIG_SPEC + ")V";
var PCCARD_REGISTER_CONFIG = "ae2lt$registerConfig";
var PCCARD_REGISTER_CONFIG_DESCRIPTOR = "(" + LOADING_CONTEXT_DESCRIPTOR
        + MOD_CONFIG_TYPE + CONFIG_SPEC + ")V";

function initializeCoreMod() {
    return {
        "pccard_constructor": {
            "target": {
                "type": "CLASS",
                "name": "yuuki1293.pccard.PCCard"
            },
            "transformer": function(classNode) {
                if (classNode.name !== PCCARD_CLASS) {
                    return classNode;
                }
                var hasNoArgsConstructor = false;
                var hasLoadingContextConstructor = false;
                var hasRegisterConfigBridge = false;
                var needsRegisterConfigBridge = false;

                for (var index = 0; index < classNode.methods.size(); index++) {
                    var method = classNode.methods.get(index);
                    if (method.name === "<init>" && method.desc === "()V") {
                        hasNoArgsConstructor = true;
                    }
                    if (method.name === "<init>" && method.desc === CONTEXT_CONSTRUCTOR) {
                        hasLoadingContextConstructor = true;
                    }
                    if (method.name === PCCARD_REGISTER_CONFIG
                            && method.desc === PCCARD_REGISTER_CONFIG_DESCRIPTOR) {
                        hasRegisterConfigBridge = true;
                    }

                    var instruction = method.instructions.getFirst();
                    while (instruction !== null) {
                        var next = instruction.getNext();
                        if (instruction.getType() === AbstractInsnNode.METHOD_INSN
                                && instruction.getOpcode() === Opcodes.INVOKEVIRTUAL
                                && instruction.owner === LOADING_CONTEXT
                                && instruction.name === "registerConfig"
                                && instruction.desc === REGISTER_CONFIG_DESCRIPTOR) {
                            instruction.setOpcode(Opcodes.INVOKESTATIC);
                            instruction.owner = PCCARD_CLASS;
                            instruction.name = PCCARD_REGISTER_CONFIG;
                            instruction.desc = PCCARD_REGISTER_CONFIG_DESCRIPTOR;
                            instruction.itf = false;
                            needsRegisterConfigBridge = true;
                        }
                        instruction = next;
                    }
                }

                if (!hasNoArgsConstructor && hasLoadingContextConstructor) {
                    var constructor = new MethodNode(
                            Opcodes.ASM9, Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
                    constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    constructor.instructions.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC, LOADING_CONTEXT, "get",
                            "()" + LOADING_CONTEXT_DESCRIPTOR, false));
                    constructor.instructions.add(new MethodInsnNode(
                            Opcodes.INVOKESPECIAL, PCCARD_CLASS, "<init>", CONTEXT_CONSTRUCTOR, false));
                    constructor.instructions.add(new InsnNode(Opcodes.RETURN));
                    constructor.maxStack = 2;
                    constructor.maxLocals = 1;
                    classNode.methods.add(constructor);
                }

                if (!hasRegisterConfigBridge && needsRegisterConfigBridge) {
                    var registerConfig = new MethodNode(
                            Opcodes.ASM9,
                            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                            PCCARD_REGISTER_CONFIG,
                            PCCARD_REGISTER_CONFIG_DESCRIPTOR,
                            null,
                            null);
                    registerConfig.instructions.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC, MOD_LOADING_CONTEXT, "get",
                            "()L" + MOD_LOADING_CONTEXT + ";", false));
                    registerConfig.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    registerConfig.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
                    registerConfig.instructions.add(new MethodInsnNode(
                            Opcodes.INVOKEVIRTUAL, MOD_LOADING_CONTEXT, "registerConfig",
                            REGISTER_CONFIG_DESCRIPTOR, false));
                    registerConfig.instructions.add(new InsnNode(Opcodes.RETURN));
                    registerConfig.maxStack = 3;
                    registerConfig.maxLocals = 3;
                    classNode.methods.add(registerConfig);
                }

                return classNode;
            }
        }
    };
}
