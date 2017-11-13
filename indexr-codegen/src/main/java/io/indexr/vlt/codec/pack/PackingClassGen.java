package io.indexr.vlt.codec.pack;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * This class copied from org.apache.parquet.encoding.bitpacking.ByteBasedBitPackingGenerator with some modifications.
 */
public class PackingClassGen {

    private static final String CLASS_NAME_PREFIX_FOR_INT = "IntPacking";
    private static final String CLASS_NAME_PREFIX_FOR_LONG = "LongPacking";
    private static final String VARIABLE_TYPE_FOR_INT = "int";
    private static final String VARIABLE_TYPE_FOR_LONG = "long";
    private static final int MAX_BITS_FOR_INT = 32;
    private static final int MAX_BITS_FOR_LONG = 64;

    public static void main(String[] args) throws Exception {
        String basePath = args[0];
        // Int for Big Endian
        generateScheme(false, true, basePath);

        // Int for Little Endian
        generateScheme(false, false, basePath);

        // Long for Big Endian
        generateScheme(true, true, basePath);

        // Long for Little Endian
        generateScheme(true, false, basePath);
    }

    private static void generateScheme(boolean isLong, boolean msbFirst,
                                       String basePath) throws IOException {
        String baseClassName = isLong ? CLASS_NAME_PREFIX_FOR_LONG : CLASS_NAME_PREFIX_FOR_INT;
        String className = msbFirst ? (baseClassName + "BE") : (baseClassName + "LE");
        int maxBits = isLong ? MAX_BITS_FOR_LONG : MAX_BITS_FOR_INT;
        //String nameSuffix = isLong ? "ForLong" : "";
        String namePrefix = isLong ? "Long" : "Int";

        final File file = new File(basePath + "/io/indexr/vlt/codec/pack/" + className + ".java").getAbsoluteFile();
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        FileWriter fw = new FileWriter(file);
        fw.append("package io.indexr.vlt.codec.pack;\n");
        fw.append("import java.nio.ByteBuffer;\n");
        fw.append("import static io.indexr.vlt.codec.UnsafeUtil.*;\n");
        fw.append("\n");
        fw.append("/**\n");
        if (msbFirst) {
            fw.append(" * Packs from the Most Significant Bit first\n");
        } else {
            fw.append(" * Packs from the Least Significant Bit first\n");
        }
        fw.append(" * \n");
        fw.append(" * @author automatically generated\n");
        fw.append(" * @see ByteBasedBitPackingGenerator\n");
        fw.append(" *\n");
        fw.append(" */\n");
        fw.append("public abstract class " + className + " {\n");
        fw.append("\n");
        fw.append("  private static final " + namePrefix + "Packer[] packers = new " + namePrefix + "Packer[" + (maxBits + 1) + "];\n");
        fw.append("  static {\n");
        for (int i = 0; i <= maxBits; i++) {
            fw.append("    packers[" + i + "] = new Packer" + i + "();\n");
        }
        fw.append("  }\n");
        fw.append("\n");
        fw.append("  public static final " + namePrefix + "PackerFactory factory = new " + namePrefix + "PackerFactory() {\n");
        fw.append("    public " + namePrefix + "Packer newPacker(int bitWidth) {\n");
        fw.append("      return packers[bitWidth];\n");
        fw.append("    }\n");
        fw.append("  };\n");
        fw.append("\n");
        for (int i = 0; i <= maxBits; i++) {
            generateClass(fw, i, isLong, msbFirst);
            fw.append("\n");
        }
        fw.append("}\n");
        fw.close();
    }

    private static void generateClass(FileWriter fw, int bitWidth, boolean isLong, boolean msbFirst) throws IOException {
        String namePrefix = isLong ? "Long" : "Int";
        fw.append("  private static final class Packer" + bitWidth + " extends " + namePrefix + "Packer {\n");
        fw.append("\n");
        fw.append("    private Packer" + bitWidth + "() {\n");
        fw.append("      super("+bitWidth+");\n");
        fw.append("    }\n");
        //fw.append("    public int bitWidth(){return " + bitWidth + ";}\n");
        fw.append("\n");
        // Packing
        generatePack(fw, bitWidth, isLong);
        generatePack(fw, bitWidth, 1, isLong, msbFirst);

        // Unpacking
        generateUnpack(fw, bitWidth, isLong);
        generateUnpack(fw, bitWidth, 1, isLong, msbFirst, true);
        //generateUnpack(fw, bitWidth, 1, isLong, msbFirst, false);
        //generateUnpack(fw, bitWidth, 4, isLong, msbFirst, true);
        //generateUnpack(fw, bitWidth, 4, isLong, msbFirst, false);

        fw.append("  }\n");
    }

    private static class ShiftMask {
        ShiftMask(int shift, long mask) {
            this.shift = shift;
            this.mask = mask;
        }

        public int shift;
        public long mask;
    }

    private static ShiftMask getShift(FileWriter fw, int bitWidth, boolean isLong, boolean msbFirst,
                                      int byteIndex, int valueIndex) throws IOException {
        // relative positions of the start and end of the value to the start and end of the byte
        int valueStartBitIndex = (valueIndex * bitWidth) - (8 * (byteIndex));
        int valueEndBitIndex = ((valueIndex + 1) * bitWidth) - (8 * (byteIndex + 1));

        // boundaries of the current value that we want
        int valueStartBitWanted;
        int valueEndBitWanted;
        // boundaries of the current byte that will receive them
        int byteStartBitWanted;
        int byteEndBitWanted;

        int shift;
        int widthWanted;

        if (msbFirst) {
            valueStartBitWanted = valueStartBitIndex < 0 ? bitWidth - 1 + valueStartBitIndex : bitWidth - 1;
            valueEndBitWanted = valueEndBitIndex > 0 ? valueEndBitIndex : 0;
            byteStartBitWanted = valueStartBitIndex < 0 ? 8 : 7 - valueStartBitIndex;
            byteEndBitWanted = valueEndBitIndex > 0 ? 0 : -valueEndBitIndex;
            shift = valueEndBitWanted - byteEndBitWanted;
            widthWanted = Math.min(7, byteStartBitWanted) - Math.min(7, byteEndBitWanted) + 1;
        } else {
            valueStartBitWanted = bitWidth - 1 - (valueEndBitIndex > 0 ? valueEndBitIndex : 0);
            valueEndBitWanted = bitWidth - 1 - (valueStartBitIndex < 0 ? bitWidth - 1 + valueStartBitIndex : bitWidth - 1);
            byteStartBitWanted = 7 - (valueEndBitIndex > 0 ? 0 : -valueEndBitIndex);
            byteEndBitWanted = 7 - (valueStartBitIndex < 0 ? 8 : 7 - valueStartBitIndex);
            shift = valueStartBitWanted - byteStartBitWanted;
            widthWanted = Math.max(0, byteStartBitWanted) - Math.max(0, byteEndBitWanted) + 1;
        }

        int maskWidth = widthWanted + Math.max(0, shift);

        visualizeAlignment(
                fw, bitWidth, valueEndBitIndex,
                valueStartBitWanted, valueEndBitWanted,
                byteStartBitWanted, byteEndBitWanted,
                shift
        );
        return new ShiftMask(shift, genMask(maskWidth, isLong));
    }

    private static void visualizeAlignment(FileWriter fw, int bitWidth,
                                           int valueEndBitIndex, int valueStartBitWanted, int valueEndBitWanted,
                                           int byteStartBitWanted, int byteEndBitWanted, int shift) throws IOException {
        // ASCII art to visualize what is happening
        fw.append("//");
        int buf = 2 + Math.max(0, bitWidth + 8);
        for (int i = 0; i < buf; i++) {
            fw.append(" ");
        }
        fw.append("[");
        for (int i = 7; i >= 0; i--) {
            if (i <= byteStartBitWanted && i >= byteEndBitWanted) {
                fw.append(String.valueOf(i));
            } else {
                fw.append("_");
            }
        }
        fw.append("]\n          //");
        for (int i = 0; i < buf + (8 - bitWidth + shift); i++) {
            fw.append(" ");
        }
        fw.append("[");
        for (int i = bitWidth - 1; i >= 0; i--) {
            if (i <= valueStartBitWanted && i >= valueEndBitWanted) {
                fw.append(String.valueOf(i % 10));
            } else {
                fw.append("_");
            }
        }
        fw.append("]\n");
        fw.append("           ");
    }

    private static void generatePack(FileWriter fw, int bitWidth, boolean isLong) throws IOException {
        long mask = genMask(bitWidth, isLong);
        String maskSuffix = isLong ? "L" : "";
        String variableType = isLong ? VARIABLE_TYPE_FOR_LONG : VARIABLE_TYPE_FOR_INT;
        fw.append("    public final void pack8Values(Object inBase, long inAddr, Object outBase, long outAddr){\n");
        for (int byteIndex = 0; byteIndex < bitWidth; ++byteIndex) {
            //fw.append("      out[" + align(byteIndex, 2) + " + outPos] = (byte)((\n");
            //       putByte(outBase, 0 + outAddr, (byte)((
            fw.append("       setByte(outBase, " + align(byteIndex, 2) + " + outAddr, (byte)((\n");
            int startIndex = (byteIndex * 8) / bitWidth;
            int endIndex = ((byteIndex + 1) * 8 + bitWidth - 1) / bitWidth;
            for (int valueIndex = startIndex; valueIndex < endIndex; valueIndex++) {
                if (valueIndex == startIndex) {
                    fw.append("          ");
                } else {
                    fw.append("\n        | ");
                }
                ShiftMask shiftMask = getShift(fw, bitWidth, isLong, false, byteIndex, valueIndex);

                String shiftString = ""; // used when shift == 0
                if (shiftMask.shift > 0) {
                    shiftString = " >>> " + shiftMask.shift;
                } else if (shiftMask.shift < 0) {
                    shiftString = " <<  " + (-shiftMask.shift);
                }
                //fw.append("((in[" + align(valueIndex, 2) + " + inPos] & " + mask + maskSuffix + ")" + shiftString + ")");
                //           ((in[ 0 + inPos] & 1023))) & 255);
                //           ((getInt(inBase, (0 << 2) + inAddr) & 1023))) & 255));
                if (isLong) {
                    fw.append("((getLong(inBase, (" + align(valueIndex, 2) + " << 3) + inAddr) & " + mask + maskSuffix + ")" + shiftString + ")");
                } else {
                    fw.append("((getInt(inBase, (" + align(valueIndex, 2) + " << 2) + inAddr) & " + mask + maskSuffix + ")" + shiftString + ")");
                }
            }
            fw.append(") & 255));\n");
        }
        fw.append("    }\n");
    }

    private static void generatePack(FileWriter fw, int bitWidth, int batch, boolean isLong, boolean msbFirst) throws IOException {
        long mask = genMask(bitWidth, isLong);
        String maskSuffix = isLong ? "L" : "";
        String variableType = isLong ? VARIABLE_TYPE_FOR_LONG : VARIABLE_TYPE_FOR_INT;
        fw.append("    public final void pack" + (batch * 8) + "Values(final " + variableType + "[] in, final int inPos, final byte[] out, final int outPos) {\n");
        for (int byteIndex = 0; byteIndex < bitWidth * batch; ++byteIndex) {
            fw.append("      out[" + align(byteIndex, 2) + " + outPos] = (byte)((\n");
            int startIndex = (byteIndex * 8) / bitWidth;
            int endIndex = ((byteIndex + 1) * 8 + bitWidth - 1) / bitWidth;
            for (int valueIndex = startIndex; valueIndex < endIndex; valueIndex++) {

                if (valueIndex == startIndex) {
                    fw.append("          ");
                } else {
                    fw.append("\n        | ");
                }
                ShiftMask shiftMask = getShift(fw, bitWidth, isLong, msbFirst, byteIndex, valueIndex);

                String shiftString = ""; // used when shift == 0
                if (shiftMask.shift > 0) {
                    shiftString = " >>> " + shiftMask.shift;
                } else if (shiftMask.shift < 0) {
                    shiftString = " <<  " + (-shiftMask.shift);
                }
                fw.append("((in[" + align(valueIndex, 2) + " + inPos] & " + mask + maskSuffix + ")" + shiftString + ")");
            }
            fw.append(") & 255);\n");
        }
        fw.append("    }\n");
    }


    private static void generateUnpack(FileWriter fw, int bitWidth, boolean isLong)
            throws IOException {
        final String variableType = isLong ? VARIABLE_TYPE_FOR_LONG : VARIABLE_TYPE_FOR_INT;
        //final String bufferDataType = useByteArray ? "byte[]" : "ByteBuffer";

        //fw.append("    public final void unpack" + 8 + "Values(final " + bufferDataType + " in, "
        //        + "final int inPos, final " + variableType + "[] out, final int outPos) {\n");
        fw.append("    public final void unpack8Values(Object inBase, long inAddr, Object outBase, long outAddr) {\n");

        if (bitWidth > 0) {
            String maskSuffix = isLong ? "L" : "";
            for (int valueIndex = 0; valueIndex < 8; ++valueIndex) {
                //fw.append("      out[" + align(valueIndex, 2) + " + outPos] =\n");
                if (isLong) {
                    fw.append("      setLong(outBase, (" + align(valueIndex, 2) + " << 3) + outAddr, \n");
                } else {
                    fw.append("      setInt(outBase, (" + align(valueIndex, 2) + " << 2) + outAddr, \n");
                }

                int startIndex = valueIndex * bitWidth / 8;
                int endIndex = paddedByteCountFromBits((valueIndex + 1) * bitWidth);

                for (int byteIndex = startIndex; byteIndex < endIndex; byteIndex++) {
                    if (byteIndex == startIndex) {
                        fw.append("          ");
                    } else {
                        fw.append("\n        | ");
                    }

                    ShiftMask shiftMask = getShift(fw, bitWidth, isLong, false, byteIndex, valueIndex);

                    String shiftString = ""; // when shift == 0
                    if (shiftMask.shift < 0) {
                        shiftString = ">>  " + (-shiftMask.shift);
                    } else if (shiftMask.shift > 0) {
                        shiftString = "<<  " + shiftMask.shift;
                    }

                    final String byteAccess = "getByte(inBase, " + align(byteIndex, 2) + " + inAddr)";
                    //if (useByteArray) {
                    //    byteAccess = "in[" + align(byteIndex, 2) + " + inPos]";
                    //} else {
                    //     use ByteBuffer#get(index) method
                    //byteAccess = "in.get(" + align(byteIndex, 2) + " + inPos)";
                    //}

                    // Shift the wanted bits to the least significant position and mask them knowing how many bits to get.
                    fw.append(" ((((" + variableType + ")" + byteAccess + ") " + shiftString +
                            ") & " + shiftMask.mask + maskSuffix + ")");
                }
                fw.append(");\n");
            }
        }
        fw.append("    }\n");
    }

    private static void generateUnpack(FileWriter fw, int bitWidth, int batch, boolean isLong, boolean msbFirst, boolean useByteArray)
            throws IOException {
        final String variableType = isLong ? VARIABLE_TYPE_FOR_LONG : VARIABLE_TYPE_FOR_INT;
        final String bufferDataType = useByteArray ? "byte[]" : "ByteBuffer";

        fw.append("    public final void unpack" + (batch * 8) + "Values(final " + bufferDataType + " in, "
                + "final int inPos, final " + variableType + "[] out, final int outPos) {\n");

        if (bitWidth > 0) {
            String maskSuffix = isLong ? "L" : "";
            for (int valueIndex = 0; valueIndex < (batch * 8); ++valueIndex) {
                fw.append("      out[" + align(valueIndex, 2) + " + outPos] =\n");

                int startIndex = valueIndex * bitWidth / 8;
                int endIndex = paddedByteCountFromBits((valueIndex + 1) * bitWidth);

                for (int byteIndex = startIndex; byteIndex < endIndex; byteIndex++) {
                    if (byteIndex == startIndex) {
                        fw.append("          ");
                    } else {
                        fw.append("\n        | ");
                    }

                    ShiftMask shiftMask = getShift(fw, bitWidth, isLong, msbFirst, byteIndex, valueIndex);

                    String shiftString = ""; // when shift == 0
                    if (shiftMask.shift < 0) {
                        shiftString = ">>  " + (-shiftMask.shift);
                    } else if (shiftMask.shift > 0) {
                        shiftString = "<<  " + shiftMask.shift;
                    }

                    final String byteAccess;
                    if (useByteArray) {
                        byteAccess = "in[" + align(byteIndex, 2) + " + inPos]";
                    } else {
                        // use ByteBuffer#get(index) method
                        byteAccess = "in.get(" + align(byteIndex, 2) + " + inPos)";
                    }

                    // Shift the wanted bits to the least significant position and mask them knowing how many bits to get.
                    fw.append(" ((((" + variableType + ")" + byteAccess + ") " + shiftString +
                            ") & " + shiftMask.mask + maskSuffix + ")");
                }
                fw.append(";\n");
            }
        }
        fw.append("    }\n");
    }

    private static long genMask(int bitWidth, boolean isLong) {
        int maxBitWidth = isLong ? MAX_BITS_FOR_LONG : MAX_BITS_FOR_INT;
        if (bitWidth >= maxBitWidth) {
            // -1 is always ones (11111...1111). It covers all it can possibly can.
            return -1;
        }

        long mask = 0;
        for (int i = 0; i < bitWidth; i++) {
            mask <<= 1;
            mask |= 1;
        }
        return mask;
    }

    private static String align(int value, int digits) {
        final String valueString = String.valueOf(value);
        StringBuilder result = new StringBuilder();
        for (int i = valueString.length(); i < digits; i++) {
            result.append(" ");
        }
        result.append(valueString);
        return result.toString();
    }

    // duplicated from BytesUtils to avoid a circular dependency between parquet-common and parquet-generator
    private static int paddedByteCountFromBits(int bitLength) {
        return (bitLength + 7) / 8;
    }
}
