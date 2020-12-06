package compression.lz77.streams;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;


public final class BitInputStream {

	private InputStream inputStream;

	private int nextBits;

	private int numberOfBitsRemaining;

	private boolean isEndOfStream;

	public BitInputStream(InputStream in) {
		if (in == null) {
			throw new NullPointerException("Null pointer");
		}
		inputStream = in;
		numberOfBitsRemaining = 0;
		isEndOfStream = false;
	}

	public int read() throws IOException {
		if (isEndOfStream)
			return -1;
		if (numberOfBitsRemaining == 0) {
			nextBits = inputStream.read();
			if (nextBits == -1) {
				isEndOfStream = true;
				return -1;
			}
			numberOfBitsRemaining = 8;
		}
		numberOfBitsRemaining--;
		return (nextBits >>> numberOfBitsRemaining) & 1;
	}

	public int read(int n) throws IOException {
		int output = 0;
		for (int i = 0; i < n; i++) {
			int val = readNoEof();
			output = output << 1 | val;
		}
		return output;
	}

	public byte readByte() throws IOException {
		return (byte) read(8);
	}

	public int readNoEof() throws IOException {
		int result = read();
		if (result != -1)
			return result;
		else
			throw new EOFException("End of stream reached");
	}

	public void close() throws IOException {
		inputStream.close();
	}

}