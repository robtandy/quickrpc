package rt.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class ByteBufferInputStream extends InputStream {
	ByteBuffer buf;
	
	public ByteBufferInputStream (ByteBuffer buf) {
		this.buf = buf;
	}


	@Override
	public int read() throws IOException {
		// TODO Auto-generated method stub
		return (int) buf.get();
	}

}
