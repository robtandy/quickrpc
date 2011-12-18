package rt.quickrpc.rpc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;
import java.util.logging.Logger;

import rt.quickrpc.dgram.ActiveConnection;
import rt.quickrpc.dgram.ReliabilityListener;
import rt.quickrpc.dgram.ReliableDatagram;
import rt.quickrpc.dgram.ReliableDatagram.Bundle;

import com.esotericsoftware.kryo.Kryo;

public class RPCService implements ReliabilityListener {
	
	static Logger log = Logger.getLogger("rt.quickrpc.rpc.RPCService");
	ReliableDatagram dgram;
	HashMap<String, RPCCommand> commands;
	HashMap<String, InetSocketAddress> remotes;
	ByteBuffer send_buf;
	ByteBuffer receive_buf;
	Kryo kryo;
	RPCWire wire;
	RPCCommand empty_callback;
	long last_send_time = 0;
	Vector<ActiveConnection> cons;
	int keep_alive_interval = 500;
	int unexecuted_callbacks = 0;

	public RPCService(int port) throws IOException {
		kryo = new Kryo();
		kryo.register(RPCWire.class);

		dgram = new ReliableDatagram(port, false);
		send_buf = ByteBuffer.allocateDirect(ReliableDatagram.MAX_SIZE);
		receive_buf = ByteBuffer.allocateDirect(ReliableDatagram.MAX_SIZE);
		commands = new HashMap<String, RPCCommand>();
		remotes = new HashMap<String, InetSocketAddress>();
		wire = new RPCWire();
		cons = new Vector<ActiveConnection>();

		empty_callback = new RPCCommand() {
			public RPCArgs execute(RPCArgs args) {
				return null;
			}
		};
		dgram.addListener(this);

	}

	public void setDropPercent(int p) {
		dgram.setDropPercent(p);
	}

	public void registerClass(Class klass) {
		kryo.register(klass);
	}

	public void register(String rpc_method_name, RPCCommand cmd) {
		commands.put(rpc_method_name, cmd);
	}

	public void addRemote(String name, InetSocketAddress addr) {
		remotes.put(name, addr);
	}

	public void remoteReliableCall(String rpc_method_name, RPCArgs args,
			InetSocketAddress addr) {
		if (addr == null) {
			// FIXME: add exception
		} else {
			prepareSend(rpc_method_name, args, empty_callback, false);
			ActiveConnection con = dgram.reliableSend(send_buf, addr);
			postSend(empty_callback, con);
		}
	}

	public void remoteReliableCallWithCallback(String rpc_method_name,
			RPCArgs args, InetSocketAddress addr, RPCCommand callback) {
		if (addr == null) {
			// FIXME: add exception
		} else {
			prepareSend(rpc_method_name, args, callback, false);
			ActiveConnection con = dgram.reliableSend(send_buf, addr);
			if (callback != null)
				postSend(callback, con);
		}
	}

	public void remoteUnreliableCall(String rpc_method_name, RPCArgs args,
			InetSocketAddress addr) {
		if (addr == null) {
			// FIXME: add exception
		} else {
			prepareSend(rpc_method_name, args, null, false);
			dgram.send(send_buf, addr);
		}
	}

	private RPCUserData getUD(ActiveConnection con) {
		Object o = con.getUserData();
		RPCUserData ud;
		if (o == null) {
			ud = new RPCUserData();
			con.setUserData(ud);
		} else {
			// FIXME : read up on this warning
			ud = (RPCUserData) o;
		}
		return ud;
	}

	private void postSend(RPCCommand callback, ActiveConnection con) {
		unexecuted_callbacks++;
		getUD(con).callbacks.put(new Integer(wire.rpc_id), callback);
	}

	private void prepareSend(String rpc_method_name, RPCArgs args,
			RPCCommand callback, boolean is_keep_alive) {
		send_buf.clear();

		wire.prepare();
		if (is_keep_alive)
			wire.is_keep_alive = true;
		wire.name = rpc_method_name;
		wire.args = args;
		if (callback != null) {
			wire.response_required = true;
		}
		kryo.writeObject(send_buf, wire);
		log.fine ("sending:" + wire);
		send_buf.flip();
		last_send_time = System.currentTimeMillis();
	}
	
	

	public boolean canExit() {
		return dgram.numOutstandingReliables() == 0
				&& unexecuted_callbacks == 0;
	}

	public void upkeep() {
		receiveCalls();
		dgram.retransmit();

		// now send keep alives if we haven't spoken to one of our connections
		// recently. This lets the underlying ActiveDatagram send a message
		// back that contains acks for packets we've received. We only
		// need to do this in the absence of any other communications as
		// any other outbound messages will contain ack headers
		
		// we also need to do this when, for example, we have unexecuted callbacks
		/// waiting.  This means that the otherside has reliable packets to send us
		// yet we have nothing to say to them, so, this connection needs to be 
		// kept alive such that the other side will not give up on us.
		
		// so, there are two cases, if we have outstanding retransmits, or unexecuted callbacks
		// so this should be done in a loop like,
		// while (!canExit) upkeep ();

		cons.clear();

		if (dgram.getConnections().size() > 0)
			log.finest("size of cons" + dgram.getConnections().size());
		Iterator<ActiveConnection> it = dgram.getConnections().iterator();
		while (it.hasNext()) {
			ActiveConnection con = it.next();
			if (System.currentTimeMillis() - con.last_send_time > keep_alive_interval) {

				cons.add(con);
			}
		}
		// can't call send in above loop as dgram.send modifies its active
		// connection list
		for (ActiveConnection con : cons) {
			log.finest("sending keepalive to " + con);
			prepareSend(null, null, null, true);
			dgram.send(send_buf, con.getAddress());
		}
	}
	
	protected RPCWire deserialize (ByteBuffer b) {
		return kryo.readObject(b, RPCWire.class);
	}

	public void receiveCalls() {
		ActiveConnection connection = null;
		while (true) {
			receive_buf.clear();
			connection = dgram.receive(receive_buf);
			if (connection == null) {
				break;
			} else {
				RPCWire w = deserialize (receive_buf);
				RPCUserData ud = getUD(connection);
				log.fine ("receive: got " + w);
				if (w.is_keep_alive) {
					log.fine ("keep alive");
				} else if (!w.is_reply) {
					if (!ud.store(w.rpc_id)) {
						log.fine ("executing call for rpc_id:" + w.rpc_id);
						RPCArgs ret = performCall(w);
						if (w.response_required) {
							send_buf.clear();
							wire.name = null;
							wire.args = ret;
							wire.is_reply = true;
							wire.is_keep_alive = false;
							wire.reply_id = w.rpc_id;
							kryo.writeObject(send_buf, wire);
							log.fine ("sending: " + wire);
							send_buf.flip();
							dgram.reliableSend(send_buf,
									connection.getAddress());
						}
					} else {
						log.finest("discarding duplicate request for rpc_id "
								+ w.rpc_id);
					}

				} else {
					log.finer ("got a rpc response rpc_id " + w.rpc_id);
					// this is a reply
					RPCCommand c = ud.callbacks
							.get(Integer.valueOf(w.reply_id));
					log.fine("received reply for rpc_id " + w.reply_id);
					if (c == null) {
						// this means, assuming everything works like we
						// designed, that
						// we have already received this reply. No need to do
						// anything
						log.finer("dropping duplicate response for rpc_id "
								+ w.reply_id);
					} else {
						log.fine ("executing callback for reply_id:" + w.reply_id);
						c.execute(w.args);
						unexecuted_callbacks--;
						log.fine("unexcuted: " + unexecuted_callbacks);
						ud.callbacks.remove(Integer.valueOf(w.reply_id));
					}
				}
			}
		}
	}

	protected RPCArgs performCall(RPCWire w) {
		RPCCommand cmd = commands.get(w.name);
		return cmd.execute(w.args);
	}

	@Override
	public void retransmitted(Bundle b) {
		b.data.mark ();
		RPCWire w = deserialize (b.data);
		log.fine("retransmitted:" + w);
		b.data.reset ();
	}

	@Override
	public void reliableAcked(Bundle b) {
		b.data.mark ();
		RPCWire w = deserialize (b.data);
		log.fine ("ack for:" + w);
		b.data.reset ();
		
	}
	
	public long maxRequestAge () {
		return dgram.oldestOutstandingReliable();
	}
}
