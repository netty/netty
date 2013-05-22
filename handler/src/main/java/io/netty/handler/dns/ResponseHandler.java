package bakkar.mohamed.dnsresolver;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;

import java.util.List;

import bakkar.mohamed.dnscodec.DnsResponse;
import bakkar.mohamed.dnscodec.Resource;

public class ResponseHandler extends ChannelInboundMessageHandlerAdapter<DnsResponse> {

	@Override
	public void messageReceived(ChannelHandlerContext ctx,
			DnsResponse object) throws Exception {
		DnsResponse response = (DnsResponse) object;
		int id = response.getHeader().getId();
		DnsExchange resolver = DnsExchange.forId(id);
		if (resolver == null) {
			return;
		}
		List<Resource> answers = response.getAnswers();
		for (Resource answer : answers) {
			if (answer.type() == resolver.question().type()) {
				DnsCache.submitAnswer(resolver.name(), answer.type(), answer.content(), answer.timeToLive());
				resolver.setResult(answer.content());
				synchronized (resolver) {
					resolver.notify();
				}
				return;
			}
		}
	}

}
