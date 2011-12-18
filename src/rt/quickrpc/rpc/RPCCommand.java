package rt.quickrpc.rpc;


public abstract class RPCCommand {
	public abstract RPCArgs execute (RPCArgs args);
}
