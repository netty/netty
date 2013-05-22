package bakkar.mohamed.dnsresolver;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.nio.NioDatagramChannel;
import bakkar.mohamed.dnscodec.DnsQueryEncoder;
import bakkar.mohamed.dnscodec.DnsResponseDecoder;

public class TransmissionInitializer extends ChannelInitializer<NioDatagramChannel> {

	@Override
	protected void initChannel(NioDatagramChannel ch) throws Exception {
		ch.pipeline()
			.addLast("decoder", new DnsResponseDecoder())
			.addLast("encoder", new DnsQueryEncoder())
			.addLast("handler", new ResponseHandler());
	}

}
