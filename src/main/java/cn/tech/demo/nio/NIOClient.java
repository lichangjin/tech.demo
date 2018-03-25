package cn.tech.demo.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class NIOClient extends NIOSocketChannel {

	public void client() {
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		SocketChannel socketChannel = null;
		try {
			socketChannel = SocketChannel.open();
			socketChannel.configureBlocking(false);
			socketChannel.connect(new InetSocketAddress("127.0.0.1", 801));

			if (socketChannel.finishConnect()) {
				int i = 0;
				while (true) {
					Util.sleep(1000);
					String info = "I'm " + i++ + "-th information from client";
					buffer.clear();
					buffer.putInt(info.getBytes().length + 5);
					buffer.putInt(0x001);
					buffer.put(info.getBytes());
					buffer.put((byte) '\n');
					buffer.flip();
					while (buffer.hasRemaining()) {
						socketChannel.write(buffer);
						System.out.println(info);
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			close(socketChannel);
		}
	}

	public static void main(String[] args) {
		new NIOClient().client();
	}
}