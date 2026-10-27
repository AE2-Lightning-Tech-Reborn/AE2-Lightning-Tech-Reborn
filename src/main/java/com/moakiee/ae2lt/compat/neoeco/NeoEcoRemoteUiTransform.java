package com.moakiee.ae2lt.compat.neoeco;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Defers the optional addon's client-world lookup until the client actually opens the UI. */
public final class NeoEcoRemoteUiTransform {
    private static final String LT_WORLD_HELPER = "com/moakiee/ae2lt/compat/neoeco/NeoEcoClientWorld";
    private static final String CORE_WORLD_HELPER = "com/moakiee/thunderbolt/compat/neoeco/NeoEcoClientLevel";
    private static final String WORLD_LOOKUP_DESCRIPTOR = "()Lnet/minecraft/world/level/Level;";
    private static final String READER_DESCRIPTOR = "(Lnet/minecraft/network/FriendlyByteBuf;)"
            + "Lcn/dancingsnow/neoecoae/compat/ae2/ECORemoteMachineUI$Holder;";

    private NeoEcoRemoteUiTransform() {}

    public static void isolateClientWorld(ClassNode target) {
        if (hasUpstreamClientIsolation(target)) return;
        var lookups = new ArrayList<MethodInsnNode>();
        var fields = new ArrayList<FieldInsnNode>();
        var methods = new ArrayList<MethodNode>();
        String helperOwner = null;
        int helperLookups = 0;
        boolean clientReferences = false;
        for (var method : target.methods) {
            if (!method.name.equals("readHolderFromSyncData")) continue;
            for (var instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode call) {
                    clientReferences |= call.owner.startsWith("net/minecraft/client/")
                            || call.desc.contains("Lnet/minecraft/client/");
                    if (call.owner.equals(CORE_WORLD_HELPER) || call.owner.equals(LT_WORLD_HELPER)) {
                        if (call.getOpcode() != Opcodes.INVOKESTATIC || !call.name.equals("current")
                                || !call.desc.equals(WORLD_LOOKUP_DESCRIPTOR) || call.itf
                                || !method.desc.equals(READER_DESCRIPTOR)
                                || helperOwner != null && !helperOwner.equals(call.owner))
                            throw new IllegalStateException("Unsupported NeoECO remote UI world helper");
                        helperOwner = call.owner;
                        helperLookups++;
                    }
                } else if (instruction instanceof FieldInsnNode field) {
                    clientReferences |= field.owner.startsWith("net/minecraft/client/")
                            || field.desc.contains("Lnet/minecraft/client/");
                } else if (instruction instanceof TypeInsnNode type) {
                    clientReferences |= type.desc.contains("net/minecraft/client/");
                }
                if (!(instruction instanceof MethodInsnNode call)
                        || call.getOpcode() != Opcodes.INVOKESTATIC
                        || !call.owner.equals("net/minecraft/client/Minecraft")
                        || !call.desc.equals("()Lnet/minecraft/client/Minecraft;")) continue;
                var next = instruction.getNext();
                while (next != null && next.getOpcode() < 0) next = next.getNext();
                if (!(next instanceof FieldInsnNode field)
                        || field.getOpcode() != Opcodes.GETFIELD
                        || !field.owner.equals(call.owner)
                        || !field.desc.equals("Lnet/minecraft/client/multiplayer/ClientLevel;"))
                    throw new IllegalStateException("Unsupported NeoECO remote UI world lookup");
                lookups.add(call);
                fields.add(field);
                methods.add(method);
            }
        }
        // Core's coremod runs before this Mixin; preserve its complete isolation (and our own on reentry).
        if (helperLookups > 0) {
            if (helperLookups != 2 || clientReferences)
                throw new IllegalStateException("Expected two isolated NeoECO remote UI world lookups without client references, found "
                        + helperLookups);
            return;
        }
        if (lookups.size() != 2)
            throw new IllegalStateException("Expected two NeoECO remote UI world lookups or the upstream client isolation, found "
                    + lookups.size());
        for (int i = 0; i < lookups.size(); i++) {
            methods.get(i).instructions.set(lookups.get(i), new MethodInsnNode(Opcodes.INVOKESTATIC,
                        LT_WORLD_HELPER, "current", WORLD_LOOKUP_DESCRIPTOR, false));
            methods.get(i).instructions.remove(fields.get(i));
        }
    }

    /** Recognize cca7fbb's complete guarded forwarder; zero old lookups alone is not sufficient. */
    private static boolean hasUpstreamClientIsolation(ClassNode target) {
        String owner = "cn/dancingsnow/neoecoae/compat/ae2/ECORemoteMachineUI";
        String descriptor = "(Lnet/minecraft/network/FriendlyByteBuf;)L" + owner + "$Holder;";
        var readers = target.methods.stream()
                .filter(method -> method.name.equals("readHolderFromSyncData") && method.desc.equals(descriptor))
                .toList();
        if (readers.size() != 1) return false;
        List<AbstractInsnNode> code = new ArrayList<>();
        for (var instruction : readers.getFirst().instructions) {
            if (instruction.getOpcode() >= 0) code.add(instruction);
        }
        return code.size() == 8
                && staticField(code.get(0), "net/minecraftforge/fml/loading/FMLEnvironment", "dist",
                        "Lnet/minecraftforge/api/distmarker/Dist;")
                && staticField(code.get(1), "net/minecraftforge/api/distmarker/Dist", "CLIENT",
                        "Lnet/minecraftforge/api/distmarker/Dist;")
                && code.get(2) instanceof JumpInsnNode branch && branch.getOpcode() == Opcodes.IF_ACMPNE
                && code.get(3) instanceof VarInsnNode buffer && buffer.getOpcode() == Opcodes.ALOAD && buffer.var == 1
                && code.get(4) instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC
                && call.owner.equals(owner + "$ClientHolderReader") && call.name.equals("read")
                && call.desc.equals(descriptor) && !call.itf
                && code.get(5) instanceof JumpInsnNode done && done.getOpcode() == Opcodes.GOTO
                && code.get(6).getOpcode() == Opcodes.ACONST_NULL
                && code.get(7).getOpcode() == Opcodes.ARETURN
                && nextCode(branch.label) == code.get(6) && nextCode(done.label) == code.get(7);
    }

    private static boolean staticField(AbstractInsnNode instruction, String owner, String name, String descriptor) {
        return instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
                && field.owner.equals(owner) && field.name.equals(name) && field.desc.equals(descriptor);
    }

    private static AbstractInsnNode nextCode(AbstractInsnNode instruction) {
        while (instruction != null && instruction.getOpcode() < 0) instruction = instruction.getNext();
        return instruction;
    }
}
