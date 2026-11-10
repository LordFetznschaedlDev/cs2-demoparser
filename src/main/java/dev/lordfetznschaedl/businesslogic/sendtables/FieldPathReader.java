package dev.lordfetznschaedl.businesslogic.sendtables;

import dev.lordfetznschaedl.businesslogic.io.BitReader;
import dev.lordfetznschaedl.businesslogic.io.DemoFormatException;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Reads the Huffman encoded list of changed field paths that precedes the values of an entity update.
 */
public final class FieldPathReader {

    /** Operation weights as used by the engine to build its Huffman tree (order matters). */
    private static final int[] WEIGHTS = {
            36271, 10334, 1375, 646, 4128, 35, 3, 521, 2942, 560, 471, 10530, 251,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            310, 2, 0, 1837, 149, 300, 634, 0, 0, 1, 76, 271, 99, 25474
    };
    private static final int OP_FINISH = 39;

    /** Flattened Huffman tree: for internal nodes left/right children, for leaves the op (as -op - 1). */
    private static final int[] LEFT;
    private static final int[] RIGHT;
    private static final int ROOT;

    private sealed interface Node permits Leaf, Inner {
        int weight();

        int value();
    }

    private record Leaf(int weight, int value) implements Node {
    }

    private record Inner(int weight, int value, Node left, Node right) implements Node {
    }

    static {
        PriorityQueue<Node> queue = new PriorityQueue<>((a, b) -> a.weight() != b.weight()
                ? Integer.compare(a.weight(), b.weight())
                : Integer.compare(b.value(), a.value()));
        for (int i = 0; i < WEIGHTS.length; i++) {
            queue.add(new Leaf(Math.max(WEIGHTS[i], 1), i));
        }
        int nextValue = WEIGHTS.length;
        while (queue.size() > 1) {
            Node a = queue.poll();
            Node b = queue.poll();
            queue.add(new Inner(a.weight() + b.weight(), nextValue++, a, b));
        }
        LEFT = new int[nextValue];
        RIGHT = new int[nextValue];
        ROOT = flatten(queue.poll());
    }

    private static int flatten(Node node) {
        if (node instanceof Leaf leaf) {
            return -leaf.value() - 1;
        }
        Inner inner = (Inner) node;
        LEFT[inner.value()] = flatten(inner.left());
        RIGHT[inner.value()] = flatten(inner.right());
        return inner.value();
    }

    private FieldPathReader() {
    }

    public static List<FieldPath> read(BitReader r) {
        List<FieldPath> paths = new ArrayList<>();
        FieldPath fp = new FieldPath();
        while (true) {
            int node = ROOT;
            while (node >= 0) {
                node = r.readBoolean() ? RIGHT[node] : LEFT[node];
            }
            int op = -node - 1;
            if (op == OP_FINISH) {
                return paths;
            }
            apply(op, fp, r);
            paths.add(fp.copy());
        }
    }

    private static void apply(int op, FieldPath fp, BitReader r) {
        switch (op) {
            case 0 -> fp.addLast(1);                                            // PlusOne
            case 1 -> fp.addLast(2);                                            // PlusTwo
            case 2 -> fp.addLast(3);                                            // PlusThree
            case 3 -> fp.addLast(4);                                            // PlusFour
            case 4 -> fp.addLast(r.readUBitVarFieldPath() + 5);                 // PlusN
            case 5 -> fp.push(0);                                               // PushOneLeftDeltaZeroRightZero
            case 6 -> fp.push(r.readUBitVarFieldPath());                        // PushOneLeftDeltaZeroRightNonZero
            case 7 -> {                                                         // PushOneLeftDeltaOneRightZero
                fp.addLast(1);
                fp.push(0);
            }
            case 8 -> {                                                         // PushOneLeftDeltaOneRightNonZero
                fp.addLast(1);
                fp.push(r.readUBitVarFieldPath());
            }
            case 9 -> {                                                         // PushOneLeftDeltaNRightZero
                fp.addLast(r.readUBitVarFieldPath());
                fp.push(0);
            }
            case 10 -> {                                                        // PushOneLeftDeltaNRightNonZero
                fp.addLast(r.readUBitVarFieldPath() + 2);
                fp.push(r.readUBitVarFieldPath() + 1);
            }
            case 11 -> {                                                        // ...Pack6Bits
                fp.addLast(r.readBits(3) + 2);
                fp.push(r.readBits(3) + 1);
            }
            case 12 -> {                                                        // ...Pack8Bits
                fp.addLast(r.readBits(4) + 2);
                fp.push(r.readBits(4) + 1);
            }
            case 13 -> pushVar(fp, r, 2);                                       // PushTwoLeftDeltaZero
            case 14 -> pushPack5(fp, r, 2);                                     // PushTwoPack5LeftDeltaZero
            case 15 -> pushVar(fp, r, 3);                                       // PushThreeLeftDeltaZero
            case 16 -> pushPack5(fp, r, 3);                                     // PushThreePack5LeftDeltaZero
            case 17 -> {                                                        // PushTwoLeftDeltaOne
                fp.addLast(1);
                pushVar(fp, r, 2);
            }
            case 18 -> {                                                        // PushTwoPack5LeftDeltaOne
                fp.addLast(1);
                pushPack5(fp, r, 2);
            }
            case 19 -> {                                                        // PushThreeLeftDeltaOne
                fp.addLast(1);
                pushVar(fp, r, 3);
            }
            case 20 -> {                                                        // PushThreePack5LeftDeltaOne
                fp.addLast(1);
                pushPack5(fp, r, 3);
            }
            case 21 -> {                                                        // PushTwoLeftDeltaN
                fp.addLast(r.readUBitVar() + 2);
                pushVar(fp, r, 2);
            }
            case 22 -> {                                                        // PushTwoPack5LeftDeltaN
                fp.addLast(r.readUBitVar() + 2);
                pushPack5(fp, r, 2);
            }
            case 23 -> {                                                        // PushThreeLeftDeltaN
                fp.addLast(r.readUBitVar() + 2);
                pushVar(fp, r, 3);
            }
            case 24 -> {                                                        // PushThreePack5LeftDeltaN
                fp.addLast(r.readUBitVar() + 2);
                pushPack5(fp, r, 3);
            }
            case 25 -> {                                                        // PushN
                int n = r.readUBitVar();
                fp.addLast(r.readUBitVar());
                pushVar(fp, r, n);
            }
            case 26 -> {                                                        // PushNAndNonTopological
                for (int i = 0; i <= fp.last(); i++) {
                    if (r.readBoolean()) {
                        fp.add(i, r.readSignedVarInt32() + 1);
                    }
                }
                int count = r.readUBitVar();
                for (int i = 0; i < count; i++) {
                    fp.push(r.readUBitVarFieldPath());
                }
            }
            case 27 -> {                                                        // PopOnePlusOne
                fp.pop(1);
                fp.addLast(1);
            }
            case 28 -> {                                                        // PopOnePlusN
                fp.pop(1);
                fp.addLast(r.readUBitVarFieldPath() + 1);
            }
            case 29 -> {                                                        // PopAllButOnePlusOne
                fp.pop(fp.last());
                fp.add(0, 1);
            }
            case 30 -> {                                                        // PopAllButOnePlusN
                fp.pop(fp.last());
                fp.add(0, r.readUBitVarFieldPath() + 1);
            }
            case 31 -> {                                                        // PopAllButOnePlusNPack3Bits
                fp.pop(fp.last());
                fp.add(0, r.readBits(3) + 1);
            }
            case 32 -> {                                                        // PopAllButOnePlusNPack6Bits
                fp.pop(fp.last());
                fp.add(0, r.readBits(6) + 1);
            }
            case 33 -> {                                                        // PopNPlusOne
                fp.pop(r.readUBitVarFieldPath());
                fp.addLast(1);
            }
            case 34 -> {                                                        // PopNPlusN
                fp.pop(r.readUBitVarFieldPath());
                fp.addLast(r.readSignedVarInt32());
            }
            case 35 -> {                                                        // PopNAndNonTopographical
                fp.pop(r.readUBitVarFieldPath());
                nonTopo(fp, r);
            }
            case 36 -> nonTopo(fp, r);                                          // NonTopoComplex
            case 37 -> fp.add(fp.last() - 1, 1);                                // NonTopoPenultimatePlusOne
            case 38 -> {                                                        // NonTopoComplexPack4Bits
                for (int i = 0; i <= fp.last(); i++) {
                    if (r.readBoolean()) {
                        fp.add(i, r.readBits(4) - 7);
                    }
                }
            }
            default -> throw new DemoFormatException("Unknown field path op " + op);
        }
    }

    private static void pushVar(FieldPath fp, BitReader r, int count) {
        for (int i = 0; i < count; i++) {
            fp.pushAdd(r.readUBitVarFieldPath());
        }
    }

    private static void pushPack5(FieldPath fp, BitReader r, int count) {
        for (int i = 0; i < count; i++) {
            fp.push(r.readBits(5));
        }
    }

    private static void nonTopo(FieldPath fp, BitReader r) {
        for (int i = 0; i <= fp.last(); i++) {
            if (r.readBoolean()) {
                fp.add(i, r.readSignedVarInt32());
            }
        }
    }
}
