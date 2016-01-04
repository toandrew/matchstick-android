/*
 * This file is part of AirReceiver.
 *
 * AirReceiver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * AirReceiver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with AirReceiver.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.phlo.airreceiver;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.logging.Level;

import nz.co.iswe.android.airplay.AirPlayServer;
import nz.co.iswe.android.airplay.music.MusicActivity;
import nz.co.iswe.android.airplay.network.airplay.AirplayMediaController;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.WriteCompletionEvent;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.example.airplay.util.Constants;
import com.example.airplay.util.Globals;
import com.example.airplay.util.LogManager;

/**
 * Logs RTSP requests and responses.
 */
public class RtspLoggingHandler extends SimpleChannelHandler
{
//	private static final Logger s_logger = Logger.getLogger(RtspLoggingHandler.class.getName());
	private Context context ;
	public RtspLoggingHandler(Context context) {
		// TODO Auto-generated constructor stub
		this.context = context;
	}

	@Override
	public void channelConnected(final ChannelHandlerContext ctx, final ChannelStateEvent e)
		throws Exception
	{	
		LogManager.i("====================channelConnected ");
		this.e = e ;
		registerReceiver();
		LogManager.i("Client " + e.getChannel().getRemoteAddress() + " connected on " + e.getChannel().getLocalAddress());
		
        // start music activity?
      Intent intent = new Intent();
      intent.setClass(Globals.getContext(), MusicActivity.class);
      //DlnaMediaModelFactory.pushMediaModelToIntent(intent, null);
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
              | Intent.FLAG_ACTIVITY_SINGLE_TOP);
      Globals.getContext().startActivity(intent);
	}
	
	
	private void writeToStop() {
		StringBuffer buffer = new StringBuffer("GET /ctrl-int/1/playpause HTTP/1.1") ;
		buffer.append("\n") ;
		buffer.append("Host: starlight.local.") ;
		buffer.append("\n") ;
		buffer.append(remote) ;
		
		ChannelBuffer channelbuffer = ChannelBuffers.buffer(buffer.length() * 2);
		channelbuffer.writeBytes(buffer.toString().getBytes());
		e.getChannel().write(channelbuffer);
		
		Log.e("hefeng","stop command : "+buffer.toString()) ;
	}
	ChannelStateEvent e ;
	private String remote  ;
	@Override
	public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent evt)
		throws Exception
	{	//[id: 0x42320e48, /192.168.20.58:60810 => /192.168.20.128:5000] RECEIVED: DefaultHttpRequest(chunked: false) OPTIONS * RTSP/1.0 CSeq: 0 X-Apple-Device-ID: 0xf0c1f145b120 Apple-Challenge: aegvjDkBxWjPQlDBfGZnXA== DACP-ID: D3DADC2D4FD6B073 Active-Remote: 850148892 User-Agent: AirPlay/190.9
		final HttpRequest req = (HttpRequest)evt.getMessage();
		LogManager.i("====================messageReceived:"+req.toString());
		final Level level = Level.FINE;
//		if (Constants.DEBUG) {
			final String content = req.getContent().toString(Charset.defaultCharset());

			final StringBuilder s = new StringBuilder();
			s.append(">");
			s.append(req.getMethod());
			s.append(" ");
			s.append(req.getUri());
			s.append("\n");
			OUT:for(final Map.Entry<String, String> header: req.getHeaders()) {
				s.append("  ");
				s.append(header.getKey());
				s.append(": ");
				s.append(header.getValue());
				s.append("\n");
				if("Active-Remote".equalsIgnoreCase(header.getKey())){
					remote = "Active-Remote: "+header.getValue() ;
					LogManager.e("remote============"+remote);
					break OUT ;
				}
				
			}
			s.append(content);
//			LogManager.i("messageReceived:"+s.toString());
//		}

		super.messageReceived(ctx, evt);
	}

	@Override
	public void writeRequested(final ChannelHandlerContext ctx, final MessageEvent evt)
		throws Exception
	{
		final HttpResponse resp = (HttpResponse)evt.getMessage();
		LogManager.i("====================writeRequested : "+resp.toString());

		final Level level = Level.FINE;
		if (Constants.DEBUG) {
			final StringBuilder s = new StringBuilder();
			s.append("<");
			s.append(resp.getStatus().getCode());
			s.append(" ");
			s.append(resp.getStatus().getReasonPhrase());
			s.append("\n");
			for(final Map.Entry<String, String> header: resp.getHeaders()) {
				s.append("  ");
				s.append(header.getKey());
				s.append(": ");
				s.append(header.getValue());
				s.append("\n");
			}
			LogManager.i("writeRequested: "+ s.toString());
		}

		super.writeRequested(ctx, evt);
	}
	
	@Override
	public void channelDisconnected(ChannelHandlerContext ctx,
			ChannelStateEvent e) throws Exception {
		// TODO Auto-generated method stub
		super.channelDisconnected(ctx, e);
		LogManager.i("====================channelDisconnected");
		unregisterReceiver();
		LogManager.i("Client " + e.getChannel().getRemoteAddress() + " disconnected on " + e.getChannel().getLocalAddress());
		
        // stop music activity?
        final AirPlayServer airPlayServer = AirPlayServer.getIstance(Globals
                .getContext());
        AirplayMediaController controller = airPlayServer
                .getAirplayMusicController();
        if (controller != null) {
            controller.onStopCommand();
        }
	}

	@Override
	public void writeComplete(ChannelHandlerContext ctx, WriteCompletionEvent e)
			throws Exception {
		// TODO Auto-generated method stub
		super.writeComplete(ctx, e);
		LogManager.i("====================writeComplete");
	}

	public void registerReceiver(){
		IntentFilter filter = new IntentFilter() ;
		filter.addAction(Constants.IKEY_MEDIA_PLAY_OR_PAUSE) ;
		context.registerReceiver(receiver, filter);
	}
	
	public void unregisterReceiver(){
		context.unregisterReceiver(receiver);
	}
	
	BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			// TODO Auto-generated method stub
			
			if(Constants.IKEY_MEDIA_PLAY_OR_PAUSE.equals(intent.getAction())){
				writeToStop();
			}
		}
	};
}
