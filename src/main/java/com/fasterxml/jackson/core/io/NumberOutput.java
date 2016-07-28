package com.fasterxml.jackson.core.io;

public final class NumberOutput
{
    private static int MILLION = 1000000;
    private static int BILLION = 1000000000;
    private static long TEN_BILLION_L = 10000000000L;
    private static long THOUSAND_L = 1000L;

    private static long MIN_INT_AS_LONG = (long) Integer.MIN_VALUE;
    private static long MAX_INT_AS_LONG = (long) Integer.MAX_VALUE;

    final static String SMALLEST_LONG = String.valueOf(Long.MIN_VALUE);

    /**
     * Encoded representations of 3-decimal-digit indexed values, where
     * 3 LSB are ascii characters
     *
     * @since 2.8.2
     */
    private final static int[] FULL_AND_LEAD_I3 = new int[1000];

    static {
        /* Let's fill it with NULLs for ignorable leading digits,
         * and digit chars for others
         */
        int fullIx = 0;
        for (int i1 = 0; i1 < 10; ++i1) {
            for (int i2 = 0; i2 < 10; ++i2) {
                for (int i3 = 0; i3 < 10; ++i3) {
                    int enc = ((i1 + '0') << 16)
                            | ((i2 + '0') << 8)
                            | (i3 + '0');
                    FULL_AND_LEAD_I3[fullIx++] = enc;
                }
            }
        }
    }

    private final static String[] sSmallIntStrs = new String[] {
        "0","1","2","3","4","5","6","7","8","9","10"
    };
    private final static String[] sSmallIntStrs2 = new String[] {
        "-1","-2","-3","-4","-5","-6","-7","-8","-9","-10"
    };

    /*
    /**********************************************************
    /* Efficient serialization methods using raw buffers
    /**********************************************************
     */

    /**
     * @return Offset within buffer after outputting int
     */
    public static int outputInt(int v, char[] b, int off)
    {
        if (v < 0) {
            if (v == Integer.MIN_VALUE) {
                /* Special case: no matching positive value within range;
                 * let's then "upgrade" to long and output as such.
                 */
                return outputLong((long) v, b, off);
            }
            b[off++] = '-';
            v = -v;
        }

        if (v < MILLION) { // at most 2 triplets...
            if (v < 1000) {
                if (v < 10) {
                    b[off] = (char) ('0' + v);
                    return off+1;
                }
                return leading3(v, b, off);
            }
            int thousands = v / 1000;
            v -= (thousands * 1000); // == value % 1000
            off = leading3(thousands, b, off);
            off = full3(v, b, off);
            return off;
        }

        // ok, all 3 triplets included
        /* Let's first hand possible billions separately before
         * handling 3 triplets. This is possible since we know we
         * can have at most '2' as billion count.
         */
        boolean hasBillions = (v >= BILLION);
        if (hasBillions) {
            v -= BILLION;
            if (v >= BILLION) {
                v -= BILLION;
                b[off++] = '2';
            } else {
                b[off++] = '1';
            }
        }
        int newValue = v / 1000;
        int ones = (v - (newValue * 1000)); // == value % 1000
        v = newValue;
        newValue /= 1000;
        int thousands = (v - (newValue * 1000));
        
        // value now has millions, which have 1, 2 or 3 digits
        if (hasBillions) {
            off = full3(newValue, b, off);
        } else {
            off = leading3(newValue, b, off);
        }
        off = full3(thousands, b, off);
        off = full3(ones, b, off);
        return off;
    }

    public static int outputInt(int v, byte[] b, int off)
    {
        if (v < 0) {
            if (v == Integer.MIN_VALUE) {
                return outputLong((long) v, b, off);
            }
            b[off++] = '-';
            v = -v;
        }

        if (v < MILLION) { // at most 2 triplets...
            if (v < 1000) {
                if (v < 10) {
                    b[off++] = (byte) ('0' + v);
                } else {
                    off = leading3(v, b, off);
                }
            } else {
                int thousands = v / 1000;
                v -= (thousands * 1000); // == value % 1000
                off = leading3(thousands, b, off);
                off = full3(v, b, off);
            }
            return off;
        }
        boolean hasB = (v >= BILLION);
        if (hasB) {
            v -= BILLION;
            if (v >= BILLION) {
                v -= BILLION;
                b[off++] = '2';
            } else {
                b[off++] = '1';
            }
        }
        int newValue = v / 1000;
        int ones = (v - (newValue * 1000)); // == value % 1000
        v = newValue;
        newValue /= 1000;
        int thousands = (v - (newValue * 1000));
        
        if (hasB) {
            off = full3(newValue, b, off);
        } else {
            off = leading3(newValue, b, off);
        }
        off = full3(thousands, b, off);
        off = full3(ones, b, off);
        return off;
    }
    
    /**
     * @return Offset within buffer after outputting int
     */
    public static int outputLong(long v, char[] b, int off)
    {
        // First: does it actually fit in an int?
        if (v < 0L) {
            /* MIN_INT is actually printed as long, just because its
             * negation is not an int but long
             */
            if (v > MIN_INT_AS_LONG) {
                return outputInt((int) v, b, off);
            }
            if (v == Long.MIN_VALUE) {
                // Special case: no matching positive value within range
                int len = SMALLEST_LONG.length();
                SMALLEST_LONG.getChars(0, len, b, off);
                return (off + len);
            }
            b[off++] = '-';
            v = -v;
        } else {
            if (v <= MAX_INT_AS_LONG) {
                return outputInt((int) v, b, off);
            }
        }

        /* Ok: real long print. Need to first figure out length
         * in characters, and then print in from end to beginning
         */
        int origOffset = off;
        off += calcLongStrLength(v);
        int ptr = off;

        // First, with long arithmetics:
        while (v > MAX_INT_AS_LONG) { // full triplet
            ptr -= 3;
            long newValue = v / THOUSAND_L;
            int triplet = (int) (v - newValue * THOUSAND_L);
            full3(triplet, b, ptr);
            v = newValue;
        }
        // Then with int arithmetics:
        int ivalue = (int) v;
        while (ivalue >= 1000) { // still full triplet
            ptr -= 3;
            int newValue = ivalue / 1000;
            int triplet = ivalue - (newValue * 1000);
            full3(triplet, b, ptr);
            ivalue = newValue;
        }
        // And finally, if anything remains, partial triplet
        leading3(ivalue, b, origOffset);

        return off;
    }

    public static int outputLong(long v, byte[] b, int off)
    {
        if (v < 0L) {
            if (v > MIN_INT_AS_LONG) {
                return outputInt((int) v, b, off);
            }
            if (v == Long.MIN_VALUE) {
                // Special case: no matching positive value within range
                int len = SMALLEST_LONG.length();
                for (int i = 0; i < len; ++i) {
                    b[off++] = (byte) SMALLEST_LONG.charAt(i);
                }
                return off;
            }
            b[off++] = '-';
            v = -v;
        } else {
            if (v <= MAX_INT_AS_LONG) {
                return outputInt((int) v, b, off);
            }
        }
        int origOff = off;
        off += calcLongStrLength(v);
        int ptr = off;

        // First, with long arithmetics:
        while (v > MAX_INT_AS_LONG) { // full triplet
            ptr -= 3;
            long newV = v / THOUSAND_L;
            int t = (int) (v - newV * THOUSAND_L);
            full3(t, b, ptr);
            v = newV;
        }
        // Then with int arithmetics:
        int ivalue = (int) v;
        while (ivalue >= 1000) { // still full triplet
            ptr -= 3;
            int newV = ivalue / 1000;
            int t = ivalue - (newV * 1000);
            full3(t, b, ptr);
            ivalue = newV;
        }
        leading3(ivalue, b, origOff);
        return off;
    }
    
    /*
    /**********************************************************
    /* Secondary convenience serialization methods
    /**********************************************************
     */

    /* !!! 05-Aug-2008, tatus: Any ways to further optimize
     *   these? (or need: only called by diagnostics methods?)
     */

    public static String toString(int v)
    {
        // Lookup table for small values
        if (v < sSmallIntStrs.length) {
            if (v >= 0) {
                return sSmallIntStrs[v];
            }
            int v2 = -v - 1;
            if (v2 < sSmallIntStrs2.length) {
                return sSmallIntStrs2[v2];
            }
        }
        return Integer.toString(v);
    }

    public static String toString(long v) {
        if (v <= Integer.MAX_VALUE && v >= Integer.MIN_VALUE) {
            return toString((int) v);
        }
        return Long.toString(v);
    }

    public static String toString(double v) {
        return Double.toString(v);
    }

    /**
     * @since 2.6.0
     */
    public static String toString(float v) {
        return Float.toString(v);
    }

    /*
    /**********************************************************
    /* Internal methods
    /**********************************************************
     */

    private static int leading3(int t, char[] b, int off)
    {
        int enc = FULL_AND_LEAD_I3[t];
        if (t > 9) {
            if (t > 99) {
                b[off++] = (char) (enc >> 16);
            }
            b[off++] = (char) ((enc >> 8) & 0x7F);
        }
        b[off++] = (char) (enc & 0x7F);
        return off;
    }

    private static int leading3(int t, byte[] b, int off)
    {
        int enc = FULL_AND_LEAD_I3[t];
        if (t > 9) {
            if (t > 99) {
                b[off++] = (byte) (enc >> 16);
            }
            b[off++] = (byte) (enc >> 8);
        }
        b[off++] = (byte) enc;
        return off;
    }

    private static int full3(int t, char[] b, int off)
    {
        int enc = FULL_AND_LEAD_I3[t];
        b[off++] = (char) (enc >> 16);
        b[off++] = (char) ((enc >> 8) & 0x7F);
        b[off++] = (char) (enc & 0x7F);
        return off;
    }

    private static int full3(int t, byte[] b, int off)
    {
        int enc = FULL_AND_LEAD_I3[t];
        b[off++] = (byte) (enc >> 16);
        b[off++] = (byte) (enc >> 8);
        b[off++] = (byte) enc;
        return off;
    }

    /**
     *<p>
     * Pre-conditions: <code>c</code> is positive, and larger than
     * Integer.MAX_VALUE (about 2 billions).
     */
    private static int calcLongStrLength(long v)
    {
        int len = 10;
        long cmp = TEN_BILLION_L;

        // 19 is longest, need to worry about overflow
        while (v >= cmp) {
            if (len == 19) {
                break;
            }
            ++len;
            cmp = (cmp << 3) + (cmp << 1); // 10x
        }
        return len;
    }
}
