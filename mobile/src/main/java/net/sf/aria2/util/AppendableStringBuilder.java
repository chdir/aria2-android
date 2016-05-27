package net.sf.aria2.util;

import android.support.annotation.NonNull;
import android.text.GetChars;

/**
 * A modifiable {@link CharSequence sequence of characters} for use in creating
 * and modifying Strings. Saner, faster alternative to {@link StringBuilder}
 * for constructing file names.
 */
public final class AppendableStringBuilder implements GetChars, CharSequence {
    private final char[] value;

    private int count;

    public AppendableStringBuilder(int capacity) {
        if (capacity < 0) {
            throw new NegativeArraySizeException(Integer.toString(capacity));
        }
        value = new char[capacity];
    }

    public final void append(char ch) {
        value[count++] = ch;
    }

    public final void append(@NonNull char[] chars) {
        int newCount = count + chars.length;
        System.arraycopy(chars, 0, value, count, chars.length);
        count = newCount;
    }

    public final void append(@NonNull char[] chars, int offset, int length) {
        System.arraycopy(chars, offset, value, count, length);
        count += length;
    }

    public final void append(@NonNull String string) {
        int length = string.length();
        string.getChars(0, length, value, count);
        count += length;
    }

    public final void append(@NonNull String s, int start, int end) {
        int length = end - start;
        s.getChars(start, end, value, count);
        this.count += length;
    }

    public final void append(@NonNull GetChars s) {
        int length = s.length();
        s.getChars(0, length, value, count);
        this.count += length;
    }

    public final void append(@NonNull GetChars s, int start, int end) {
        int length = end - start;
        s.getChars(start, end, value, count);
        this.count += length;
    }

    public final void append(@NonNull AppendableStringBuilder s) {
        int length = s.count;
        System.arraycopy(s.value, 0, value, count, length);
        this.count += length;
    }

    public final void append(@NonNull AppendableStringBuilder s, int start, int end) {
        int length = end - start;
        System.arraycopy(s.value, start, value, count, length);
        this.count += length;
    }

    /**
     * Returns the number of characters that can be held without growing.
     *
     * @return the capacity
     * @see #length
     */
    public int capacity() {
        return value.length;
    }

    /**
     * Returns the character at {@code index}.
     */
    @Override
    public char charAt(int index) {
        return value[index];
    }

    /**
     * Copies the requested sequence of characters into {@code dst} passed
     * starting at {@code dst}.
     *
     * @param start
     *            the inclusive start index of the characters to copy.
     * @param end
     *            the exclusive end index of the characters to copy.
     * @param dst
     *            the {@code char[]} to copy the characters to.
     * @param dstStart
     *            the inclusive start index of {@code dst} to begin copying to.
     */
    @Override
    public void getChars(int start, int end, char[] dst, int dstStart) {
        System.arraycopy(value, start, dst, dstStart, end - start);
    }

    /**
     * The current length.
     *
     * @return the number of characters contained in this instance.
     */
    @Override
    public int length() {
        return count;
    }

    /**
     * Sets the character at the {@code index}.
     *
     * @param index
     *            the zero-based index of the character to replace.
     * @param ch
     *            the character to set.
     * @throws IndexOutOfBoundsException
     *             if {@code index} is negative or greater than or equal to the
     *             current {@link #length()}.
     */
    public void setCharAt(int index, char ch) {
        value[index] = ch;
    }

    /**
     * Sets the current length to a new value.
     *
     * @param length
     *            the new length of this StringBuffer.
     * @see #length
     */
    public void setLength(int length) {
        count = length;
    }

    /**
     * Returns the String value of the subsequence from the {@code start} index
     * to the current end.
     *
     * @param start
     *            the inclusive start index to begin the subsequence.
     * @return a String containing the subsequence.
     * @throws StringIndexOutOfBoundsException
     *             if {@code start} is negative or greater than the current
     *             {@link #length()}.
     */
    public String substring(int start) {
        if (start == count) {
            return "";
        }

        // Remove String sharing for more performance
        return String.valueOf(value, start, count - start);
    }

    /**
     * Returns the String value of the subsequence from the {@code start} index
     * to the {@code end} index.
     *
     * @param start
     *            the inclusive start index to begin the subsequence.
     * @param end
     *            the exclusive end index to end the subsequence.
     * @return a String containing the subsequence.
     */
    public String substring(int start, int end) {
        if (start == end) {
            return "";
        }

        // Remove String sharing for more performance
        return String.valueOf(value, start, end - start);
    }

    /**
     * Returns the current String representation.
     *
     * @return a String containing the characters in this instance.
     */
    @Override
    public String toString() {
        if (count == 0) {
            return "";
        }
        return String.valueOf(value, 0, count);
    }

    /**
     * Returns a {@code CharSequence} of the subsequence from the {@code start}
     * index to the {@code end} index.
     *
     * @param start
     *            the inclusive start index to begin the subsequence.
     * @param end
     *            the exclusive end index to end the subsequence.
     * @return a CharSequence containing the subsequence.
     */
    @Override
    public CharSequence subSequence(int start, int end) {
        return substring(start, end);
    }
}
