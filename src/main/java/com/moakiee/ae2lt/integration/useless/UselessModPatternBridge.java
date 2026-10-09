package com.moakiee.ae2lt.integration.useless;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.patternprovider.PatternContainer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import static com.moakiee.ae2lt.integration.useless.UselessModReflection.*;
final class UselessModPatternBridge {
    private static final String CATALOG="com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog";
    private static final String ENCODING="com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternEncoding";
    private static final String DETAILS="com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternDetails";
    static long recipeGeneration() {return ((Number)call(CATALOG,"generation")).longValue();}
    static boolean isViewerRecipe(Object entry) {return is(entry,CATALOG+"$Entry");}
    static ItemStack encodeViewerRecipe(Object entry,Level level) {
        if(!isViewerRecipe(entry))return ItemStack.EMPTY;
        var source=(ItemStack)call(ENCODING,"createProcessingPattern",call(entry,"recipe"));
        return source.isEmpty()?ItemStack.EMPTY:(ItemStack)call(ENCODING,"encode",source,entry,level);
    }
    static ItemStack encodeDraft(ItemStack pattern,Level level) {
        return encodeMatchingDraft(pattern,null,level).pattern();
    }
    static UselessModCompat.Preview preview(ItemStack pattern,Level level) {
        var data=PatternDetailsHelper.decodePattern(pattern,level);
        if(data==null)return UselessModCompat.Preview.EMPTY;
        var inputs=new ArrayList<GenericStack>();
        for(var input:data.getInputs()) {
            var options=input.getPossibleInputs();
            if(options.length>0)inputs.add(new GenericStack(options[0].what(),Math.multiplyExact(options[0].amount(),input.getMultiplier())));
        }
        var recipe=is(data,DETAILS)?call(data,"recipe"):null;
        return new UselessModCompat.Preview(inputs,List.of(data.getOutputs()),recipe==null?List.of():molds(recipe),recipe==null?"":String.valueOf(call(recipe,"getId")));
    }
    static UselessModCompat.EncodingResult encodeMatchingDraft(ItemStack pattern,@Nullable List<ItemStack> editedMolds,Level level) {
        if(pattern.isEmpty())return new UselessModCompat.EncodingResult(ItemStack.EMPTY,"ae2lt.tianshu.omniversal.no_match");
        var preview=preview(pattern,level);var supplied=editedMolds==null?preview.molds():editedMolds.stream().filter(s->!s.isEmpty()).toList();
        var source=PatternDetailsHelper.encodeProcessingPattern(preview.inputs().toArray(GenericStack[]::new),preview.outputs().toArray(GenericStack[]::new));
        var details=new AEProcessingPattern(Objects.requireNonNull(AEItemKey.of(source)));
        // A native selected binding is accepted only after the mod validates its recipe again.
        var selected=PatternDetailsHelper.decodePattern(pattern,level);
        if(UselessModCompat.isOmniversalPattern(pattern) && is(selected,DETAILS)) {
            var recipe=call(selected,"recipe");
            if(matchesMolds(recipe,supplied)) {
                var candidates=(List<?>)call(CATALOG,"findPatternCandidates",level,details);
                for(var entry:candidates) if(Objects.equals(call(entry,"recipe"),recipe)
                        && Boolean.TRUE.equals(call(CATALOG,"matchesRecipe",level,call(entry,"sourceId"),recipe,details)))
                    return new UselessModCompat.EncodingResult((ItemStack)call(ENCODING,"encode",source,details,entry,level),"");
            }
        }
        for(var entry:(List<?>)call(CATALOG,"findPatternCandidates",level,details)) {
            var recipe=call(entry,"recipe");
            if(matchesMolds(recipe,supplied) && Boolean.TRUE.equals(call(CATALOG,"matchesRecipe",level,call(entry,"sourceId"),recipe,details)))
                return new UselessModCompat.EncodingResult((ItemStack)call(ENCODING,"encode",source,details,entry,level),"");
        }
        return new UselessModCompat.EncodingResult(ItemStack.EMPTY,"ae2lt.tianshu.omniversal.no_match");
    }
    private static boolean matchesMolds(Object recipe,List<ItemStack> supplied) {
        var required=(List<?>)call(recipe,"molds");
        return required.size()==supplied.size() && Boolean.TRUE.equals(call("com.sorrowmist.useless.content.blockentities.multiblock.OmniversalMoldHubBlockEntity","matchesMolds",required,supplied));
    }
    private static List<ItemStack> molds(Object recipe) {
        var result=new ArrayList<ItemStack>();
        for(var mold:(List<?>)call(recipe,"molds")) {
            var stack=(ItemStack)call("com.sorrowmist.useless.content.recipe.AdapterUtils","itemRepresentative",mold);
            if(stack!=null&&!stack.isEmpty())result.add(stack.copy());
        }
        return result;
    }
    static void clearPendingRecipe(Object logic) {
        if(is(logic,"com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.PendingOmniversalPatternHolder")) {
            call(logic,"uselessMod$setPendingOmniversalRecipe",(Object)null);
            call(logic,"uselessMod$setPendingOmniversalSourceId",(Object)null);
        }
    }
    @Nullable static UselessModCompat.TargetState targetState(PatternContainer target,ItemStack pattern,Level level) {
        Object machine=target;boolean single=is(target,"com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity");
        if(!single) {
            if(!is(target,"com.sorrowmist.useless.content.blockentities.multiblock.MePatternAssemblyBlockEntity"))return null;
            machine=call(target,"getController");
            if(machine==null || !Boolean.TRUE.equals(call(machine,"isFormed"))) return new UselessModCompat.TargetState(false,false,true,"ae2lt.tianshu.omniversal.upload.unformed");
        }
        var details=PatternDetailsHelper.decodePattern(pattern,level);
        if(!is(details,DETAILS))return new UselessModCompat.TargetState(false,false,!single,"ae2lt.tianshu.omniversal.invalid");
        var recipe=call(details,"recipe");
        if(single&&((List<?>)call(recipe,"molds")).size()>1)return new UselessModCompat.TargetState(false,false,false,"ae2lt.tianshu.omniversal.upload.requires_multiblock");
        var state=call(machine,"getTaskAvailability",recipe);
        if(Boolean.TRUE.equals(call(state,"available")))return new UselessModCompat.TargetState(true,true,!single,"");
        var reason=String.valueOf(call(state,"statusKey"));boolean missing=reason.endsWith("waiting_missing_mold")||reason.endsWith("waiting_mold_hub");
        return new UselessModCompat.TargetState(missing,false,!single,missing?"ae2lt.tianshu.omniversal.upload.missing_mold":reason);
    }
}
