package rt.quickrpc.rpc;

import java.io.Serializable;
import java.util.Random;

public class RPCWire implements Serializable {
	public static final long serialVersionUID = 1L;
	public static final Random rand = new Random ();
	public String name;
	public RPCArgs args;
	public int rpc_id;
	public boolean is_reply;
	public boolean response_required;
	public boolean is_keep_alive;
	public int reply_id;
	
	public RPCWire () {
		rpc_id = -1;  // so first rpc sent is id = 1
		prepare ();
	}
	
	public void prepare () {
		is_reply = false;
		is_keep_alive = false;
		response_required = false;
		reply_id = -1;
		rpc_id++;
		
	}
	
	public String toString () {
		if (is_reply)
			return "WIRE: reply:" + "reply_id:" + reply_id;
		else if (is_keep_alive)
			return "WIRE: keep_alive";
		else 
			return "WIRE: rpc_id:" + rpc_id + " name:" + name;
	}
}
