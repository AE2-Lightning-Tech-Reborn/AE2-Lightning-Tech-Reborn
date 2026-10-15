package com.moakiee.ae2lt.logic.management;

import java.util.List;
import java.util.Locale;
import net.minecraft.network.FriendlyByteBuf;

public record ManagementQuery(int serial, boolean energy, boolean grouped, String type, String search,
        List<Token> tokens, int sort, boolean descending, int window, int offset) {
    public static final int PAGE_SIZE = 5;
    /** Localized item names are resolved to registry IDs on the client. The server still chooses all rows. */
    public record Token(String text, List<String> types) {}
    public boolean valid() {
        return serial >= 0 && type != null && type.length() <= 256 && search != null && search.length() <= 128
                && tokens != null && tokens.size() <= 8 && tokens.stream().allMatch(t -> t != null
                && t.text != null && t.text.length() <= 128 && t.types != null && t.types.size() <= 256
                && t.types.stream().allMatch(s -> s != null && s.length() <= 256))
                && sort >= 0 && sort <= 4 && window >= 0 && window < 5 && offset >= 0 && offset <= 1_000_000;
    }
    public boolean matches(DeviceLocation target, DeviceLocation source, String kind) {
        String haystack = searchable(target) + " " + searchable(source) + " " + kind;
        for (var token : tokens) {
            if (!haystack.contains(token.text.toLowerCase(Locale.ROOT))
                    && !token.types.contains(target.type()) && (source == null || !token.types.contains(source.type()))
                    && !token.types.contains("kind:" + kind) && !token.types.contains("dimension:" + target.dimension())
                    && (source == null || !token.types.contains("dimension:" + source.dimension()))) return false;
        }
        return true;
    }
    private static String searchable(DeviceLocation d) {
        return d == null ? "" : (d.type() + " " + d.name() + " " + d.dimension() + " "
                + d.pos().getX() + " " + d.pos().getY() + " " + d.pos().getZ() + " "
                + d.pos().getX() + "," + d.pos().getY() + "," + d.pos().getZ()).toLowerCase(Locale.ROOT);
    }
    public void write(FriendlyByteBuf b) {
        b.writeVarInt(serial); b.writeBoolean(energy); b.writeBoolean(grouped); b.writeUtf(type, 256); b.writeUtf(search, 128);
        b.writeVarInt(tokens.size());
        for (var t : tokens) {
            b.writeUtf(t.text, 128); b.writeVarInt(t.types.size());
            for (var id : t.types) b.writeUtf(id, 256);
        }
        b.writeVarInt(sort); b.writeBoolean(descending); b.writeVarInt(window); b.writeVarInt(offset);
    }
    public static ManagementQuery read(FriendlyByteBuf b) {
        int serial = b.readVarInt(); boolean energy = b.readBoolean(), grouped = b.readBoolean();
        String type = b.readUtf(256), search = b.readUtf(128);
        int size = bounded(b.readVarInt(), 8);
        var tokens = new java.util.ArrayList<Token>(size);
        for (int i = 0; i < size; i++) {
            String text = b.readUtf(128); int count = bounded(b.readVarInt(), 256);
            var types = new java.util.ArrayList<String>(count);
            for (int j = 0; j < count; j++) types.add(b.readUtf(256));
            tokens.add(new Token(text, List.copyOf(types)));
        }
        return new ManagementQuery(serial, energy, grouped, type, search, List.copyOf(tokens),
                b.readVarInt(), b.readBoolean(), b.readVarInt(), b.readVarInt());
    }
    public static int bounded(int n, int max) {
        if (n < 0 || n > max) throw new IllegalArgumentException("Management collection exceeds bound");
        return n;
    }
}
