package rt.quickrpc.dgram;

import java.net.InetSocketAddress;
import java.util.logging.Logger;

public class ActiveConnection {
	static Logger log = Logger.getLogger("rt.quickrpc.dgram.ActiveConnection");

	
	public InetSocketAddress address;
	public int[] received_packets;
	public short received_packets_index;
	public int num_received_packets;
	public int[] acked_packets;
	public int receivers_ack_list;
	public int receivers_latest_ack;
	public long last_receive_time;
	public long last_send_time;
	public short timeout;
	public Object userData;
	
	public ActiveConnection (InetSocketAddress sa) {
		log.finest("ActiveConnection (" + sa + ")");
		address = sa;
		num_received_packets = 0;
		// technically, not accurate when packets received = 0,
		// but needed for isActive to work well
		last_receive_time = System.currentTimeMillis();
		received_packets = new int [33];
		received_packets_index = 0; // represents the latest received packet
		acked_packets = new int [33];
		receivers_ack_list = 0;
		receivers_latest_ack = 0;
		timeout = 5;
		userData = null;
	}
	
	public Object getUserData () {return userData;}
	public void setUserData (Object o) {userData = o;}
	public InetSocketAddress getAddress () {return address;}
	
	public boolean isActive () {
		long now = System.currentTimeMillis();
		boolean retval = true;
		//if (num_received_packets > 0 && (now - last_receive_time > (timeout *1000))) {
		if (now - last_receive_time > (timeout *1000)) {
			retval = false;
		}
		log.finest("(" + address + ") isActive = " + (retval == false ? "False" : "True") 
				+ " rec:" + num_received_packets);

		return retval;
	}
	public String toString () {
		return address.toString ();
	}
}
