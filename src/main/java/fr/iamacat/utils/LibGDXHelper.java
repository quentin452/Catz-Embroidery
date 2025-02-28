package fr.iamacat.utils;

import java.lang.reflect.Array;

public class LibGDXHelper {

    // Expands an integer array by doubling its size or to a given new size.
    public static int[] expand(int[] array) {
        return expand(array, array.length > 0 ? array.length << 1 : 1); // Double size by default
    }

    // Expands an integer array by a specified new size.
    public static int[] expand(int[] array, int newSize) {
        int[] temp = new int[newSize];
        System.arraycopy(array, 0, temp, 0, Math.min(newSize, array.length));
        return temp;
    }

    // You can implement the expand method similarly for other data types, for example:
    public static float[] expand(float[] array) {
        return expand(array, array.length > 0 ? array.length << 1 : 1); // Double size by default
    }

    public static float[] expand(float[] array, int newSize) {
        float[] temp = new float[newSize];
        System.arraycopy(array, 0, temp, 0, Math.min(newSize, array.length));
        return temp;
    }

    // Example for resizing a generic Object array
    public static Object expand(Object array) {
        int len = Array.getLength(array);
        return expand(array, len > 0 ? len << 1 : 1); // Double size by default
    }

    public static Object expand(Object array, int newSize) {
        Class<?> type = array.getClass().getComponentType();
        Object temp = Array.newInstance(type, newSize);
        System.arraycopy(array, 0, temp, 0, Math.min(Array.getLength(array), newSize));
        return temp;
    }
}
