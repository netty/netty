package io.netty.handler.dns;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.Resource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class DnsCallback<E> implements Callable<E> {

	private static final Object DEFAULT = new Object();
	private static final Map<Integer, DnsCallback<?>> callbacks = new HashMap<Integer, DnsCallback<?>>();

	public static void finish(DnsResponse response) {
		DnsCallback<?> callback = callbacks.remove(response.getHeader().getId());
		if (callback != null) {
			int[] types = callback.types();
			List<Resource> answers = response.getAnswers();
			for (int i = 0; i < answers.size(); i++) {
				Resource resource = answers.get(i);
				for (int n = 0; n < types.length; n++) {
					if (types[n] == resource.type()) {
						callback.complete(types[n], resource.content());
						try {
							ResourceCache.submitRecord(resource.name(), resource.type(), resource.timeToLive(), callback.call());
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						return;
					}
				}
			}
			callback.complete(-1, null);
		}
	}

	private final int id;
	private final int[] types;

	@SuppressWarnings("unchecked")
	private volatile E result = (E) DEFAULT;

	public DnsCallback(int id, int... types) {
		if (types == null) {
			throw new NullPointerException("Argument 'types' must contain one valid resource type.");
		}
		callbacks.put(this.id = id, this); // Assuming we can't complete 65536 requests before we cycle back to this id
		this.types = types;
	}

	@Override
	public E call() throws InterruptedException {
		if (result == DEFAULT) {
			synchronized (this) {
				if (result == DEFAULT) {
					wait(DnsExchangeFactory.REQUEST_TIMEOUT);
				}
			}
		}
		return result == DEFAULT ? null : result;
	}

	public int id() {
		return id;
	}

	public int[] types() {
		return types;
	}

	@SuppressWarnings("unchecked")
	private void complete(int type, ByteBuf content) {
		switch (type) {

		case -1:
			result = null;
			break;

		case Resource.TYPE_A:
		case Resource.TYPE_AAAA:
			result = (E) content;
			break;

		}
		synchronized (this) {
			notify();
		}
	}

}
