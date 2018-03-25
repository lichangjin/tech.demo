package cn.tech.demo.nio;

import java.io.UnsupportedEncodingException;

public class Util {
	
	public static byte[] toByte(Object data) {
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
		} catch (UnsupportedEncodingException e) {
			// TODO 处理异常
			e.printStackTrace();
		}
		return bs;
	}
	
	public static void sleep(int millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			// ignole
		}
	}
	
	public static int byteArrayToInt(byte[] bb) {
		return bb[3] & 0xFF | (bb[2] & 0xFF) << 8 | (bb[1] & 0xFF) << 16 | (bb[1] & 0xFF) << 24;
	}

	public static byte[] intToByteArray(int a) {
		return new byte[] { (byte) ((a >> 24) & 0xFF), (byte) ((a >> 16) & 0xFF), (byte) ((a >> 8) & 0xFF), (byte) (a & 0xFF) };
	}

	public static int toInt(byte[] bb) {
		int iOutcome = 0;
		byte bLoop;
		for (int i = 0; i < bb.length; i++) {
			bLoop = bb[i];
			iOutcome += (bLoop & 0xff) << (8 * i);
		}
		return iOutcome;
	}
}
