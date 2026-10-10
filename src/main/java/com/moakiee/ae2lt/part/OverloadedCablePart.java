package com.moakiee.ae2lt.part;

import com.moakiee.thunderbolt.api.channel.HighCapacityChannelOwner;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.world.entity.player.Player;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.util.AEColor;
import appeng.api.util.AECableType;
import appeng.items.parts.ColoredPartItem;
import appeng.parts.networking.CoveredDenseCablePart;
import appeng.parts.networking.IUsedChannelProvider;

/** Dense cable owner for high-capacity channels and native connection usage reporting. */
public class OverloadedCablePart extends CoveredDenseCablePart
        implements HighCapacityChannelOwner, IUsedChannelProvider {

    public OverloadedCablePart(ColoredPartItem<?> partItem) {
        super(partItem);
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode().setTagName("overloaded_cable");
    }

    @Override
    public AECableType getCableConnectionType() {
        return AECableType.DENSE_COVERED;
    }

    @Override
    public int getUsedChannelsInfo() {
        int used = 0;
        IGridNode node = this.getGridNode();
        if (node != null && node.isActive()) {
            for (var connection : node.getConnections()) {
                used = Math.max(used, connection.getUsedChannels());
            }
        }
        return used;
    }

    @Override
    public int getMaxChannelsInfo() {
        if (this.getGridNode() == null) {
            return 0;
        }
        return -1;
    }

    @Override
    public boolean changeColor(AEColor newColor, Player who) {
        if (this.getCableColor() == newColor) {
            return false;
        }

        var newPart = ModItems.getOverloadedCable(newColor);

        if (isClientSide()) {
            return true;
        }

        setPartItem(newPart);
        getMainNode().setGridColor(getCableColor());
        getHost().partChanged();
        getHost().markForUpdate();
        getHost().markForSave();
        return true;
    }
}
