package rt.util;

import java.io.IOException;
import java.nio.*;
import java.io.OutputStream;

public class ByteBufferOutputStream extends OutputStream {
	ByteBuffer buf;
	
	public ByteBufferOutputStream (ByteBuffer buf) {
		this.buf = buf;
	}

	@Override
	public void write(int b) throws IOException {
		this.buf.put ((byte) b);
	}
}
