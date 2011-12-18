package rt.util;

import java.nio.ByteBuffer;

public class BitPrint {
	public static String bitString (ByteBuffer buf, int num_bits) {
		buf.mark();
		
		int count = 0;
		String s = "";
		byte b = 0;
		
		while (count < num_bits) {
			int offset = count % 8;
			
			if (offset == 0) {
				b = buf.get();
			}
			// remember we're coming in from the right most bit, but that is the 
			// last element in the string, so we want to check the bit marked by 1 << (7-offset)
			int bit = (b & (1<<(7-offset)));
			if (bit != 0) s += '1';
			else s += '0';
			count++;
		}
		buf.reset();
		return s;
	}
	public static String byteString (ByteBuffer buf, int num_bytes) {
		int count = 0;
		String s = "";
		byte b;
		
		buf.mark ();
		
		while (count < num_bytes) {
			b = buf.get ();
			s += String.format ("0x%x", b);

			count ++;
			if (count < num_bytes) s += " ";
		}
		buf.reset ();
		return s;
	}
}
