package com.moakiee.ae2lt.crafting.timewheel.allocation;

import java.util.HashMap;
import java.util.Map;

import appeng.api.stacks.AEKey;

/** A job-local intrusive list: quantity updates unlink depleted keys without a prefix scan.
 * Transactions only read its links; base inventory reconciliation happens between transactions. */
final class PositiveKeyIndex {
    static final class Link {
        final AEKey key;
        Link previous;
        Link next;
        Link(AEKey key) { this.key = key; }
    }

    private final Map<AEKey, Link> links = new HashMap<>();
    private Link first;
    private Link last;

    Link first() { return first; }
    int size() { return links.size(); }

    void set(AEKey key, boolean positive) {
        var link = links.get(key);
        if (positive) {
            if (link != null) return;
            link = new Link(key);
            links.put(key, link);
            link.previous = last;
            if (last == null) first = link;
            else last.next = link;
            last = link;
        } else if (link != null) {
            links.remove(key);
            if (link.previous == null) first = link.next;
            else link.previous.next = link.next;
            if (link.next == null) last = link.previous;
            else link.next.previous = link.previous;
            link.previous = null;
            link.next = null;
        }
    }
}
