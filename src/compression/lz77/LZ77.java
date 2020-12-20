package compression.lz77;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

import compression.lz77.streams.BitInputStream;
import compression.lz77.streams.BitOutputStream;

//FIXME - Remove all printStackTrace with proper System.out.
public class LZ77 {

	public static final int MAX_WINDOW_SIZE = (1 << 12) - 1;

	public static final int LOOK_AHEAD_BUFFER_SIZE = (1 << 4) - 1;

	private int windowSize = MAX_WINDOW_SIZE;

	public LZ77(int windowSize) {
		this.windowSize = Math.min(windowSize, MAX_WINDOW_SIZE);
	}


	private void compress(String inputFileName, String outputFileName) throws IOException {
		BitOutputStream out = null;
		try {
			byte[] data = Files.readAllBytes(Paths.get(inputFileName));
			out = new BitOutputStream(new BufferedOutputStream(new FileOutputStream(outputFileName)));
			for (int i = 0; i < data.length;) {
				Match match = findMatchInSlidingWindow(data, i);
				if (match != null) {
					out.write(Boolean.TRUE);
					out.write((byte) (match.getDistance() >> 4));
					out.write((byte) (((match.getDistance() & 0x0F) << 4) | match.getLength()));
					//System.out.println("<1," + match.getDistance() + ", " + match.getLength() + ">");
					i = i + match.getLength();
				} else {
					out.write(Boolean.FALSE);
					out.write(data[i]);
					i = i + 1;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			out.close();
		}
	}

	private void decompress(String inputFileName, String outputFileName) throws IOException {
		BitInputStream inputFileStream = null;
		FileChannel outputChannel = null;
		RandomAccessFile outputFileStream = null;
		try {
			inputFileStream = new BitInputStream(new BufferedInputStream(new FileInputStream(inputFileName)));
			outputFileStream = new RandomAccessFile(outputFileName, "rw");
			outputChannel = outputFileStream.getChannel();
			ByteBuffer buffer = ByteBuffer.allocate(1);
			try {
				while (true) {
					int flag = inputFileStream.read();
					if (flag == 0) {
						buffer.clear();
						buffer.put(inputFileStream.readByte());
						buffer.flip();
						outputChannel.write(buffer, outputChannel.size());
						outputChannel.position(outputChannel.size());
					} else {
						int byte1 = inputFileStream.read(8);
						int byte2 = inputFileStream.read(8);
						int distance = (byte1 << 4) | (byte2 >> 4);
						int length = (byte2 & 0x0f);
						for (int i = 0; i < length; i++) {
							buffer.clear();
							outputChannel.read(buffer, outputChannel.position() - distance);
							buffer.flip();
							outputChannel.write(buffer, outputChannel.size());
							outputChannel.position(outputChannel.size());
						}
					}
				}
			} catch (EOFException e) {
				// ignore. means we reached the end of the file. and we are done.
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			outputFileStream.close();
			outputChannel.close();
			inputFileStream.close();
		}
	}

	private Match findMatchInSlidingWindow(byte[] data, int currentIndex) {
		Match match = new Match();
		int end = Math.min(currentIndex + LOOK_AHEAD_BUFFER_SIZE, data.length + 1);
		for (int j = currentIndex + 2; j < end; j++) {
			int startIndex = Math.max(0, currentIndex - windowSize);
			byte[] bytesToMatch = Arrays.copyOfRange(data, currentIndex, j);
			for (int i = startIndex; i < currentIndex; i++) {
				int repeat = bytesToMatch.length / (currentIndex - i);
				int remaining = bytesToMatch.length % (currentIndex - i);

				byte[] tempArray = new byte[(currentIndex - i) * repeat + (i + remaining - i)];
				int m = 0;
				for (; m < repeat; m++) {
					int destPos = m * (currentIndex - i);
					System.arraycopy(data, i, tempArray, destPos, currentIndex - i);
				}
				int destPos = m * (currentIndex - i);
				System.arraycopy(data, i, tempArray, destPos, remaining);
				if (Arrays.equals(tempArray, bytesToMatch) && bytesToMatch.length > match.getLength()) {
					match.setLength(bytesToMatch.length);
					match.setDistance(currentIndex - i);
				}
			}
		}
		if (match.getLength() > 0 && match.getDistance() > 0)
			return match;
		return null;
	}


	public static void main(String[] args) throws IOException {

		int windowSize = 100;

		if (args.length < 1) {
			System.out.println("Kich thuoc cua so hien tai :" + windowSize+". Kich thuoc cang lon thoi gian cang lau");
			System.out.println("File nen duoc luu duoi dang inputfilename-compressed.extension");
			System.out.println("File giai nen duoc luu duoi dang inputfilename-decompressed.extension");
			return;
		}

		if (args.length > 1) {
			try {
				windowSize = Integer.valueOf(args[1]);
			} catch (NumberFormatException e) {
				System.out.println("Dien kich thuoc cua so khong vuot qua 4095");
				return;
			}
		}
		String inputFileName = args[0];
		
		if(!Files.exists(Paths.get(inputFileName))){
			System.out.println("File khong ton tai");
		}
		
		StringBuilder compressedFileNameBuilder = new StringBuilder();
		String compressedFileName = new String();
		String decompressedFileName = new String();
		int extension = inputFileName.lastIndexOf(".");
		if (extension > -1) {
			compressedFileNameBuilder.append(inputFileName.substring(0, extension));
			compressedFileNameBuilder.append("-compressed");
			compressedFileNameBuilder.append(inputFileName.substring(extension));
		} else {
			compressedFileNameBuilder.append(inputFileName);
			compressedFileNameBuilder.append("-compressed");
		}
		compressedFileName = compressedFileNameBuilder.toString();
		decompressedFileName = compressedFileName.toString().replace("-compressed", "-decompressed");

		if(Files.exists(Paths.get(compressedFileName))){
			Files.delete(Paths.get(compressedFileName));
		}
		if(Files.exists(Paths.get(decompressedFileName))){
			Files.delete(Paths.get(decompressedFileName));
		}
		
		LZ77 lz77 = new LZ77(windowSize);
		System.out.println("Bat dau nen...");
		
		long startTime = System.currentTimeMillis();
		lz77.compress(inputFileName, compressedFileName);
		long endTime = System.currentTimeMillis();
		System.out.println("Qua trinh nen dien ra trong: " + (endTime - startTime) + " ms");
		
		startTime = System.currentTimeMillis();
		System.out.println("\nBat dau giai nen...");
		lz77.decompress(compressedFileName, decompressedFileName);
		endTime = System.currentTimeMillis();
		System.out.println("Qua trinh giai nen dien ra trong: " + (endTime - startTime) + " ms");
	}

}
