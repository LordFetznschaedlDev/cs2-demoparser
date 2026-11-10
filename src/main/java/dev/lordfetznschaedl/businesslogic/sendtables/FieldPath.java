package dev.lordfetznschaedl.businesslogic.sendtables;

import java.util.Arrays;

/**
 * Address of a (possibly nested) entity property: a list of indices into serializer fields and arrays.
 */
public final class FieldPath {

    private static final int MAX_DEPTH = 7;

    private final int[] path;
    private int last;

    FieldPath() {
        path = new int[MAX_DEPTH];
        path[0] = -1;
        last = 0;
    }

    private FieldPath(int[] path, int last) {
        this.path = path;
        this.last = last;
    }

    public int get(int index) {
        return path[index];
    }

    /** Index of the last valid component. */
    public int last() {
        return last;
    }

    void add(int index, int delta) {
        path[index] += delta;
    }

    void addLast(int delta) {
        path[last] += delta;
    }

    void push(int value) {
        path[++last] = value;
    }

    void pushAdd(int delta) {
        path[++last] += delta;
    }

    void pop(int count) {
        for (int i = 0; i < count; i++) {
            path[last--] = 0;
        }
    }

    /** Copy of this path truncated so that {@code last} is the final component. */
    FieldPath prefix(int last) {
        int[] copy = new int[path.length];
        System.arraycopy(path, 0, copy, 0, last + 1);
        return new FieldPath(copy, last);
    }

    FieldPath copy() {
        return new FieldPath(Arrays.copyOf(path, path.length), last);
    }

    /**
     * Packs the path into a long (9 bits per component) for caching, or returns {@code -1}
     * if a component does not fit.
     */
    long packedKey() {
        if (last >= 6) {
            return -1;
        }
        long key = last;
        for (int i = 0; i <= last; i++) {
            int value = path[i];
            if (value < 0 || value >= 512) {
                return -1;
            }
            key |= (long) value << (3 + 9 * i);
        }
        return key;
    }

    @Override
    public String toString() {
        return Arrays.toString(Arrays.copyOf(path, last + 1));
    }
}
