package io.netty.handler.dns;

import java.util.List;
import java.util.concurrent.Callable;

public class SingleResultCallback<T> implements Callable<T> {

	private final DnsCallback<List<T>> parent;

	public SingleResultCallback(DnsCallback<List<T>> parent) {
		this.parent = parent;
	}

	@Override
	public T call() throws InterruptedException {
		List<T> list = parent.call();
		if (list == null || list.isEmpty())
			return null;
		return list.get(0);
	}

}
