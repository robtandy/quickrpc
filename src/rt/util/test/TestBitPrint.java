package rt.util.test;
import java.nio.ByteBuffer;

import rt.util.BitPrint;

public class TestBitPrint {
	public static void main (String args[]) {
		ByteBuffer buf = ByteBuffer.allocate (16);
		for (int i = 0; i < 16; i++)
			buf.put((byte)i);

		
		buf.flip ();
		System.out.println (BitPrint.bitString(buf, 16*8 /*16 byte */));
		
		buf.rewind ();
		for (int i = 0; i < 16; i++) {
			ByteBuffer b = buf.duplicate();
			for (int j = 0; j < i; j++) b.get ();
			System.out.println (BitPrint.bitString (b, 8/* 1 byte */));
		}
		buf.rewind();
		System.out.println (BitPrint.byteString(buf, 16));
	}
}
