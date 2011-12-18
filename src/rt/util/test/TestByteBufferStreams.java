package rt.util.test;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;

import rt.util.ByteBufferInputStream;
import rt.util.ByteBufferOutputStream;

public class TestByteBufferStreams {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		ByteBuffer buf = ByteBuffer.allocate (1000);
		ByteBufferOutputStream bbos = new ByteBufferOutputStream (buf);
		
		Double d = new Double (2.0);
		try {
			ObjectOutputStream o = new ObjectOutputStream (bbos);
			o.writeObject(d);
			o.close();
			
			buf.flip ();
			ByteBufferInputStream bbis = new ByteBufferInputStream (buf);
			ObjectInputStream i = new ObjectInputStream (bbis);
			Object myo = i.readObject();
			Double myd = (Double) myo;
			System.out.println ("got " + myd);
		} catch (Exception e) {	
			System.out.println (e);
		}
	
		
	}

}
