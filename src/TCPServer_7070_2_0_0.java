import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.BindException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Enumeration;
import java.util.Set;

import com.sio.model.Packer;
import com.sio.plugin.Terminal;


public class TCPServer_7070_2_0_0 extends Terminal {
	private static final int PORT = 7070;
	private static final File PROP_FILE = new File("./config/tcp.ini");
	private ServerSocketChannel serverchannel;
	private Selector serverSelector;

//	instance
	public static TCPServer_7070_2_0_0 instance;
	@Override
	public void start() {
		instance = this;
		String mac = null;
		if(PROP_FILE.exists()){
			try(FileReader reader = new FileReader(PROP_FILE);
					BufferedReader in = new BufferedReader(reader);){
				mac = in.readLine();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		try {
			serverchannel = ServerSocketChannel.open();
			serverchannel.configureBlocking(false);
			if(mac == null){
				serverchannel.bind(new InetSocketAddress(InetAddress.getLocalHost().getHostAddress(),PORT));
			} else {
				serverchannel.bind(new InetSocketAddress(getInet4AddressByHardwareAddress(mac),PORT));
			}

		} catch(BindException e){
			System.out.println("异常 -> TCP 7070 端口被占用");
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			serverSelector = Selector.open();
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
				if((keys_count = serverSelector.select())>0){
					Set<SelectionKey> keys = serverSelector.selectedKeys();
					for(SelectionKey key : keys){
						ServerSocketChannel sChannel = (ServerSocketChannel) key.channel();
						if(key.isAcceptable()){
							SocketChannel socketChannel = null;
							socketChannel = sChannel.accept();
							socketChannel.configureBlocking(false);
							System.out.println("发生连接-[tcp7070]");
							new Thread(new TCP_ChannelNode(socketChannel)).start();
						} 
						keys.remove(key);
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void stop() {
		disconnect();
	}

	private void disconnect(){
		if(serverchannel != null){
			try {
				serverchannel.socket().close();
				serverchannel.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			System.out.println("切断服务连接-[tcp7070]");
			serverSelector = null;
		}
	}
	
	private InetAddress getInet4AddressByHardwareAddress(String hardware_address){
		Enumeration<NetworkInterface> interfaces = null;
		try {
			interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements()){
				NetworkInterface netInterface = interfaces.nextElement();
				if(netInterface.getHardwareAddress() != null)
				if(Packer.fromBytesTo16radix(netInterface.getHardwareAddress()).equalsIgnoreCase(hardware_address)){
					Enumeration<InetAddress> inets = netInterface.getInetAddresses();
					while(inets.hasMoreElements()) {
						InetAddress addr = inets.nextElement();
						if(addr instanceof Inet4Address){
							return addr;
						}
					}
					return null;
				}
			}
		} catch (SocketException e) {
			e.printStackTrace();
		}
		return null;
	}
}
