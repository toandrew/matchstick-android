package nz.co.iswe.android.airplay.network.airplay;

import nz.co.iswe.android.airplay.AirPlayServer;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.jboss.netty.handler.codec.http.HttpMessageDecoder;
import org.jboss.netty.handler.codec.http.HttpMessageEncoder;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;

import android.content.Context;
import android.util.Log;

/**
 * Factory for Airplay channels
 */
public class AirplayPipelineFactory implements ChannelPipelineFactory {
    private Context context;

    AirplayServerChannelHandler mAirplayServerChannelHandler;

    public AirplayPipelineFactory(Context context) {
        // TODO Auto-generated constructor stub
        this.context = context;
    }

    @Override
    public ChannelPipeline getPipeline() throws Exception {
        final ChannelPipeline pipeline = Channels.pipeline();

        final AirPlayServer airPlayServer = AirPlayServer.getIstance(context);

        pipeline.addLast("executionHandler",
                airPlayServer.getChannelExecutionHandler());
        pipeline.addLast("closeOnShutdownHandler",
                new SimpleChannelUpstreamHandler() {
                    @Override
                    public void channelOpen(final ChannelHandlerContext ctx,
                            final ChannelStateEvent e) throws Exception {
                        airPlayServer.getChannelGroup().add(e.getChannel());
                        super.channelOpen(ctx, e);
                    }
                });
        // pipeline.addLast("exceptionCaught", new ExceptionLoggingHandler());
        pipeline.addLast("decoder", new MyHttpDecoder());
        pipeline.addLast("encoder", new MyHttpEncoder());

        pipeline.addLast("aggregator", new HttpChunkAggregator(655360));// buffer
                                                                        // size

        mAirplayServerChannelHandler = new AirplayServerChannelHandler();
        airPlayServer.setAirplayVideoInfoListener(mAirplayServerChannelHandler);
        pipeline.addLast("handler", mAirplayServerChannelHandler);

        return pipeline;
    }

    /**
     * Horizontal space
     */
    public static final byte SP = 32;

    /**
     * Carriage return
     */
    public static final byte CR = 13;

    /**
     * Line feed character
     */
    public static final byte LF = 10;

    private static final char SLASH = '/';
    private static final char QUESTION_MARK = '?';

    public class MyHttpEncoder extends HttpMessageEncoder {

        @Override
        protected void encodeInitialLine(ChannelBuffer buf, HttpMessage message)
                throws Exception {
            if (message instanceof HttpRequest) {
                HttpRequest request = (HttpRequest) message;
                buf.writeBytes(request.getMethod().toString().getBytes("ASCII"));
                buf.writeByte(SP);

                // Add / as absolute path if no is present.
                // See http://tools.ietf.org/html/rfc2616#section-5.1.2
                String uri = request.getUri();
                int start = uri.indexOf("://");
                if (start != -1) {
                    int startIndex = start + 3;
                    // Correctly handle query params.
                    // See https://github.com/netty/netty/issues/2732
                    int index = uri.indexOf(QUESTION_MARK, startIndex);
                    if (index == -1) {
                        if (uri.lastIndexOf(SLASH) <= startIndex) {
                            uri += SLASH;
                        }
                    } else {
                        if (uri.lastIndexOf(SLASH, index) <= startIndex) {
                            int len = uri.length();
                            StringBuilder sb = new StringBuilder(len + 1);
                            sb.append(uri, 0, index);
                            sb.append(SLASH);
                            sb.append(uri, index, len);
                            uri = sb.toString();
                        }
                    }
                }

                buf.writeBytes(uri.getBytes("UTF-8"));
                buf.writeByte(SP);
                buf.writeBytes(request.getProtocolVersion().toString()
                        .getBytes("ASCII"));
                buf.writeByte(CR);
                buf.writeByte(LF);
            } else {
                HttpResponse response = (HttpResponse) message;
                encodeAscii(response.getProtocolVersion().toString(), buf);
                buf.writeByte(SP);
                encodeAscii(String.valueOf(response.getStatus().getCode()), buf);
                buf.writeByte(SP);
                encodeAscii(
                        String.valueOf(response.getStatus().getReasonPhrase()),
                        buf);
                buf.writeByte(CR);
                buf.writeByte(LF);
            }
        }

        protected void encodeAscii(String s, ChannelBuffer buf) {
            for (int i = 0; i < s.length(); i++) {
                buf.writeByte(c2b(s.charAt(i)));
            }
        }

        private byte c2b(char c) {
            if (c > 255) {
                return '?';
            }
            return (byte) c;
        }
    }

    public class MyHttpDecoder extends HttpMessageDecoder {
        boolean mIsReqeust = true;

        @Override
        protected HttpMessage createMessage(String[] initialLine)
                throws Exception {
            // TODO Auto-generated method stub

            Log.e("AirplayPipelineFactory", "initialLine: 0[" + initialLine[0]
                    + "]1[" + initialLine[1] + "]2[" + initialLine[2] + "]");
            if (initialLine[0].startsWith("HTTP")) {
                mIsReqeust = false;
            } else {
                mIsReqeust = true;
            }

            if (mIsReqeust) {
                return new DefaultHttpRequest(
                        HttpVersion.valueOf(initialLine[2]),
                        HttpMethod.valueOf(initialLine[0]), initialLine[1]);
            } else {
                return new DefaultHttpResponse(
                        HttpVersion.valueOf(initialLine[0]),
                        new HttpResponseStatus(Integer.valueOf(initialLine[1]),
                                initialLine[2]));
            }
        }

        @Override
        protected boolean isDecodingRequest() {
            // TODO Auto-generated method stub
            return mIsReqeust;
        }

    }

}
