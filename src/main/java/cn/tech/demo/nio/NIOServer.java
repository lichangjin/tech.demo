package cn.tech.demo.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class NIOServer extends NIOSocketChannel {

	private static final int BUF_SIZE = 1024;
	private static final int PORT = 801;
	private static final int TIMEOUT = 3000;

	private Map<Integer, SocketChannel> socketChannels = new HashMap<Integer, SocketChannel>();
	public Map<Integer, SelectionKey> selectionKeys = new HashMap<Integer, SelectionKey>();

	private void selector() {
		Selector selector = null;
		ServerSocketChannel ssc = null;
		try {
			selector = Selector.open();
			ssc = ServerSocketChannel.open();
			ssc.socket().bind(new InetSocketAddress(PORT));
			ssc.configureBlocking(false);
			ssc.register(selector, SelectionKey.OP_ACCEPT);

			while (true) {
				if (selector.select(TIMEOUT) == 0) {
					Util.sleep(1000);
					System.out.println("==");
					continue;
				}
				Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
				while (iter.hasNext()) {
					SelectionKey key = iter.next();
					try {
						if (key.isAcceptable()) {
							accept(key);
						} else if (key.isReadable()) {
							read(key);
						} else if (key.isWritable()) {
							write(key);
						} else if (key.isConnectable()) {
							System.out.println("isConnectable=true");
						}
					} catch (IOException e) {
						close(key);
					} finally {
						iter.remove();
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			close(selector);
			close(ssc);
		}
	}

	private void accept(SelectionKey key) throws IOException {
		ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
		SocketChannel sc = ssc.accept();
		if (sc != null) {
			System.out.println(sc.getRemoteAddress());
			sc.configureBlocking(false);
			sc.register(key.selector(), SelectionKey.OP_READ, ByteBuffer.allocateDirect(BUF_SIZE));
			if (!socketChannels.containsKey(sc.hashCode()))
				socketChannels.put(sc.hashCode(), sc);
		}
	}

	private void read(SelectionKey key) throws IOException {
		SocketChannel sc = (SocketChannel) key.channel();
		ByteBuffer buf = (ByteBuffer) key.attachment();
		while (sc.read(buf) > 0) {
			buf.flip();
			int position = buf.position();
			if (buf.remaining() >= 4) {
				int len = buf.getInt();
				if (len < 1 || len > BUF_SIZE) {
					System.out.println("非法协议");
				}
				if (buf.remaining() >= len) {
					byte[] array = new byte[len];
					buf.get(array, 0, len);
					if (array[len - 1] != 10) {
						System.out.println("非法协议");
					}
					int cmd = Util.byteArrayToInt(Arrays.copyOfRange(array, 0, 4));
					String content = new String(Arrays.copyOfRange(array, 4, len - 1));

					System.out.println(Integer.toHexString(cmd) + " " + content);
				}
			}
			buf.position(position);
		}
		buf.clear();

		while (buf.hasRemaining()) {
			sc.write(buf);
		}
		buf.compact();
	}

	protected class ReceiveHandler implements Runnable {

		private SocketChannel socketChannel;

		public ReceiveHandler(SocketChannel socketChannel) {
			this.socketChannel = socketChannel;
		}

		@Override
		public void run() {
			if (socketChannel == null)
				return;
			try {
				receive_internal(socketChannel);
			} catch (IOException e) {
				
			}
		}

	}
	
	public void receive_internal(SocketChannel socket) throws IOException {
		
	}

	private void write(SelectionKey key) throws IOException {
		ByteBuffer buf = (ByteBuffer) key.attachment();
		buf.flip();
		SocketChannel sc = (SocketChannel) key.channel();
		while (buf.hasRemaining()) {
			sc.write(buf);
		}
		buf.compact();
	}

	public static void main(String[] args) {
		new NIOServer().selector();
	}

}