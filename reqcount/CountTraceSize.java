/* 
    Counts the amount of bytes needed to store all items in a trace.

    trace 01: 682842924032
        10% = 65120 slabs of size 1048576

 */

import java.util.*;
import java.io.*;
import java.nio.*;

public class CountTraceSize {

    public static class TraceLine {
        // static variables
        static byte[] id_buff = new byte[8];
        static byte[] size_buff = new byte[4];
        static byte[] next_time_buff = new byte[8];
    
        // class variables
        long id;
        int size;
        long next_time;

        // constructor: parse numbers from whatever is stored in static arrs
        public TraceLine() {
            ByteBuffer bb;

            bb = ByteBuffer.wrap(id_buff);
            bb.order( ByteOrder.LITTLE_ENDIAN );
            this.id = bb.getLong();

            bb = ByteBuffer.wrap(size_buff);
            bb.order( ByteOrder.LITTLE_ENDIAN );
            this.size = bb.getInt();

            bb = ByteBuffer.wrap(next_time_buff);
            bb.order( ByteOrder.LITTLE_ENDIAN );
            this.next_time = bb.getLong();
        }

        // constructor: manually parse line from values and set here
        public TraceLine(long id, int size, long next_time) {
            this.id = id;
            this.size = size;
            this.next_time = next_time;
        }
    }

    static int SLAB_SIZE = 1048576;

    // class variables
    String traceLink;
    HashMap<Long, Integer> item_sizes;
    BufferedInputStream reader;

    // constructor
    public CountTraceSize(String traceLink) {
        // class variables
        this.item_sizes = new HashMap<Long, Integer>();
        this.traceLink = traceLink;

        // init readers and writers 
        try {
            this.reader = new BufferedInputStream(new FileInputStream(new File(this.traceLink)));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // class methods
    public TraceLine readTraceLine() throws IOException{
        // discard the first 4 bytes (actual time)
        if (this.reader.read(TraceLine.size_buff) == -1) return null;

        // read the next fields from the trace
        if (this.reader.read(TraceLine.id_buff) == -1) return null;
        if (this.reader.read(TraceLine.size_buff) == -1) return null;
        if (this.reader.read(TraceLine.next_time_buff) == -1) return null;
        return new TraceLine();
    }
    
    public void processLine(TraceLine line) {
        // if the item isn't going to fit in any slab class, just ignore it
        if (line.size > SLAB_SIZE) return;

        this.item_sizes.put(line.id, line.size);
    }

    public long sumSizes() {
        long size = 0;
        for (int item_size : this.item_sizes.values()) {
            size += item_size;
        }
        return size;
    }

    public long processTrace() {
        try {
            TraceLine line;
            while((line = this.readTraceLine()) != null) {

                this.processLine(line);
            }
            this.reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        long size = sumSizes();
        System.out.println(size);
        return size;
    }

    // example how to run
    public static void main(String[] args) throws Exception {
        CountTraceSize alloc = new CountTraceSize("/mntData2/jason/cphy/w01.oracleGeneral.bin");
        System.out.println(alloc.processTrace());
    }
}

