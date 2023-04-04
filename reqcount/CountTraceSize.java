/* 
    Determines a slab allocation using request-count analysis 

    Simply count the number of requests for each slab in each epoch and move 
    slabs to commonly-requested slab classes to unused ones. 

    Naively always moves, without considering the cost of moving (evicting items
    currently in the slab). Will add this consideration in the future. 

    Slightly different from HRC analysis previously done. HRC analysis, in 
    each epoch, for each slab class, computed the "HRC", which mapped each 
    possible cache size "n" to the number of items with stack distance <= n (
    since if the stack distance is smaller than a size, the cache will get a 
    hit). Then, the "benefit" of moving to a slab class was the gain in items
    that would get a hit by moving from cache_size to cache_size+slab. 

    This strategy purely just looks at the number of requests for each slab 
    class, and it will add in additional cost analysis later. 

    Returns an ArrayList<HashMap<Integer, Integer>> describing how many slabs
    should be allocated for each slab class at each epoch. 
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

    // class variables
    String traceLink;
    HashMap<Long, Integer> item_sizes;
    BufferedInputStream reader;
    
    // constructor
    public CountTraceSize(String traceLink) {
        // class variables
        this.item_sizes = new HashMap<Integer, Integer>();

        // init readers and writers 
        try {
            this.reader = new BufferedInputStream(new FileInputStream(new File(this.traceLink)));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // static methods
    public static int getSlabClass(int size) {
        int low = 0;
        int high = SLAB_CLASSES.length;
        while (low < high) {
            int mid = (low + high) / 2;
            if (SLAB_CLASSES[mid] == size) {
                return SLAB_CLASSES[mid];
            } else if (size < SLAB_CLASSES[mid]) {
                high = mid;
            } else {
                low = mid + 1;
            }
        }       
        return low < SLAB_CLASSES.length ? SLAB_CLASSES[low] : SLAB_CLASSES[SLAB_CLASSES.length - 1];
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

