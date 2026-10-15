package com.moakiee.ae2lt.logic.management;

import java.util.List;
import java.util.Locale;
import net.minecraft.network.FriendlyByteBuf;

public record ManagementQuery(int serial, boolean energy, boolean grouped, List<String> expandedTypes, String search,
        List<Token> tokens, int sort, boolean descending, int window, int offset) {
    public static final int PAGE_SIZE = 5;
    public static final int MAX_EXPANDED_TYPES = 128;
    public static final int MAX_EXPANSION_BYTES = 8192;
    /** Localized item names are resolved to registry IDs on the client. The server still chooses all rows. */
    public record Token(String text, List<String> types) {}
    public boolean valid() {
        return serial >= 0 && validExpandedTypes(expandedTypes) && search != null && search.length() <= 128
                && tokens != null && tokens.size() <= 8 && tokens.stream().allMatch(t -> t != null
                && t.text != null && t.text.length() <= 128 && t.types != null && t.types.size() <= 256
                && t.types.stream().allMatch(s -> s != null && s.length() <= 256))
                && sort >= 0 && sort <= 4 && window >= 0 && window < 5 && offset >= 0 && offset <= 1_000_000;
    }
    public static boolean validExpandedTypes(List<String> types) {
        return types != null && types.size() <= MAX_EXPANDED_TYPES
                && types.stream().allMatch(s -> s != null && s.length() <= 256)
                && types.stream().distinct().count() == types.size()
                && types.stream().mapToInt(s -> s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 3).sum() <= MAX_EXPANSION_BYTES;
    }
    public static ManagementQuery initial(boolean energy) {
        return new ManagementQuery(0, energy, true, List.of(), "", List.of(), energy ? 4 : 2, energy, 1, 0);
    }
    public ManagementQuery atOffset(int offset) {
        return new ManagementQuery(serial, energy, grouped, expandedTypes, search, tokens, sort, descending, window, offset);
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
        b.writeVarInt(serial); b.writeBoolean(energy); b.writeBoolean(grouped);
        b.writeVarInt(expandedTypes.size());
        for (String type : expandedTypes) b.writeUtf(type, 256);
        b.writeUtf(search, 128);
        b.writeVarInt(tokens.size());
        for (var t : tokens) {
            b.writeUtf(t.text, 128); b.writeVarInt(t.types.size());
            for (var id : t.types) b.writeUtf(id, 256);
        }
        b.writeVarInt(sort); b.writeBoolean(descending); b.writeVarInt(window); b.writeVarInt(offset);
    }
    public static ManagementQuery read(FriendlyByteBuf b) {
        int serial = b.readVarInt(); boolean energy = b.readBoolean(), grouped = b.readBoolean();
        int expandedCount = bounded(b.readVarInt(), MAX_EXPANDED_TYPES);
        var expanded = new java.util.ArrayList<String>(expandedCount);
        for (int i = 0; i < expandedCount; i++) expanded.add(b.readUtf(256));
        if (!validExpandedTypes(expanded)) throw new IllegalArgumentException("Invalid expanded device types");
        String search = b.readUtf(128);
        int size = bounded(b.readVarInt(), 8);
        var tokens = new java.util.ArrayList<Token>(size);
        for (int i = 0; i < size; i++) {
            String text = b.readUtf(128); int count = bounded(b.readVarInt(), 256);
            var types = new java.util.ArrayList<String>(count);
            for (int j = 0; j < count; j++) types.add(b.readUtf(256));
            tokens.add(new Token(text, List.copyOf(types)));
        }
        return new ManagementQuery(serial, energy, grouped, List.copyOf(expanded), search, List.copyOf(tokens),
                b.readVarInt(), b.readBoolean(), b.readVarInt(), b.readVarInt());
    }
    public static int bounded(int n, int max) {
        if (n < 0 || n > max) throw new IllegalArgumentException("Management collection exceeds bound");
        return n;
    }
}
