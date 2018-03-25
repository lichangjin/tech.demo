package cn.tech.demo.tcp;

import java.io.IOException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

public abstract class TCPSocket {

	// public Logger logger = Logger.getLogger(TCPSocket.class);
	protected AbstractSelectableChannel schannel;
	protected static int checkIntervalTime = 30000;
	protected int port = 0;
	protected Selector selector = null;
	protected boolean running = false;
	protected ThreadPoolExecutor threadPool = null;
	protected static final byte[] syncroot = new byte[0];
	protected boolean isServer = false;
	protected long lastHeartbeatRunningTime = 0L;
	protected Thread heartbeatThread = null;
	protected long lastchecktime = System.currentTimeMillis();

	public Map<Object, TechSocketChannel> serverSocketChannels = new ConcurrentHashMap<Object, TechSocketChannel>();
	public Map<Integer, SelectionKey> selectionKeys = new HashMap<Integer, SelectionKey>();

	public TechSocketChannel getTechSocketChannel(int key) {
		return this.serverSocketChannels.get(key);
	}

	public TechSocketChannel getTechSocketChannel(SocketChannel socket) {
		if (socket == null)
			return null;
		return this.serverSocketChannels.get(socket.hashCode());
	}

	public void putTechSocketChannel(SocketChannel socket) {
		putTechSocketChannel(new TechSocketChannel(socket));
	}

	public void putTechSocketChannel(TechSocketChannel techSocketChannel) {
		if (techSocketChannel == null || techSocketChannel.getSocket() != null)
			return;
		int key = techSocketChannel.getSocket().hashCode();
		TechSocketChannel item = getTechSocketChannel(key);
		if (item == null) {
			synchronized (syncroot) {
				item = getTechSocketChannel(key);
				if (item == null)
					this.serverSocketChannels.put(key, techSocketChannel);
			}
		}
	}

	public void close(SelectionKey selectionKey) {
		if (selectionKey != null) {
			synchronized (syncroot) {
				int hashCode = selectionKey.hashCode();
				int socketHashCode = selectionKey.channel().hashCode();
				try {
					selectionKey.cancel();
					selectionKey.channel().close();
				} catch (IOException ex) {

				} finally {
					selectionKeys.remove(hashCode);
					serverSocketChannels.remove(socketHashCode);
				}
			}
		}
	}

	public void close(SocketChannel socket) {
		try {
			if (socket != null) {
				SelectionKey selectionKey = socket.keyFor(selector);
				synchronized (this.syncroot) {
					close(selectionKey);
					if (socket != null) {
						if (!isServer) {
							stop();
						} else {
							removeTechSocketChannel(socket);
							socket.close();
						}
					}
				}
			}
		} catch (IOException e) {

		}
	}

	private void removeTechSocketChannel(SocketChannel socket) {
		// TODO Auto-generated method stub

	}

	private void stop() {
		try {
			if (this.isServer) {
				// this.logger.info("stop port(" + this.port + ") listen.");
			} else {
				// this.logger.info("stop port(" + this.port + ") connect.");
			}
			this.running = false;
			if (this.selector != null) {
				this.selector.close();
			}
			if (this.schannel != null) {
				this.schannel.close();
			}
			if (this.threadPool != null) {
				this.threadPool.shutdown();
			}
			this.selector = null;
			this.schannel = null;
			this.threadPool = null;
			this.serverSocketChannels.clear();
		} catch (Exception ex) {
			// this.logger.error(null, ex);
		}
	}

	private void start() {
		if (!this.running) {
			synchronized (this.syncroot) {
				if (!this.running) {
					this.running = true;
					this.lastchecktime = System.currentTimeMillis();
					start_internal();
				}
			}
		}
	}

	private void start_internal() {
		if (this.threadPool == null) {
			// this.logger.info("this.threadPool=null!");
			return;
		}
		if ((!this.isServer) && ((this.heartbeatThread == null) || (System.currentTimeMillis() - this.lastHeartbeatRunningTime > 2 * checkIntervalTime))) {
			heartbeatCheck();
		}
		this.threadPool.execute(new Runnable() {
			public void run() {
				try {
					while (running) {
						if (selector.select(1000L) <= 0) {
							Thread.sleep(20L);
						} else {
							Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
							while (iterator.hasNext()) {
								SelectionKey selectionKey = (SelectionKey) iterator.next();
								iterator.remove();
								try {
									if (selectionKey.isReadable()) {
										if (selectionKeys.get(selectionKey.hashCode()) != selectionKey) {
											selectionKeys.put(selectionKey.hashCode(), selectionKey);
											threadPool.execute(new ReceiveHandler((SocketChannel) selectionKey.channel()));
										}
									} else if (selectionKey.isWritable()) {
										SocketChannel sc = (SocketChannel) selectionKey.channel();
										selectionKey.interestOps(1);
										TCPSocket.TechSocketChannel item = getTechSocketChannel(sc);
										if ((item != null) && (item.getResponseQueue().size() > 0)) {
											threadPool.execute(new TCPSocket.SendHandler(sc));
										}
									} else if (selectionKey.isAcceptable()) {
										if ((selectionKey.channel() instanceof ServerSocketChannel)) {
											ServerSocketChannel ssc = (ServerSocketChannel) selectionKey.channel();
											SocketChannel sc = ssc.accept();

											TCPSocket.this.putTechSocketChannel(new TCPSocket.TechSocketChannel(sc));
											if (sc != null) {
												sc.configureBlocking(false);
												sc.register(TCPSocket.this.selector, 5);
												TCPSocket.this.conectComplete_internal(sc);
											}
										}
									} else if (selectionKey.isConnectable()) {
										if ((selectionKey.channel() instanceof SocketChannel)) {
											SocketChannel sc = (SocketChannel) selectionKey.channel();
											TCPSocket.this.putTechSocketChannel(new TCPSocket.TechSocketChannel(sc));
											if (sc.isConnectionPending()) {
												sc.finishConnect();
											}
											sc.register(TCPSocket.this.selector, 5);

											TCPSocket.this.conectComplete_internal(sc);
										}
									}
								} catch (SocketException ex) {
									// TCPSocket.this.logger.info("close(" +
									// selectionKey.hashCode() + ").");
									if (TCPSocket.this.isServer) {
										TCPSocket.this.close(selectionKey);
									} else {
										TCPSocket.this.stop();
									}
								} catch (CancelledKeyException ex) {
									// TCPSocket.this.logger.info("close(" +
									// selectionKey.hashCode() + ").");
									if (TCPSocket.this.isServer) {
										TCPSocket.this.close(selectionKey);
									} else {
										TCPSocket.this.stop();
									}
								} catch (Exception ex) {
									// TCPSocket.this.logger.error(null, ex);
								}
							}
						}
					}
				} catch (Exception ex) {
					// TCPSocket.this.logger.error(null, ex);
					TCPSocket.this.stop();
				}
			}
		});
	}

	protected void conectComplete_internal(SocketChannel socket) {
		try {
			conectComplete(socket);
		} catch (Exception ex) {
			// this.logger.error(null, ex);
		}
	}

	public abstract void conectComplete(SocketChannel socket);

	public synchronized boolean isRunning() {
		return running;
	}

	public void receive(SocketChannel socket, Object data) {
		if (data instanceof String) {

		} else if (data instanceof byte[]) {

		} else {

		}
	}

	public void receive_internal(SocketChannel socket) throws IOException {
		TechSocketChannel sc = getTechSocketChannel(socket);
		if (sc == null) {
			return;
		}
		ByteBuffer byteBuffer = sc.getByteBuffer();
		byte[] data = null;
		int len = 0;
		while ((len = socket.read(byteBuffer)) > 0) {
			data = null;
			int pos = 0;
			int lim = len;

			byte[] data_s = new byte[0];
			byte[] data_e = new byte[0];

			for (int i = lim - 1; i >= pos; i--) {
				if (byteBuffer.get(i) == 10) {
					int len_s = i - pos + 1;
					int len_e = lim - i - 1;
					if (len_s > 0) {
						data_s = Arrays.copyOf(data_s, len_s);
						System.arraycopy(byteBuffer.array(), 0, data_s, 0, len_s);
					}
					if (len_e > 0) {
						data_e = Arrays.copyOf(data_e, len_e);
						System.arraycopy(byteBuffer.array(), i + 1, data_e, 0, len_e);
					}
					byteBuffer.clear();
					byteBuffer.rewind();
					if (sc.getLastBytes() != null) {
						data = new byte[0];
						data = Arrays.copyOf(data, sc.getLastBytes().length + data_s.length);
						System.arraycopy(sc.getLastBytes(), 0, data, 0, sc.getLastBytes().length);
						System.arraycopy(data_s, 0, data, sc.getLastBytes().length, len_s);
					} else {
						data = data_s;
					}
					if ((data_e != null) && (data_e.length > 0)) {
						sc.setLastBytes(data_e);
						break;
					}
					sc.setLastBytes(null);
					break;
				}
			}
			if (data != null) {
				this.lastchecktime = System.currentTimeMillis();
				if (data.length > 0) {
					receive(socket, data);
				}
			}
		}
		if (len == -1) {
			if (!this.isServer) {
				stop();
			} else {
				close(socket.keyFor(this.selector));
			}
		}
	}

	public void send(SocketChannel socket, Object data) {
		try {
			if (!isRunning() && !isServer) {
				start();
			}
			if (socket == null) {
				return;
			}
			if (!this.isServer && (heartbeatThread == null || System.currentTimeMillis() - lastHeartbeatRunningTime > 2 * checkIntervalTime)) {
				heartbeatCheck();
			}
			TechSocketChannel channel = getTechSocketChannel(socket);
			send_internal(socket, data);
		} catch (Exception e) {

		}
	}

	public void sendPing() {
		send((SocketChannel) this.schannel, "\n");
	}

	private boolean send_internal(SocketChannel socket, Object data) {
		if (data == null) {
			return true;
		}
		byte[] bs = null;
		try {
			if ((data instanceof byte[])) {
				bs = (byte[]) data;
			} else if ((data instanceof String)) {
				bs = ((String) data).getBytes("utf8");
			} else {
				String str = data.toString();
				bs = str.getBytes("utf8");
			}
			if ((bs == null) || (bs.length == 0)) {
				return true;
			}
		} catch (Exception ex) {
			// this.logger.error(null, ex);
		}
		try {
			if ((socket == null) || (!socket.isOpen()) || (!socket.isConnected())) {
				// this.logger.info("socket == null || !socket.isOpen() || !socket.isConnected()");
				if ((!this.isServer) && ((!socket.isOpen()) || (!socket.isConnected())) && (System.currentTimeMillis() - this.lastchecktime > checkIntervalTime)) {
					stop();
				}
				return false;
			}
			socket.write(ByteBuffer.wrap(bs));
			this.lastchecktime = System.currentTimeMillis();

			return true;
		} catch (ClosedChannelException ex) {
			// this.logger.error(null, ex);
			close(socket);
		} catch (Exception ex) {
			// this.logger.error(null, ex);
			close(socket);
		}
		return false;
	}

	private void heartbeatCheck() {
		if ((this.heartbeatThread == null) || (System.currentTimeMillis() - this.lastHeartbeatRunningTime > 2 * checkIntervalTime)) {
			synchronized (this.syncroot) {
				if ((this.heartbeatThread == null) || (System.currentTimeMillis() - this.lastHeartbeatRunningTime > 2 * checkIntervalTime)) {
					this.lastHeartbeatRunningTime = System.currentTimeMillis();
				} else {
					return;
				}
			}
		} else {
			return;
		}
		if (this.heartbeatThread != null) {
			try {
				this.heartbeatThread.interrupt();
			} catch (Exception ex) {
				// this.logger.error(null, ex);
			} finally {
				this.heartbeatThread = null;
			}
		}
		this.heartbeatThread = new Thread(new Runnable() {
			/* Error */
			public void run() {

			}
		});
		this.heartbeatThread.start();
	}

	protected class ReceiveHandler implements Runnable {

		private SocketChannel socket;

		public ReceiveHandler(SocketChannel socket) {
			this.socket = socket;
		}

		@Override
		public void run() {
			try {
				int key = 0;
				try {
					if (socket == null || selector == null) {
						return;
					}

					key = socket.keyFor(selector).hashCode();

					TechSocketChannel item = getTechSocketChannel(socket);
					if (item == null)
						return;
					synchronized (item.getSyncRoot()) {
						receive_internal(socket);
					}
				} catch (Exception e) {
					if (!isServer) {
						close(socket);
					}
					if (isServer) {
						close(socket.keyFor(selector));
					}
				} finally {
					if (key > 0) {
						selectionKeys.remove(key);
					}
				}
			} catch (Exception e) {

			}
		}
	}

	protected class SendHandler implements Runnable {

		private SocketChannel socket;

		SendHandler(SocketChannel socket) {
			this.socket = socket;
		}

		@Override
		public void run() {
			try {
				if (this.socket != null) {
					run_internal(this.socket);
				} else {
					Object[] keys = TCPSocket.this.serverSocketChannels.keySet().toArray();
					for (Object key : keys) {
						TCPSocket.TechSocketChannel item = (TCPSocket.TechSocketChannel) TCPSocket.this.serverSocketChannels.get(key);
						if (!item.isSendLock() && item.getSocket() != null && item.getSocket().isOpen() && item.getSocket().isConnected() && item.getResponseQueue().size() > 0) {
							run_internal(this.socket);
						}
					}
				}
			} catch (Exception e) {

			}
		}

		private void run_internal(SocketChannel socket) {
			TechSocketChannel item = getTechSocketChannel(socket);
			if (item == null || item.getResponseQueue().isEmpty()) {
				return;
			}
			synchronized (item.getSyncRoot()) {
				try {
					item.setSendLock(true);
					while (!item.getResponseQueue().isEmpty()) {
						send_internal(socket, item.getResponseQueue().poll());
					}
				} finally {
					item.setSendLock(false);
				}
			}
		}
	}

	protected class TechSocketChannel {

		private SocketChannel socket = null;
		private ByteBuffer byteBuffer = null;
		private boolean sendLock = false;
		private byte[] lastBytes = null;
		private Queue<Object> responseQueue = new LinkedBlockingQueue<Object>();
		private byte[] syncroot = new byte[0];

		public TechSocketChannel(SocketChannel socket) {
			this.socket = socket;
		}

		public synchronized ByteBuffer getByteBuffer() {
			if (this.byteBuffer == null) {
				this.byteBuffer = ByteBuffer.allocate(1048576);
			}
			return this.byteBuffer;
		}

		public int getKey() {
			if (this.socket != null) {
				return this.socket.hashCode();
			}
			return 0;
		}

		public byte[] getLastBytes() {
			return this.lastBytes;
		}

		public void setLastBytes(byte[] lastBytes) {
			this.lastBytes = lastBytes;
		}

		public Queue<Object> getResponseQueue() {
			return this.responseQueue;
		}

		public SocketChannel getSocket() {
			return this.socket;
		}

		public byte[] getSyncRoot() {
			return this.syncroot;
		}

		public void setSyncroot(byte[] syncroot) {
			this.syncroot = syncroot;
		}

		public boolean isSendLock() {
			return this.sendLock;
		}

		public void setSendLock(boolean sendLock) {
			this.sendLock = sendLock;
		}

	}

}