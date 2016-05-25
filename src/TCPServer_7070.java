import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
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
import com.sio.plugin.Terminal;


public class TCPServer_7070 extends Terminal {
	private static final int PORT = 7070;
	private static final File prop_file = new File("./tcp.ini");
	private static final int BUFFER_DEFAULT_SIZE = 1024;
	private static final int HTTP_CONNECTION_TIME_OUT = 2;
	private static final String COMMAND_HEAD_SEND = "ESLSend";
	private static final String SUCCESS = "Cmd=set(1)";					//succeed
	private static final String FAILD = "Cmd=set(2)[Error1]";			//image or path(url) problems
	private static final String FAILD_2 = "Cmd=set(3)[Error2]";			//mac or tag problem
	private static final String FAILD_3 = "Cmd=set(4)[Error 403]";		//download or browse exception
	private static final String FAILD_4 = "Cmd=set(5)[Error4]";			//not online
	private ServerSocketChannel serverchannel;
	private SocketChannel socketChannel;
	private Selector serverSelector;
	private Selector socketSelector;
	
//	buffer
	private ByteBuffer buffer = ByteBuffer.allocate(BUFFER_DEFAULT_SIZE);
	
	@Override
	public void start() {
		if(!prop_file.exists()){
			try {
				prop_file.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
			try(FileWriter writer = new FileWriter(prop_file)){
				try {
					writer.write(InetAddress.getLocalHost().getHostAddress());
				} catch (UnknownHostException e) {
					e.printStackTrace();
				}
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
		String ip = null;
		try(FileReader reader = new FileReader(prop_file);
				BufferedReader in = new BufferedReader(reader);){
			ip = in.readLine();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			serverchannel = ServerSocketChannel.open();
			serverchannel.configureBlocking(false);
			if(ip == null){
				serverchannel.bind(new InetSocketAddress(InetAddress.getLocalHost().getHostAddress(),PORT));
			} else {
				serverchannel.bind(new InetSocketAddress(ip,PORT));
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		try {
			serverSelector = Selector.open();
			socketSelector = Selector.open();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		try {
			serverchannel.register(serverSelector, SelectionKey.OP_ACCEPT);
		} catch (ClosedChannelException e) {
			e.printStackTrace();
		}

	}

	@Override
	public synchronized void onEvent() {
//		server selector
		int keys_count = 0;
		{
			try {
				if((keys_count = serverSelector.selectNow())>0){
					Set<SelectionKey> keys = serverSelector.selectedKeys();
					for(SelectionKey key : keys){
						ServerSocketChannel sChannel = (ServerSocketChannel) key.channel();
						if(key.isAcceptable()){
							socketChannel = sChannel.accept();
							socketChannel.configureBlocking(false);
							System.out.println("连接进来1个终端");
							socketChannel.register(socketSelector, SelectionKey.OP_READ);
						} 
						keys.remove(key);
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
//		socket selector
		{
			if(socketChannel == null || !socketChannel.isConnected()){
				return;
			}
			try {
				if((keys_count = socketSelector.select(2000))>0){
					Set<SelectionKey> keys = socketSelector.selectedKeys();
					for(SelectionKey key : keys){
						SocketChannel channel = (SocketChannel) key.channel();
						if (key.isReadable()){
							buffer.clear();
							int size = channel.read(buffer);
							if(size < 1){
								disconnect();
								return;
							}
							byte[] data = new byte[size];
							buffer.flip();
							buffer.get(data);
							String[] args = new String(data).split(" ");
							if(args !=null && args.length >= 3 && COMMAND_HEAD_SEND.equalsIgnoreCase(args[0])){
								solveCommand(args[1], downloadImage(args[2]));
							} else {
								System.out.println("wrong format!!");
							}
						}
						keys.remove(key);
					}
				} else {
					disconnect();
				}
			} catch (IOException e) {
				e.printStackTrace();
				log(e.getMessage());
			}
			
		}

	}

	@Override
	public void stop() {
		disconnect();
	}

	private void disconnect(){
		if(socketChannel != null){
			try {
				socketChannel.socket().close();
				socketChannel.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			socketChannel = null;
		}
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
	
	private void solveCommand(String mac, BufferedImage image) throws IOException{
		if(image == null){
			sendwarn(FAILD);
		}
		if(mac == null || mac.length() != 12){
			sendwarn(FAILD_2);
		}
		Set<AbstractAccessPoint> aps = getDevices();
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
	
	private void sendwarn(String warn){
		try {
			socketChannel.write(ByteBuffer.wrap(warn.getBytes()));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
