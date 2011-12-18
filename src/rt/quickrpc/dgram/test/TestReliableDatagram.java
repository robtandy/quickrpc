package rt.quickrpc.dgram.test;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import rt.quickrpc.dgram.ActiveConnection;
import rt.quickrpc.dgram.ReliableDatagram;

public class TestReliableDatagram {
	public InetSocketAddress listenAddress = null;
	ReliableDatagram sd = null;
	ReliableDatagram ld = null;
	ActiveConnection origin = null;
	int drop_percent = 0;
	
	public void test () {
		final TestReliableDatagram me = this;

		new Thread ( new Runnable () {
			public void run () {
				me.echo ();
			}
		}).start ();
	
		
		new Thread ( new Runnable () {
			public void run () {
				me.send ();
			}
		}).start ();

	}
	
	public void echo () {
		ByteBuffer buf = ByteBuffer.allocate (1000);
		try {	
			ld = new ReliableDatagram(listenAddress.getPort (), true);
			ld.setDropPercent(drop_percent);
			System.out.println ("listening address is " + listenAddress);
		} catch (IOException e) {
			System.err.println ("Could not create AD " + e.toString());
			System.exit (1);
		}
		
		while (true) {
			buf.clear ();
			origin = ld.receive(buf);
			byte [] ba = new byte [1000];
			if (origin != null) {
				//System.out.println ("receiving " + BitPrint.byteString (buf, buf.remaining ()));
				buf.get (ba, 0, buf.remaining());
				System.out.println ("receiving " + new String (ba));

				ld.send (buf, origin.getAddress());
				System.out.println ("Reply sent");
			}	
		}
	}
	

	public void send () {

		ByteBuffer buf = ByteBuffer.allocate (1000);
		buf.put("hello world".getBytes());
		buf.flip ();
		try {
			sd = new ReliableDatagram(0, false);
		} catch (Exception e) {
			System.err.println ("Could not create AD " + e.toString ());
			System.exit (1);
		}
		ByteBuffer receive_buf = ByteBuffer.allocate(1000);
				
		for (int i = 0; i < 3; i++) {

			sd.reliableSend (buf, new InetSocketAddress ("127.0.0.1", listenAddress.getPort()));
			try {
				Thread.sleep(100);	
			} catch (InterruptedException e) {}
			receive_buf.clear ();
			sd.receive (receive_buf);
			sd.retransmit();
		}
		while (sd.numOutstandingReliables() > 0) {
			receive_buf.clear ();
			sd.receive (receive_buf);
			sd.retransmit();
			try { Thread.sleep (100); } catch (InterruptedException e) {}
		}
	}
	public static void main (String args []) {

    Logger logger = Logger.getLogger("");
       logger.setLevel(Level.ALL);
       for (Handler h : logger.getHandlers()) {
    	   h.setLevel(Level.ALL);
       }
       logger.finest("This is information message for testing ConsoleHandler");
       
       TestReliableDatagram t = new TestReliableDatagram ();
       if (args[0].equals ("send")) { 
    	   //t.listenAddress = new InetSocketAddress (args[1], Integer.valueOf (args[2]).intValue ());
    	   t.listenAddress = new InetSocketAddress ("127.0.0.1", 3000);
    	   t.send ();
       }
       else if (args[0].equals ("echo")) {
       	   t.listenAddress = new InetSocketAddress (args[1], Integer.valueOf (args[2]).intValue ());
    	   t.drop_percent = Integer.valueOf (args[3]);
    	   t.echo ();
       }
	}
}
