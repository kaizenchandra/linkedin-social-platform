package dev.network.api;

import io.micrometer.core.instrument.MeterRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.handler.timeout.WriteTimeoutHandler;
import reactor.netty.http.server.HttpServerResponse;

/** Client-facing write deadline applied by the authenticated SSE route filter. */
final class StreamTransport {
  static void bound(HttpServerResponse response, MeterRegistry metrics) {
    // A stream socket is not reused for ordinary HTTP requests with different timeouts.
    response
        .keepAlive(false)
        .withConnection(
            connection -> {
              connection.channel().config().setOption(ChannelOption.SO_SNDBUF, 65536);
              connection
                  .channel()
                  .config()
                  .setWriteBufferWaterMark(new WriteBufferWaterMark(16384, 32768));
              connection.addHandlerLast(
                  "streamWriteDeadline",
                  new WriteTimeoutHandler(5) {
                    @Override
                    protected void writeTimedOut(ChannelHandlerContext context) throws Exception {
                      metrics.counter("network.stream.gateway.slow.clients").increment();
                      super.writeTimedOut(context);
                    }
                  });
            });
  }
}
