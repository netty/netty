package io.netty.handler.dns;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;

import java.util.List;

import bakkar.mohamed.dnscodec.DnsResponse;
import bakkar.mohamed.dnscodec.Resource;

public class ResponseHandler extends ChannelInboundMessageHandlerAdapter<DnsResponse> {

	private final DnsTransmission parent;

	public ResponseHandler(DnsTransmission parent) {
		this.parent = parent;
	}

	@Override
	public void messageReceived(ChannelHandlerContext ctx,
			DnsResponse object) throws Exception {
		DnsResponse response = (DnsResponse) object;
		int id = response.getHeader().getId();
		DnsTransmission resolver = DnsTransmission.forId(id);
		if (resolver == null)
			return;
		List<Resource> answers = response.getAnswers();
		for (Resource answer : answers) {
			if (answer.type() == resolver.question().type()) {
				ByteBuf info = answer.content();
				byte[] content = info.array();
				DnsCache.submitAnswer(parent.name(), content, answer.timeToLive());
				parent.setResult(content);
				synchronized (resolver) {
					resolver.notify();
				}
				break;
			}
		}
	}

}
