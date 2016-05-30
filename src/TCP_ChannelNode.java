import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.Random;
import java.util.Set;

import javax.imageio.ImageIO;

import com.sio.graphics.DefaultImageCaster;
import com.sio.graphics.ImageCaster;
import com.sio.model.AbstractAccessPoint;
import com.sio.model.DefaultUDPA1Pack;
import com.sio.model.Packer;
import com.sio.model.Tag;
import com.sio.model.WirelessTag;


public class TCP_ChannelNode implements Runnable{
	private static final int TCP_CONNECTION_TIME_OUT = 2;
	private static final int HTTP_CONNECTION_TIME_OUT = 2;
	private static final String COMMAND_HEAD_SEND = "ESLSend";
	private static final String SUCCESS = "Cmd=set(1)";					//succeed
	private static final String FAILD = "Cmd=set(2)[Error1]";			//image or path(url) problems
	private static final String FAILD_2 = "Cmd=set(3)[Error2]";			//mac or tag problem
	private static final String FAILD_3 = "Cmd=set(4)[Error 403]";		//download or browse exception
	private static final String FAILD_4 = "Cmd=set(5)[Error4]";			//not online
	private static final int DEFAULT_BUFF_SIZE = 512;
	
	private SocketChannel socketChannel;
	private Selector selector;
	
	private ByteBuffer buffer = ByteBuffer.allocate(DEFAULT_BUFF_SIZE);
	
	public TCP_ChannelNode(SocketChannel channel) {
		this.socketChannel = channel;
		try {
			selector = Selector.open();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		try {
			registe();
		} catch (ClosedChannelException e) {
			e.printStackTrace();
		}
		
		if(socketChannel == null || !socketChannel.isConnected()){
			return;
		}
		
		try {
			int eve = selector.select(TCP_CONNECTION_TIME_OUT * 1000);
			if(eve > 0){
				Collection<SelectionKey> keys = selector.selectedKeys();
				for(SelectionKey key : keys){
					SocketChannel channel = (SocketChannel) key.channel();
					if(key.isReadable() && key.isValid()){
						buffer.clear();
						int size = channel.read(buffer);
						if(size < 1){
							destroy();
							return;
						}
						byte[] data = new byte[size];
						buffer.flip();
						buffer.get(data);
						String[] args = new String(data).split(" ");
						if(args !=null && args.length >= 3 && COMMAND_HEAD_SEND.equalsIgnoreCase(args[0])){
							solveCommand(args[1], downloadImage(args[2]));
							destroy();
							return;
						} else {
							System.out.println("wrong format!!");
						}
					}
					keys.remove(key);
				}
			} else {
				destroy();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	private void registe() throws ClosedChannelException{
		socketChannel.register(selector, SelectionKey.OP_READ);
		
	}
	
	private BufferedImage downloadImage(String uri_str){
		URI uri = null;
		URL url = null;
		BufferedImage dst = null;
		try {
			uri = new URI(uri_str);
		} catch (URISyntaxException e2) {
			e2.printStackTrace();
		}
		try {
			url = uri.toURL();
		} catch (MalformedURLException e1) {
			e1.printStackTrace();
			sendwarn(FAILD);
		}
		try {
			if(url != null){
				HttpURLConnection conn = (HttpURLConnection)url.openConnection();
				conn.setRequestMethod("GET");
				conn.setConnectTimeout(HTTP_CONNECTION_TIME_OUT * 1000);
				try (InputStream in = conn.getInputStream();){
					dst =ImageIO.read(in);
					in.close();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			sendwarn(FAILD_3);
		}
		return dst;
	}
	
	private void sendwarn(String warn){
		try {
			socketChannel.write(ByteBuffer.wrap(warn.getBytes()));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void solveCommand(String mac, BufferedImage image) throws IOException{
		if(image == null){
			sendwarn(FAILD);
			return;
		}
		if(mac == null || mac.length() != 12){
			sendwarn(FAILD_2);
			return;
		}
		Set<AbstractAccessPoint> aps = TCPServer_7070_2_0_0.instance.getDevices();
		for(AbstractAccessPoint ap : aps){
			if(ap.contains(mac)){
				WirelessTag wTag = ap.getTag(mac);
				Tag tag = wTag.getTag();
					if(tag != null){
						ImageCaster caster = new DefaultImageCaster();
						byte[] data = caster.cast(image, tag.model());
						Packer packer = new DefaultUDPA1Pack();
						packer.setHead(mac, new Random().nextLong(), null);
						packer.setData(DefaultUDPA1Pack.ORDER_SEND_BW, data);
						wTag.write(packer.getPack());
						String succeedTxt = SUCCESS+"["+mac+"]";
						socketChannel.write(ByteBuffer.wrap(succeedTxt.getBytes()));
						return;
					}
				break;
			}
		}
		sendwarn(FAILD_4);
	}
	
	private void destroy() throws IOException{
		if(socketChannel != null){
			socketChannel.socket().close();
			socketChannel.close();
		}
		if(selector != null){
			selector.close();
			selector = null;
		}
		System.out.println("«–∂œ¡¨Ω”-[tcp7070]");
	}
}
