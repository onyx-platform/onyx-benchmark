package onyx.plugin;

import onyx.interop;
import onyx.IPipelineInput;
import onyx.IPipeline;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import clojure.lang.IPersistentMap;
import clojure.lang.PersistentArrayMap;
import clojure.lang.PersistentVector;
import clojure.lang.Keyword;


public class RandomInputPlugin implements IPipelineInput, IPipeline
{
	private Keyword pluginName = Keyword.intern("java-generator");
	private byte[] hundredBytes;
	private int maxPending;
	private int batchSize;
	private ConcurrentHashMap<UUID, IPersistentMap> pendingMessages;
	private ConcurrentLinkedQueue<IPersistentMap> pendingRetries;

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
		initBytes();

		IPersistentMap taskMap = (IPersistentMap)pipelineData.entryAt(Keyword.intern ("onyx.core", "task-map")).val();

		// TODO, implement onyx defaults for max-pending
		maxPending = ((Long) (taskMap.entryAt(Keyword.intern ("onyx", "max-pending"))).val()).intValue();
		batchSize = ((Long) (taskMap.entryAt(Keyword.intern ("onyx", "batch-size"))).val()).intValue();
		pendingMessages = new ConcurrentHashMap<UUID, IPersistentMap> ();
		pendingRetries = new ConcurrentLinkedQueue <IPersistentMap> ();
	}

	/* Add any segments that have been added to the retry queue to the batch */
	public PersistentVector addRetries (PersistentVector batch, int count)
	{
		for (int i = 0; i < count; i++)
		{
			IPersistentMap message = pendingRetries.poll();
			if (message != null)
			{
				UUID id = UUID.randomUUID();
				IPersistentMap m = (PersistentArrayMap.EMPTY)
					.assoc (Keyword.intern("id"), id)
					.assoc (Keyword.intern("input"), pluginName)
					.assoc (Keyword.intern("message"), message);
				batch = batch.cons (m);
				pendingMessages.put(id, message);
			}
		}
		return batch;
	}

	/* Fill up batch with newly generated segments */
	public PersistentVector addGenerated (PersistentVector batch, int count)
	{
		IPersistentMap payload = PersistentArrayMap.EMPTY.assoc (Keyword.intern ("data"), hundredBytes);
		for (int i = 0; i < count; i++)
		{
			UUID id = UUID.randomUUID();

			IPersistentMap message = payload.assoc (Keyword.intern ("n"), new Integer (i));
			IPersistentMap m = (PersistentArrayMap.EMPTY)
				.assoc (Keyword.intern("id"), id)
				.assoc (Keyword.intern("input"), pluginName)
				.assoc (Keyword.intern("message"), message);

			batch = batch.cons (m);
			pendingMessages.put(id, message);
		}

		return batch;
	}

	public IPersistentMap readBatch (IPersistentMap event) 
	{
		PersistentVector batch = PersistentVector.EMPTY;

		int retryCount = Math.min (pendingRetries.size(), batchSize);
		int generatedCount = Math.max (0, Math.min (maxPending - retryCount - pendingMessages.size(), batchSize - retryCount));

		batch = addGenerated(addRetries (batch, retryCount), generatedCount);

		return (PersistentArrayMap.EMPTY).assoc (Keyword.intern ("onyx.core", "batch"), batch);
	}

	// ackMessage should maybe be void return
	public Object ackMessage (IPersistentMap event, UUID messageId) 
	{
		pendingMessages.remove(messageId);
		return null;
	}

	// isPending should return the message
	public Object isPending (IPersistentMap event, UUID messageId) 
	{
		IPersistentMap message = pendingMessages.get(messageId);
		return message;
	}

	// retry should return the message if it existed, otherwise return null.
	public Object retryMessage (IPersistentMap event, UUID messageId)
	{
		IPersistentMap message = pendingMessages.get(messageId);
		if (message == null)
		{
			return null;
		}

		pendingMessages.remove(messageId);
		pendingRetries.add(message);
		return message;
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
		return (IPersistentMap)interop.write_batch (event);
	}
}
