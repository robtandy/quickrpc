package rt.quickrpc.dgram;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Collection;
import java.util.HashMap;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import rt.util.BitPrint;

public class ActiveDatagram {
	static Logger log = Logger.getLogger("rt.quickrpc.dgram.ActiveDatagram");
	
	short header_size;
	int protocol_id = 0x11001100;
	int packet_id = 0;
	DatagramChannel channel;
	HashMap <SocketAddress,ActiveConnection> connections;
	ByteBuffer databuf;
	int drop_percent;
	Random random;
	ByteBuffer header;
	ByteBuffer logb;
	
	public static final int MAX_SIZE = 60000;
	
	class BuildHeaderException extends Exception {
		public static final long serialVersionUID = 1L;
	}
	
	public ActiveDatagram (int port, boolean blocking) throws IOException {
		log.info("Creating ActiveDatagram on port " + port);
		header_size = 16; // 16 bytes
		channel = DatagramChannel.open();
		channel.configureBlocking(blocking);
		channel.socket().bind(new InetSocketAddress(port));
		databuf = ByteBuffer.allocateDirect (MAX_SIZE);
		connections = new HashMap <SocketAddress, ActiveConnection> ();
		header = ByteBuffer.allocateDirect (header_size);
		random = new Random ();	
		logb = ByteBuffer.allocate(4);
	}
	
	public void setDropPercent (int dp) {drop_percent = dp;}
	
	public SocketAddress getLocalAddress () {
		return channel.socket ().getLocalSocketAddress();
	}
	
	public Collection<ActiveConnection> getConnections () { return connections.values(); }
	
	ByteBuffer getHeader (ByteBuffer buf) {
		header.clear();
	
		for (int i = 0; i < header_size; i++) header.put (buf.get ());
		
		header.flip ();
		
		if (log.isLoggable(Level.FINEST)) {
			log.finest("getHeader: got " + BitPrint.byteString(header, header_size));
		}
		
		return header;
	}
	
	boolean decodeHeader (ByteBuffer buf, ActiveConnection ac) {
		
		
		int the_protocol_id = buf.getInt();
		if (the_protocol_id != protocol_id) return false;
		
		int packet_id = buf.getInt ();
		
		ac.receivers_latest_ack = buf.getInt ();
		ac.receivers_ack_list = buf.getInt ();
		
		ac.received_packets_index++;
		ac.num_received_packets++;
		ac.received_packets_index %= 33;
		ac.received_packets[ac.received_packets_index] = packet_id;
		
		if (log.isLoggable(Level.FINER)) {
			buf.mark ();
			buf.rewind ();
			log.finer("decode header: packet id: " + packet_id +
			    " " + BitPrint.byteString(buf, header_size));
			buf.reset ();
		}
		
		if (log.isLoggable(Level.FINEST)) {
			logb.clear ();
			logb.putInt (ac.receivers_ack_list);
			logb.flip ();
			log.finest("decodeHeader: ack list: |" + BitPrint.bitString(logb, 32) + "|");
		}
		
		return true;
	}
	
	ByteBuffer buildHeader (ActiveConnection ac) throws BuildHeaderException {
		
		header.clear ();
		
		// 4 bytes for proto id
		header.putInt(protocol_id);
		// 4 bytes for packet id
		packet_id++;
		header.putInt (packet_id);
		// the last packet we received from this
		int latest_packet = ac.received_packets[ac.received_packets_index];
		header.putInt (latest_packet);
		
		// ok, now build the 4 bytes representing the 32 packets previous
		// to latest_packet
		int ack_list = 0;
		int num_old_packets = ac.num_received_packets - 1;
		if (num_old_packets > 32) num_old_packets = 32;
		int j = ac.received_packets_index;
		
		for (int i = 1; i <= num_old_packets; i++) {
			j--;
			if (j < 0) j = 32;
			
			assert (j != ac.received_packets_index);
			// look at this packet id
			int a_packet = ac.received_packets[j];
			if (a_packet > latest_packet) {
				log.warning("invalid packet id detected.  Early reconnect?");
				throw new BuildHeaderException ();
			}
			// see if its less than 33 away from the latest packet, if so, 
			// it belongs in the ack list
			if ((a_packet + 33) > latest_packet) {
				int offset = latest_packet - a_packet - 1;
				assert (0 <= offset);
				assert (offset <= 31);
				ack_list |= (1 << offset);
			}
		}
		// last 4 bytes is the ack list
		header.putInt (ack_list);
		if (log.isLoggable(Level.FINEST)) {
			logb.clear ();
			logb.putInt (ack_list);
			logb.flip ();
			log.finest("buildHeader: ack list: |" + BitPrint.bitString(logb, 32) + "|");
		}
		
		header.flip ();
		
		log.finest ("build header: packet_id: " + packet_id + " " +
			BitPrint.byteString(header, header_size));
		
		header.rewind ();
		
		return header;
	}
	
	public ActiveConnection send (ByteBuffer buf, InetSocketAddress addr) {
		buf.mark ();

		// get a connection for this packet 
		ActiveConnection connection = connections.get(addr);
		if (connection == null) { 
			connection = makeNewConnection (addr);
			connections.put(addr, connection); 
		}
		
		//if connection is too old, remove it
		if (!connection.isActive ()) {
			connections.remove(addr);
			log.info("Removing old connection due to inactivity " + connection.address);
			return null;
		}

		try {
			header = buildHeader (connection);
		} catch (BuildHeaderException e) {
			//FIXME! don't be such a jerk and just propagate this nicely
			// note i'm being a jerk as I want to know if this  happens at the moment
			log.severe(e.toString());
			e.printStackTrace();
			System.exit(1);
		}
		
		databuf.clear ();
		databuf.put(header);
		databuf.put(buf);
		databuf.flip ();
		

		log.finest ("send: raw: " + BitPrint.byteString(databuf, databuf.limit()));
		
		try {
			channel.send(databuf, addr);
			connection.last_send_time = System.currentTimeMillis();
		} catch (IOException e) {	
			log.warning(e.toString ());
			return null;
		}
		buf.reset ();
		return connection;
	}
	
	public ActiveConnection receive (ByteBuffer buf) {
		InetSocketAddress origin = null;
		
		try {
			origin = (InetSocketAddress) channel.receive(buf);
			if (origin == null) return null;
			
			if (drop_percent > 0) {
				if (random.nextInt(100) <= drop_percent) {
					log.fine ("Dropping packet");
					return null;
				}
			}
			buf.flip ();
			//log.finest ("receive: raw: " + BitPrint.byteString(buf, buf.limit()));
		} catch (IOException e) {
			log.warning (e.toString());
			return null;
		}
		// get a connection for this packet 
		ActiveConnection connection = connections.get(origin);
		if (connection == null) { 
			connection = makeNewConnection (origin);
			connections.put(origin, connection); 
		}
		
		connection.last_receive_time = System.currentTimeMillis();
		
		log.finer("receive: got msg (" + buf.remaining() + " bytes) from " + origin);
		
		ByteBuffer header = getHeader (buf);
		if (!decodeHeader (header, connection)) {
			log.warning ("received packet with bad header from " + origin);
		}
		return connection;
	}
	
	protected ActiveConnection makeNewConnection (InetSocketAddress addr) {
		return new ActiveConnection (addr);
	}
}
