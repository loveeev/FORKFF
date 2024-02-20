package org.mineacademy.fo.menu;

import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Represents slots in menus.
 */
public enum MenuSlots {
;
    public enum SizedShape {
        BOUNDS_3(true, 10, 11, 12, 13, 14, 15, 16),
        BOUNDS_4(true, 10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25),
        BOUNDS_5(true, 10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34),
        BOUNDS_6(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53),

        CIRCLE_5(true, 11, 12, 13, 14, 15, 19, 20, 21, 22, 23, 24, 25, 29, 30, 31, 32, 33),
        CIRCLE_6(true, 12, 13, 14, 20, 21, 22, 23, 24, 29, 30, 31, 32, 33, 39, 40, 41),

        ROWS_3(0, 1, 2, 3, 4, 5, 6, 7, 8, 18, 19, 20, 21, 22, 23, 24, 25, 26),
        ROWS_4(0, 1, 2, 3, 4, 5, 6, 7, 8, 27, 28, 29, 30, 31, 32, 33, 34, 35),
        ROWS_5(0, 1, 2, 3, 4, 5, 6, 7, 8, 36, 37, 38, 39, 40, 41, 42, 43, 44),
        ROWS_6(0, 1, 2, 3, 4, 5, 6, 7, 8, 45, 46, 47, 48, 49, 50, 51, 52, 53),

        COLUMNS_1(0, 8),
        COLUMNS_2(0, 8, 9, 17),
        COLUMNS_3(0, 8, 9, 17, 18, 26),
        COLUMNS_4(0, 8, 9, 17, 18, 26, 27, 35),
        COLUMNS_5(0, 8, 9, 17, 18, 26, 27, 35, 36, 44),
        COLUMNS_6(0, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 53),

        SIX_SLOTS_2(true, 3, 4, 5, 12, 13, 14),
        SIX_SLOTS_4(true, 12, 13, 14, 21, 22, 23),
        SIX_SLOTS_6(true, 21, 22, 23, 30, 31, 32),

        TWO_SLOTS_1(true, 3, 5),
        TWO_SLOTS_2(true, 4, 13),
        TWO_SLOTS_3(true, 12, 14),
        TWO_SLOTS_4(true, 13, 22),
        TWO_SLOTS_5(true, 21, 23),
        TWO_SLOTS_6(true, 22, 31),

        ONE_SLOT_1(true, 4),
        ONE_SLOT_2(true, 4),
        ONE_SLOT_3(true, 13),
        ONE_SLOT_4(true, 13),
        ONE_SLOT_5(true, 22),
        ONE_SLOT_6(true, 22),

        NONE(-1);

        @Getter
        private final Integer[] slots;

        SizedShape(Integer... slots) {
            this(false, slots);
        }

        SizedShape(boolean reversed, Integer... slots) {
            if (!reversed) {
                this.slots = slots;
                return;
            }
            List<Integer> lockedSlots = IntStream.rangeClosed(0, 53).boxed().collect(Collectors.toList());
            for (Integer slot : slots) {
                lockedSlots.remove(Integer.valueOf(slot));
            }
            this.slots = lockedSlots.toArray(new Integer[0]);
        }
    }

    public enum Shape {
        BOUNDS(SizedShape.NONE, SizedShape.NONE, SizedShape.BOUNDS_3, SizedShape.BOUNDS_4, SizedShape.BOUNDS_5, SizedShape.BOUNDS_6),
        CIRCLE(SizedShape.NONE, SizedShape.NONE, SizedShape.NONE, SizedShape.NONE, SizedShape.CIRCLE_5, SizedShape.CIRCLE_6),
        COLUMNS(SizedShape.COLUMNS_1, SizedShape.COLUMNS_2, SizedShape.COLUMNS_3, SizedShape.COLUMNS_4, SizedShape.COLUMNS_5, SizedShape.COLUMNS_6),
        ROWS(SizedShape.NONE, SizedShape.NONE, SizedShape.ROWS_3, SizedShape.ROWS_4, SizedShape.ROWS_5, SizedShape.ROWS_6),
        SIX_SLOTS(SizedShape.NONE, SizedShape.SIX_SLOTS_2, SizedShape.NONE, SizedShape.SIX_SLOTS_4, SizedShape.NONE, SizedShape.SIX_SLOTS_6),
        TWO_SLOTS(SizedShape.TWO_SLOTS_1, SizedShape.TWO_SLOTS_2, SizedShape.TWO_SLOTS_3, SizedShape.TWO_SLOTS_4, SizedShape.TWO_SLOTS_5, SizedShape.TWO_SLOTS_6),
        ONE_SLOT(SizedShape.ONE_SLOT_1, SizedShape.ONE_SLOT_2, SizedShape.ONE_SLOT_3, SizedShape.ONE_SLOT_4, SizedShape.ONE_SLOT_5, SizedShape.ONE_SLOT_6);

        private final SizedShape[] mod;

        Shape(SizedShape... modification){
            this.mod = modification;
        }
    }

    public static SizedShape AUTO(Shape shape, int size){
        if (size == 0 || size % 9 != 0) throw new IllegalArgumentException("Menu size must be a multiple of 9.");
        return shape.mod[size / 9 - 1];
    }

}
