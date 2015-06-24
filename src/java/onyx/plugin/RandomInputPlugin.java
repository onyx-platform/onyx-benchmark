package onyx.plugin;

import onyx.peer.IPipeline;
import onyx.peer.IPipelineInput;
import onyx.peer.Function;
import clojure.lang.IPersistentMap;
import clojure.lang.PersistentArrayMap;
import clojure.lang.PersistentVector;
import clojure.lang.Keyword;
import java.util.UUID;


public class RandomInputPlugin implements IPipelineInput, IPipeline
{
	private int maxPending;
	private Keyword pluginName = Keyword.intern("java-generator");
	private byte[] hundredBytes;

	private void initBytes ()
	{
		hundredBytes = new byte[100];
		for (byte i = 0; i < 100; i++)
		{
			hundredBytes[i] = i;
		}
	}

	public RandomInputPlugin(IPersistentMap pipelineData) 
	{
		//Integer maxPending = (Integer)(pipelineData.entryAt(Keyword.intern ("onyx", "max-pending"))).val();
		//System.out.println ("maxpending is " + maxPending);
		//System.out.println ((pipelineData.entryAt(Keyword.intern ("onyx", "max-pending"))));
		//System.out.println (pipelineData);

	}

	public IPersistentMap readBatch (IPersistentMap event) 
	{
		IPersistentMap payload = PersistentArrayMap.EMPTY.assoc (Keyword.intern ("data"), hundredBytes);
		IPersistentMap m = PersistentArrayMap.EMPTY;
		m = m.assoc (Keyword.intern("id"), java.util.UUID.randomUUID())
		     .assoc (Keyword.intern("input"), pluginName)
		     .assoc (Keyword.intern("message"), payload.assoc (Keyword.intern ("n"), new Integer (0)));

		PersistentVector batch = PersistentVector.EMPTY;
		for (int i = 0; i < 20; i++)
		{
			batch = batch.cons (m);
		}

		return (PersistentArrayMap.EMPTY).assoc (Keyword.intern ("onyx.core", "batch"), batch);
	}

	public Object ackMessage (IPersistentMap event, java.util.UUID messageId) 
	{
		return null;
	}

	public Object isPending (IPersistentMap event, java.util.UUID messageId) 
	{
		return null;
	}

	public Object retryMessage (IPersistentMap event, java.util.UUID messageId)
	{
		System.out.println ("Should be retrying " + messageId);
		return null;
	}

	public boolean isDrained (IPersistentMap event)
	{
		return false;
	}

	public Object sealResource (IPersistentMap event)
	{
		return null;
	}

	public IPersistentMap writeBatch (IPersistentMap event)
	{
		return (IPersistentMap)Function.write_batch (event);
	}
}
