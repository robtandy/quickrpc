package rt.quickrpc.rpc;

import java.util.HashMap;

public class RPCUserData {
	public HashMap <Integer, RPCCommand> callbacks;
	
	protected static final int SIZE = 250;
	protected int[] rpc_ids; // holds ids of rpc_calls we've executed, so we can drop dupes
	protected int index;
	
	public RPCUserData () {
		callbacks = new HashMap <Integer, RPCCommand> ();
		rpc_ids = new int [SIZE];
		for (int i = 0; i < SIZE; i++) rpc_ids[i] = -1;
		index = 0;
	}
	
	public boolean store (int id) {
		boolean have_it = false;

		// check the previous ids, starting
		// with most recent first, as dupes are likely
		// to be back to back or close
		int j = index;
		for (int i = 0; i < SIZE; i++) { 
			if (rpc_ids[j] == id) {
				have_it = true;
				break;
			}
			j--;
			if (j < 0) j = SIZE - 1;
		}
		if (!have_it) {
			rpc_ids[index] = id;
			
			index++; index %= SIZE;
		}
		return have_it;
	}
}
