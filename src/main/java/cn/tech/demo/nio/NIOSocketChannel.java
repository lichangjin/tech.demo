package cn.tech.demo.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class NIOSocketChannel {

	private static final byte[] syncroot = new byte[0];
	protected Selector selector = null;

	public void send(SocketChannel socket, int cmd, byte[] data) {
		if (socket == null || data == null)
			return;
		send_internal(socket, cmd, data);
	}

	protected boolean send_internal(SocketChannel socket, int cmd, byte[] data) {
		if (data == null) {
			return true;
		}
		try {
			if ((socket == null) || (!socket.isOpen()) || (!socket.isConnected())) {
				return false;
			}
			ByteBuffer buffer = ByteBuffer.allocate(0);
			buffer.putInt(data.length + 5);
			buffer.putInt(cmd);
			buffer.put(data);
			buffer.put((byte) '\n');
			socket.write(buffer);
			return true;
		} catch (Exception ex) {
			close(socket);
		}
		return false;
	}

	protected void close(SocketChannel socket) {
		if (socket == null)
			return;
		try {
			SelectionKey selectionKey = socket.keyFor(this.selector);
			synchronized (syncroot) {
				close(selectionKey);
				socket.close();
			}
		} catch (IOException e) {

		}
	}

	protected void close(SelectionKey key) {
		try {
			if (key == null)
				return;
			key.cancel();
			key.channel().close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	protected static void close(ServerSocketChannel ssc) {
		try {
			if (ssc != null) {
				ssc.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	protected void close(Selector selector) {
		try {
			if (selector != null) {
				selector.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
