package com.moakiee.ae2lt.logic.tianshu.terminal;
import appeng.api.stacks.GenericStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
public record ForgeProcessingDraftData(List<GenericStack> sparseInputs,List<GenericStack> sparseOutputs) {
    public static ForgeProcessingDraftData read(ItemStack stack) {
        var tag=stack.getTag();
        if(tag==null)return null;
        return new ForgeProcessingDraftData(read(tag.getList("in",Tag.TAG_COMPOUND)),read(tag.getList("out",Tag.TAG_COMPOUND)));
    }
    private static List<GenericStack> read(net.minecraft.nbt.ListTag tags) {
        var result=new ArrayList<GenericStack>();
        for(var tag:tags)result.add(GenericStack.readTag((net.minecraft.nbt.CompoundTag)tag));
        return result;
    }
}
