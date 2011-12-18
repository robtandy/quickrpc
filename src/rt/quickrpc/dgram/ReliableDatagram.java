package rt.quickrpc.dgram;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Vector;

public class ReliableDatagram extends ActiveDatagram {
	public class Bundle {
		public static final int PACKET_ID_BUF_SIZE = 10;
		public boolean active;
		public InetSocketAddress dest;
		public int packet_ids[];
		public int last_packet_index = 0;
		public ByteBuffer data = null;
		private long born;
		public long	last_send; 
		
		
		public Bundle () {
			packet_ids = new int [PACKET_ID_BUF_SIZE];
			active = false;
			last_packet_index = 0;
			for (int i = 0; i < PACKET_ID_BUF_SIZE; i++) packet_ids[i] = -1;
			born = System.currentTimeMillis();
			last_send = born;
		}
		
		public long getAge () { return System.currentTimeMillis() - born; }
		

		public boolean isMatch (int id) {
			boolean match = false;
			int j = last_packet_index;
			for (int i = 0; i < PACKET_ID_BUF_SIZE; i++) {
				if (packet_ids[j] == id) {
					match = true;
					break;
				}
				j--;
				if (j < 0) j = PACKET_ID_BUF_SIZE - 1;
			}
			return match;
		}
		public String packetString () {
			String s = "";
			int j = last_packet_index;
			for (int i = 0; i < PACKET_ID_BUF_SIZE; i++) {
				s += " " + packet_ids [j];
				j--;
				if (j < 0) j = PACKET_ID_BUF_SIZE - 1;
			}
			return s;
		}
	};
	
	
	
	Vector<ReliabilityListener> listeners;
	public long retransmit_tolerance = 100; //milliseconds
	
	public ReliableDatagram (int port, boolean blocking) throws IOException {
		super (port, blocking);
		listeners = new Vector<ReliabilityListener> ();
	}
	
	public int numOutstandingReliables () { 
		int total = 0;
		for (ActiveConnection con : getConnections()) {
			total += ((ReliableActiveConnection) con).reliables.size();
		}
		return total;
	}
	
	public long oldestOutstandingReliable () {
		long age = 0, a = 0;
		for (ActiveConnection con : getConnections()) {
			for (Bundle b : ((ReliableActiveConnection) con).reliables) {
				a = b.getAge ();
				if (a > age) age = a;
			}
		}
		return age;
	}
	
	public void addListener (ReliabilityListener l) {listeners.add (l);}
	
	public void retransmit () {
		if (numOutstandingReliables () > 0) 
			log.finer(numOutstandingReliables() + " outstanding reliables");
		
		long now = System.currentTimeMillis();
		for (ActiveConnection con : getConnections()) {
			Iterator<Bundle> it = ((ReliableActiveConnection)con).reliables.iterator ();
			while (it.hasNext()) {
				Bundle b = it.next ();
				ActiveConnection ac = connections.get (b.dest);
				if (ac != null && !ac.isActive ()) {
					//this address was removed due to inactivity, lets give up the fight
					log.finer("removing Bundle " + b.dest + " due to stale connection");
					it.remove();
					continue;
				}
				if (now - b.last_send < retransmit_tolerance) {
					// lets give it some more time to get a reply
					continue;
				}
				b.last_send = now;
				send (b.data, b.dest);
				for (ReliabilityListener l : listeners) l.retransmitted(b);
	
				
				header.flip ();
				// id of last packet sent
				log.finest ("Retransmitting old packet id " + b.packet_ids[b.last_packet_index]);
				b.last_packet_index++;
				b.last_packet_index %= Bundle.PACKET_ID_BUF_SIZE;
				
				header.getInt (); // protocol id - toss it
				b.packet_ids [b.last_packet_index] = header.getInt ();
				log.finest ("new packet id " + b.packet_ids[b.last_packet_index]);
			}
		}
	}

	public ActiveConnection reliableSend (ByteBuffer buf, InetSocketAddress addr) {
		
		ReliableActiveConnection con = (ReliableActiveConnection) send (buf, addr);
			
		if (con != null) {
			// it was sent
			header.flip ();
			header.getInt (); //protocol id - toss it.
			int last_packet_id = header.getInt ();
			Bundle b = new Bundle ();
			b.packet_ids [0] = last_packet_id;
			b.data = ByteBuffer.allocate (buf.capacity());
			buf.rewind ();
			b.data.put(buf);
			b.data.flip ();
			b.dest = addr;
			con.reliables.add(b);
		}
		return con;
	}
	
	
	@Override
	boolean decodeHeader (ByteBuffer buf, ActiveConnection ac ) {
		int packet_id = 0;
		boolean headerOK = super.decodeHeader(buf, ac);
		if (headerOK) { 
			// check the ack list to see if we've received any 
			// acks of our reliables
			log.fine (ac + "latest ack = " + ac.receivers_latest_ack);
			Iterator <Bundle> it = ((ReliableActiveConnection)ac).reliables.iterator ();
			while (it.hasNext()) {
				Bundle b = it.next ();
				log.finest (ac + "checking bundle " + b.packetString());
				// first check the latest ack
				if (b.isMatch(ac.receivers_latest_ack)) {
					log.fine (ac + "got answer for reliable packbet " + b.packetString ());
					for (ReliabilityListener l : listeners) l.reliableAcked (b);
					it.remove ();
				} else {
					// check in the rest of the ack list
					for (int j = 0; j < 32; j++) {
						if ((ac.receivers_ack_list & (1<<j)) != 0) {
							// this bit is a "1"
							packet_id = ac.receivers_latest_ack - j - 1;
							if (b.isMatch(packet_id)) {
								log.fine (ac + "got answer for reliable packet " + b.packetString ());
								for (ReliabilityListener l : listeners) l.reliableAcked (b);
								it.remove ();
								// break here as, if there is high packet loss, its possible
								// to have more than one ack present in the list which will
								// match this bundle
								break;
							}
						}
					}
				}
			}
		}
		return headerOK;

	}
	
	@Override
	protected ActiveConnection makeNewConnection (InetSocketAddress addr) {
		return new ReliableActiveConnection(addr);
	}
}
