package org.jboss.netty.handler.codec.http;

import java.text.ParseException;
import java.util.Date;

import junit.framework.Assert;

import org.junit.Test;

public class HttpHeaderDateFormatTest {
	/**
	 * This date is set at "06 Nov 1994 08:49:37 GMT" (same used in example in
	 * RFC documentation)
	 * <p>
	 * http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html
	 */
	private static final Date DATE = new Date(784111777000L);

	@Test
	public void testParse() throws ParseException {
		HttpHeaderDateFormat format = new HttpHeaderDateFormat();

		{
			final Date parsed = format.parse("Sun, 6 Nov 1994 08:49:37 GMT");
			Assert.assertNotNull(parsed);
			Assert.assertEquals(DATE, parsed);
		}
		{
			final Date parsed = format.parse("Sun, 06 Nov 1994 08:49:37 GMT");
			Assert.assertNotNull(parsed);
			Assert.assertEquals(DATE, parsed);
		}
		{
			final Date parsed = format.parse("Sunday, 06-Nov-94 08:49:37 GMT");
			Assert.assertNotNull(parsed);
			Assert.assertEquals(DATE, parsed);
		}
		{
			final Date parsed = format.parse("Sunday, 6-Nov-94 08:49:37 GMT");
			Assert.assertNotNull(parsed);
			Assert.assertEquals(DATE, parsed);
		}
		{
			final Date parsed = format.parse("Sun Nov 6 08:49:37 1994");
			Assert.assertNotNull(parsed);
			Assert.assertEquals(DATE, parsed);
		}
	}

	@Test
	public void testFormat() {
		HttpHeaderDateFormat format = new HttpHeaderDateFormat();

		final String formatted = format.format(DATE);
		Assert.assertNotNull(formatted);
		Assert.assertEquals("Sun, 06 Nov 1994 08:49:37 GMT", formatted);
	}
}
