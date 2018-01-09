package com.proxcloud.firmwareloader;

import com.hoho.android.usbserial.driver.UsbSerialPort;
import java.util.Arrays;
import java.io.*;
import android.os.Environment;
import nl.lxtreme.binutils.elf.*;
import java.util.ArrayList;
import android.util.Log;
import android.widget.Toast;
import android.widget.ProgressBar;
import android.graphics.Color;
import android.content.res.ColorStateList;

//this file is part of the Proxcloud firmware flasher, see proxcloud.eu for details.

public class Flasher {

	static long FlashStartIndex = 0x100000;
	static long FlashEndIndex = (FlashStartIndex + (256*1024));
	static long BootloaderSize = 0x2000;
	static long BootloaderEndIndex = (FlashStartIndex + BootloaderSize);
	static long FlashBlockSize = 0x200;

	static private class Segment {
		java.nio.ByteBuffer data;
		long start;
		long length;
	}

	static private class FirmFile {
		int numSegs;
		String fileName;
		boolean writeBL;
		ArrayList<Segment> segs;
	}
	public static enum CmdType {
		ERROR (0x00),
		PRINT (0x01),
		ACK (0x02),
		FINISH_WRITE (0x03),
		NACK (0x04);

		public int hex;

		CmdType(int hex){
			this.hex = hex;
		}
	}
	public static class Cmd {
		public CmdType type;
		public byte[] data;
		public int length;
		public Cmd(){
			this.data = new byte[544];
			this.length = 0;
		}
	}

	static Thread readThread = new Thread(){
		@Override
		public void run(){
			try{
				while(true){
					Flasher.Cmd cmd = Flasher.readCommand(MainActivity.usbPort);
					if (cmd.length > 0){
						//if the proxmark3 sends a DBprint command, we show a toast
						if (cmd.type == Flasher.CmdType.PRINT){
							final byte[] data = cmd.data;
							MainActivity.main.runOnUiThread(new Runnable() {
								public void run() {
									Toast.makeText(MainActivity.main, new String(data), Toast.LENGTH_SHORT).show();
								}
							});
						} else {
							MainActivity.incomming.add(cmd);
						}
					} else {
						sleep(400);
					}
				}
			} catch (InterruptedException e){

			}
		}
	};

	//read incomming packets from the packets from the pm3
	static public Cmd readCommand(UsbSerialPort port){
		Cmd cmd = new Cmd();
		cmd.type = CmdType.ERROR;
		if (port == null){
			return cmd;
		}

		int read = 0;
		byte bufferGot[] = new byte[544];
		Arrays.fill( bufferGot, (byte) 1 );
		try {
			while (read < 544) {
				cmd.length = read;
				int r = port.read(bufferGot, 1000);

				if (r < 1){
					break;
				} else {
					System.arraycopy(bufferGot, 0, cmd.data, read, r);
					read=read+r;
				}
			}

		} catch (IOException e) {
			return cmd;
		} catch (NullPointerException e) {
			return cmd;
		}

		//decode the packet type, this should be improved
		if (cmd.data[0] == 0x00 && cmd.data[1] == 0x01){
			cmd.type = CmdType.PRINT;
		} else if (cmd.data[0] == (byte)0xff){
			cmd.type = CmdType.ACK;
		} else if (cmd.data[0] == 0xfe && cmd.data[1] == 0x0){
			cmd.type = CmdType.NACK;
		}
		//fprintf("just got packet: "+cmd.type+" "+((byte)cmd.data[0])+" "+formatCmd(cmd));

		cmd.length = read;
		return cmd;
	}
	public boolean sendCommand(UsbSerialPort port, Cmd cmd){
		try {
			port.write(cmd.data, 1000);
		} catch (IOException e){
			return false;
		}
		return true;
	}

	public boolean flash(){
		return false;
	}
	public void setArg(Cmd cmd, int arg, long value){
		setData(cmd, arg+1, value);
	}

	public void setData(Cmd cmd, int index, long value){ 
		int offset = index*8;
		java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(Long.BYTES);
		buffer.putLong(value);
    		byte[] data = buffer.array();
		cmd.data[offset] = data[7];
		cmd.data[offset+1] = data[6];
		cmd.data[offset+2] = data[5];
		cmd.data[offset+3] = data[4];
		cmd.data[offset+4] = data[3];
		cmd.data[offset+5] = data[2];
		cmd.data[offset+6] = data[1];
		cmd.data[offset+7] = data[0];

	}
	public void startFlash(UsbSerialPort port){
		Cmd cmd = new Cmd();
		cmd.data = new byte[] { 0x5, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0};
		sendCommand(port, cmd);
	}
	public boolean flashData(UsbSerialPort port, String path, boolean bootloader){
		FirmFile data = new FirmFile();
		boolean res = loadFile(data, path, bootloader);
		if (!res) {
			return false;
		}
		return WriteFirmwareData(data, port);
	}
	public Cmd getVersion(UsbSerialPort port){
		//version cmd
		byte bufferSend[] = new byte[] { 0x7, 0x1, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0};
		Cmd cmd = new Cmd();
		cmd.data = bufferSend;
		sendCommand(port, cmd);
		return cmd;
	}
	public void sendRestart(UsbSerialPort port){
		byte bufferSend[] = new byte[] { 0x4, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0};
		Cmd cmd = new Cmd();
		cmd.data = bufferSend;
                sendCommand(port, cmd);
	}
	public static void Log(String first, String second){
		Log.i("Proxcloud", first+" : "+second);
	}

	public static void Log(String first){
		Log(first, "");
	}


	//define some C mem methods which work on ByteBuffers
	private java.nio.ByteBuffer malloc(long length){
		return java.nio.ByteBuffer.allocate((int)length);
	}

	private void memset(java.nio.ByteBuffer data, byte fill, long offset){
		for(long i = offset; i < data.limit(); i++){
			data.put((int)i, fill);
		}
	}
	private void memcpy(java.nio.ByteBuffer dest, java.nio.ByteBuffer src, long length){
		memcpy(dest, src, length, 0);
	}
	private void memcpy(java.nio.ByteBuffer dest, java.nio.ByteBuffer src, long length, long offset){
		dest.position((int)offset);
		dest.put(src.array(), 0, (int)length);
	}
	private void memmove(java.nio.ByteBuffer dest, java.nio.ByteBuffer src, long length, long offset){
		for(long i = offset; i < src.limit(); i++){
			dest.put((int)(i-offset), src.get((int)i));
		}
	}

	//do some simple validity checks on program header
	private boolean checkProgramHeader(ProgramHeader pheader){
		int PF_W = 0x2;
		if (pheader.virtualAddress >= FlashStartIndex && pheader.virtualAddress < FlashEndIndex &&  (pheader.flags &  PF_W) != 0) {
			Log("Error", "segment outside bounds");
			return false;
		}
		if (pheader.physicalAddress < FlashStartIndex || (pheader.physicalAddress + pheader.segmentFileSize) >  FlashEndIndex) {
			Log("Error", "segment outside bounds");
			return false;
		}
		if (pheader.segmentFileSize != pheader.segmentMemorySize) {
			Log("Error", "invalid program size");
			return false;
		}

		return true;
	}

	// take the program header chuncks and format them into data blocks for the flash memory 
	private boolean formatDataBlocks(FirmFile firmware, Elf elf) {

		Segment seg;
		long lastEndIndex = 0;
		long physicalAddress = 0;
		long segmentFileSize = 0;
		firmware.segs = new ArrayList<Segment>();
		firmware.numSegs = 0;
		Log("Parsing ELF segments");
		for (int i = 0; i < elf.programHeaders.length; i++) {
			seg = new Segment();
			if (elf.programHeaders[i].type != SegmentType.LOAD) {
				continue;
			}

			ProgramHeader pheader = elf.programHeaders[i];

			physicalAddress = pheader.physicalAddress;
			segmentFileSize = pheader.segmentFileSize;
			if (segmentFileSize == 0) {
				continue;
			}
			if (!checkProgramHeader(pheader)) {
				Log("Error", "Invalid program, won't flash");
				return false;
			}
			if (pheader.physicalAddress < lastEndIndex) {
				Log("Error", "Wrong order of programs");
				return false;
			}
			
			java.nio.ByteBuffer data;
			try{
				data = elf.getSegment(pheader);
			} catch (IOException e){
				Log("error getting program data");
				e.printStackTrace();
				return false;
			}
			long blockOffset = physicalAddress & (FlashBlockSize-1);
			if (blockOffset != 0) {
				if (firmware.numSegs != 0) {
					Segment prevSeg = firmware.segs.get(firmware.segs.size()-1);
					long endIndex = physicalAddress + segmentFileSize;
					long firstBlockIndex = physicalAddress & ~(FlashBlockSize-1);
					long prevBlockIndex = (lastEndIndex - 1) & ~(FlashBlockSize-1);
					if (firstBlockIndex == prevBlockIndex) {
						long newLength = endIndex - prevSeg.start;
						long offset = physicalAddress - prevSeg.start;
						java.nio.ByteBuffer newDataBuff = malloc(newLength);

						memset(newDataBuff, (byte)0xff, newLength);
						memcpy(newDataBuff, prevSeg.data, prevSeg.length);
						memcpy(newDataBuff, data, segmentFileSize, offset);

						prevSeg.data = newDataBuff;
						prevSeg.length = newLength;
						lastEndIndex = endIndex;
						continue;
					}
				}
				Log("segment needs padding");
				memmove(data, data, segmentFileSize, blockOffset);
				memset(data, (byte)0xFF, blockOffset);
				segmentFileSize += blockOffset;
				physicalAddress -= blockOffset;
			}
			seg.data = data;
			seg.start = physicalAddress;
			seg.length = segmentFileSize;
			firmware.segs.add(seg);
			firmware.numSegs++;
			lastEndIndex = physicalAddress + segmentFileSize;
		}
		return true;
	}

	//do some simple validity checks on  the whole program file
	boolean CheckProgram(FirmFile firmware, boolean writeBootloader) {
		for (int i = 0; i < firmware.numSegs; i++) {
			Segment seg = firmware.segs.get(i);
			if ((seg.start & (FlashBlockSize-1)) != 0) {
				Log("Error", "Segments must stare at block boundry");
				return false;
			}
			if (seg.start < FlashStartIndex) {
				Log("Error", "Program address outside bounds");
				return false;
			}
			if (seg.start + seg.length > FlashEndIndex) {
				Log("Error", "Program address outside bounds");
				return false;
			}
			if (!writeBootloader && seg.start < BootloaderEndIndex) {
				Log("This program has an address inside the bootloader, but you didn't enable bootloader writes");
				return false;
			}
		}
		return true;
	}


	// Load an ELF file and prepare it for flashing 
	boolean loadFile(FirmFile firmware, String fileName, boolean writeBootloader) {
		boolean result;
		Elf elf;
		try {
			File file = new File(fileName);
			elf = new Elf(file);
		} catch (IOException e){
			e.printStackTrace();
			Log("Couldn't open file, or the file is not the correct format : ", fileName);
			return false;
		}
		
		if (elf.header.elfType != ObjectFileType.EXEC) {
			Log("This is not an ELF file!");
			return false;
		}
		if (elf.header.machineType != MachineType.ARM) {
			Log("ELF architecture wrong, did you cross compile it correctly?");
			return false;
		}
		if (elf.programHeaders.length == 0) {
			Log("File is missing programm headers");
			return false;
		}
		// todo: add more safety checks, the old proxmark3 flasher does more checking
		result = formatDataBlocks(firmware, elf);
		if (!result) {
			return false;
		}
		result = CheckProgram(firmware, writeBootloader);
		if (!result) {
			return false;
		}
		firmware.fileName = fileName;
		Log("loaded firmware file:");
		Log("numSegs "+firmware.numSegs);
		/*for(int i = 0; i<firmware.segs.size(); i++){
			Segment seg = firmware.segs.get(i);
			Log("seg: "+i+" start: "+seg.start+ " length: "+seg.length+" real length "+seg.data.limit());
		}*/
		return true; 
	}

	public boolean  WriteFirmwareData(FirmFile firmware, UsbSerialPort port) {
		Log("Writing segments for file: "+ firmware.fileName);
		for (int i = 0; i < firmware.numSegs; i++) {
			Segment seg = firmware.segs.get(i);
			long length = seg.length;
			long blocks = (length + FlashBlockSize - 1) / FlashBlockSize;
			long end = seg.start + length;
			int block = 0;
			byte[] data = new byte[(int)FlashBlockSize];
			long blockAddress = seg.start;
			int offset = 0;
			while (length != 0) {
				seg.data.position(offset);
				seg.data.get(data, 0, (int)Math.min(FlashBlockSize, seg.data.limit()-offset));
				long blockSize = length;
				if (blockSize > FlashBlockSize) {
					blockSize = FlashBlockSize;
				}
				if (!writeFirmwareBlock(blockAddress, data, blockSize, port)) {
					Log( "Error", "failed to write block "+ block +" / "+blocks);
					return false;
				}
				final int b = (int)Math.round(((block+1.0)/((float)blocks))*100);
				final int b2 = block;
				final int max = (int)blocks;
				MainActivity.main.runOnUiThread(new Runnable() {
					public void run() {
						// update the progress in the UI
						final ProgressBar bar = (ProgressBar)MainActivity.main.findViewById(R.id.progressBar);
						bar.setMax(max);
						bar.setProgress(b2+1);
						bar.setProgressTintList(ColorStateList.valueOf(Color.argb(255, 0xae, 0xc6, 0x2c)));

						MainActivity.main.textStatus.setText("Writing "+b+"%");
					}
				});

				offset += (int) blockSize;
				blockAddress += blockSize;
				length -= blockSize;
				block++;
			}
		}
		return true;
	}



	int blc = 0;
	boolean writeFirmwareBlock(long address, byte[] data, long length, UsbSerialPort port) {

		java.nio.ByteBuffer blockBuff = java.nio.ByteBuffer.wrap(new byte[(int)FlashBlockSize+4*8]);
		memset(blockBuff, (byte)0xFF, FlashBlockSize+4*8);
		memcpy(blockBuff, java.nio.ByteBuffer.wrap(data), length, 4*8);
	  	Cmd cmd = new Cmd();
		cmd.type = CmdType.FINISH_WRITE;
		cmd.data = blockBuff.array();

		setArg(cmd, 0, address);

		//this is the write block command:
		cmd.data[0] = 0x3;
		cmd.data[1] = 0x0;
		cmd.data[2] = 0x0;
		cmd.data[3] = 0x0;
		cmd.data[4] = 0x0;
		cmd.data[5] = 0x0;
		cmd.data[6] = 0x0;
		cmd.data[7] = 0x0;

		Log("sending block: "+blc);
		blc += 1;

		sendCommand(port, cmd);
		return waitCmdAck();
	}

	public String formatCmd(Cmd cmd){
		java.util.Formatter formatter = new java.util.Formatter();
		int c = 0;
		for (byte b : cmd.data) {
			c++;
			if (c > 50){
		        	break;
			}
			formatter.format("%02x ", b);
		}
		return formatter.toString();
	}
	boolean waitCmdAck() {
		Cmd ack;
		int counter = 0;
		MainActivity.incomming.clear();

		while (true) { //this sucks, we should be smarter about waiting for the Ack reponse packet
			if (MainActivity.incomming.size() > 0) {
				ack = MainActivity.incomming.get(0);
				MainActivity.incomming.remove(0);
				if (ack.type != CmdType.ACK) {
					Log("Error: Waiting for Ack packet but got "+ack.type);
					return false;
				} else {
					return true;
				}
			} else {
				try{
					Thread.sleep(10);
				} catch (java.lang.InterruptedException e){

				}
			}
			counter++;
			if (counter > 30) {
				return false;
			}
		}
	}

	public boolean setFlashArea(boolean writeBootloader, UsbSerialPort port) {

		//we need to write add code to which state the pm3 is in here, not just assume it is right
		
		Cmd cmd = new Cmd();
		if (writeBootloader) {
			setArg(cmd, 0, FlashStartIndex);
			setArg(cmd, 1, FlashEndIndex);
			setArg(cmd, 2, 0x54494f44);// this is just some number hard coded in the pm3 firmware
		} else {
			setArg(cmd, 0,  BootloaderEndIndex);
			setArg(cmd, 1, FlashEndIndex);
			setArg(cmd, 2, 0);
		}
		cmd.data[0] = 0x5;
		cmd.data[1] = 0x0;

		sendCommand(port, cmd);
		Log("sent START flash cmd, waiting for ack...");
		return waitCmdAck();

	}
}
