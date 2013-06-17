package io.netty.handler.dns;

import io.netty.channel.Channel;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.Question;
import io.netty.handler.codec.dns.Resource;
import io.netty.handler.dns.decoder.RecordDecoderFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class DnsCallback<T extends List<?>> implements Callable<T> {

	private static final List<Object> DEFAULT = new ArrayList<Object>();
	private static final Map<Integer, DnsCallback<?>> callbacks = new HashMap<Integer, DnsCallback<?>>();

	public static void finish(DnsResponse response) {
		DnsCallback<?> callback = callbacks.get(response.getHeader().getId());
		if (callback != null) {
			DnsQuery[] queries = callback.queries();
			if (response.getHeader().getResponseCode() != 0) {
				if (callback.failsIncremented() >= queries.length) {
					callbacks.remove(response.getHeader().getId());
					callback.complete();
					return;
				}
			}
			List<Resource> resources = new ArrayList<Resource>();
			resources.addAll(response.getAnswers());
			resources.addAll(response.getAuthorityResources());
			resources.addAll(response.getAdditionalResources());
			for (int i = 0; i < resources.size(); i++) {
				Resource resource = resources.get(i);
				for (int n = 0; n < queries.length; n++) {
					Object result = RecordDecoderFactory.decode(resource.type(), response, resource);
					if (result != null) {
						ResourceCache.submitRecord(resource.name(), resource.type(), resource.timeToLive(), result);
						if (queries[n].getQuestions().get(0).type() == resource.type()) {
							callbacks.remove(response.getHeader().getId());
							callback.flagValid(resource.type());
						}
					}
				}
			}
			callback.complete();
		}
	}

	private final DnsQuery[] queries;

	@SuppressWarnings("unchecked")
	private volatile T result = (T) DEFAULT;

	private int fails = 0;
	private int serverIndex;
	private int validType = -1;

	public DnsCallback(int serverIndex, DnsQuery... queries) {
		if (queries == null) {
			throw new NullPointerException("Argument 'queries' cannot be null.");
		}
		if (queries.length == 0) {
			throw new IllegalArgumentException("Argument 'queries' must contain minimum one valid DnsQuery.");
		}
		callbacks.put(queries[0].getHeader().getId(), this); // Assuming we can't complete 65536 requests before we cycle back to this id
		this.queries = queries;
		this.serverIndex = serverIndex;
	}

	private synchronized int failsIncremented() {
		return ++fails;
	}

	@SuppressWarnings("unchecked")
	private void complete() {
		if (validType != -1) {
			Question question = queries[0].getQuestions().get(0);
			result = (T) ResourceCache.getRecords(question.name(), validType);
		} else {
			result = null;
		}
		synchronized (this) {
			notify();
		}
	}

	private void flagValid(int validType) {
		this.validType = validType;
	}

	private void nextDns() {
		if (serverIndex == -1) {
			result = null;
		} else {
			byte[] dnsServerAddress = DnsExchangeFactory.getDnsServer(++serverIndex);
			if (dnsServerAddress == null) {
				result = null;
			} else {
				try {
					Channel channel = DnsExchangeFactory.channelForAddress(dnsServerAddress);
					for (int i = 0; i < queries.length; i++) {
						channel.write(queries[i]).sync();
					}
				} catch (Exception e) {
					e.printStackTrace();
					result = null;
				}
			}
		}
	}

	@Override
	public T call() throws InterruptedException {
		boolean initial = true;
		boolean finished = result != DEFAULT;
		while (!finished) {
			if (!initial) {
				nextDns();
			}
			synchronized (this) {
				if (result == DEFAULT) {
					wait(DnsExchangeFactory.REQUEST_TIMEOUT);
				}
				finished = result != DEFAULT;
			}
			initial = false;
		}
		return result == DEFAULT ? null : result;
	}

	public DnsQuery[] queries() {
		return queries;
	}

}
