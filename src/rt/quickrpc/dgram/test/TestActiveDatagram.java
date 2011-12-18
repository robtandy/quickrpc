package rt.quickrpc.dgram.test;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.Semaphore;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import rt.quickrpc.dgram.ActiveConnection;
import rt.quickrpc.dgram.ActiveDatagram;
import rt.util.BitPrint;

public class TestActiveDatagram {
	public InetSocketAddress listenAddress = null;
	public Semaphore sem = new Semaphore(1);
	ActiveDatagram sd = null;
	ActiveDatagram ld = null;
	ActiveConnection origin = null;
	int drop_percent = 0;
	
	public void test () {
		final TestActiveDatagram me = this;
		try {
			sem.acquire();
		} catch (Exception e) {
			System.out.println (e.toString ());
		}
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
			ld = new ActiveDatagram(0, true);
			ld.setDropPercent(drop_percent);
			listenAddress =  (InetSocketAddress) ld.getLocalAddress();
			sem.release ();
			System.out.println ("listening address is " + listenAddress);
		} catch (IOException e) {
			System.err.println ("Could not create AD " + e.toString());
			System.exit (1);
		}		
		for (int i = 0; i < 50; i++) {
			buf.clear ();
			origin = ld.receive(buf);
			if (origin != null) {
				System.out.println ("receiving " + i + " " + BitPrint.byteString (buf, buf.remaining ()));
						
				ld.send (buf, origin.getAddress());
				System.out.println ("Reply sent");
			}
		}
	}
	
	
	public void listen () {
		ByteBuffer buf = ByteBuffer.allocate (1000);
		try {	
			ld = new ActiveDatagram(0, true);
			listenAddress =  (InetSocketAddress) ld.getLocalAddress();
			sem.release ();
		} catch (IOException e) {
			System.err.println ("Could not create AD " + e.toString());
			System.exit (1);
		}		
		for (int i = 0; i < 3; i++) {
			origin = ld.receive(buf);
			if (origin != null)
				System.out.println ("receiving " + BitPrint.byteString (buf, buf.remaining ()));
		}
	}
	
	public void send () {

		ByteBuffer buf = ByteBuffer.allocate (1000);
		buf.put("hello world".getBytes());
		buf.flip ();
		try {
			// acquire ensures that the listen thread is already listening
			sem.acquire();
			sd = new ActiveDatagram(0, true);
		} catch (Exception e) {
			System.err.println ("Could not create AD " + e.toString ());
			System.exit (1);
		}
		for (int i = 0; i < 50; i++) {
			System.out.println ("sending " + BitPrint.byteString (buf, buf.limit ()));
			sd.send (buf, new InetSocketAddress ("127.0.0.1", listenAddress.getPort()));
			
		}
	}
	public static void main (String args []) {

    Logger logger = Logger.getLogger("");
       logger.setLevel(Level.ALL);
       for (Handler h : logger.getHandlers()) {
    	   h.setLevel(Level.ALL);
       }
       logger.finest("This is information message for testing ConsoleHandler");
       
       TestActiveDatagram t = new TestActiveDatagram ();
       if (args[0].equals ("send")) { 
    	   t.listenAddress = new InetSocketAddress (args[1], Integer.valueOf (args[2]).intValue ());
    	   t.send ();
       }
       else if (args[0].equals ("echo")) {
    	   t.drop_percent = Integer.valueOf (args[1]);
    	   t.echo ();
       }
	}
}
