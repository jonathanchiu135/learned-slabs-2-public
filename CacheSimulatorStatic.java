/* 
 * Simulates a static cache on the specified trace 
 */

import java.util.*;
import java.io.*;
import java.nio.*;



public class CacheSimulatorStatic {

    public static class TraceLine {
        // static variables
        static byte[] id_buff = new byte[8];
        static byte[] size_buff = new byte[4];

        // class variables
        long id;
        int size;

        // constructor: parse numbers from whatever is stored in static arrs
        public TraceLine() {
            ByteBuffer bb;

            bb = ByteBuffer.wrap(id_buff);
            bb.order( ByteOrder.LITTLE_ENDIAN );
            this.id = bb.getLong();

            bb = ByteBuffer.wrap(size_buff);
            bb.order( ByteOrder.LITTLE_ENDIAN );
            this.size = bb.getInt();
        }

        // constructor: manually parse line from values and set here
        public TraceLine(long id, int size, long next_time) {
            this.id = id;
            this.size = size;
        }
    }

    public static int[] SLAB_CLASSES = {64, 128, 256, 512, 1024, 2048, 4096, 8192, 
        16384, 32768, 65536, 131072, 262144, 524288, 1048576};
    public static int SLAB_SIZE = 1048576;
    public static int LINES_READ_PER_CHUNK = 540000;


    public int hits;
    public int epochhits;
    public int requests;
    public HashMap<Integer, Integer> slabAllocation;
    public String traceLink;
    public String resultLink;
    public BufferedInputStream reader;
    public BufferedWriter writer;

    public HashMap<Integer, Integer> cacheUsedSpace;
    public HashMap<Integer, LinkedHashMap<Long, Integer>> cacheLRU; // maps sc -> item id -> size

    /* 
        Constructor
    */
    public CacheSimulatorStatic(String traceLink, String resultLink, HashMap<Integer, Integer> slabAllocation) {
        this.hits = 0;
        this.epochhits = 0;
        this.requests = 1;
        this.traceLink = traceLink;
        this.resultLink = resultLink;
        this.slabAllocation = slabAllocation;
        
        this.cacheUsedSpace = new HashMap<Integer, Integer>();
        this.cacheLRU = new HashMap<Integer, LinkedHashMap<Long, Integer>>();
        
        for (int sc : SLAB_CLASSES) {
            this.cacheUsedSpace.put(sc, 0);
            this.cacheLRU.put(sc, new LinkedHashMap<Long, Integer>());
        }

        try {
            this.reader = new BufferedInputStream(new FileInputStream(new File(this.traceLink)));
            this.writer = new BufferedWriter(new FileWriter(this.resultLink));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
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
        this.reader.skip(8);
        
        return new TraceLine();
    }

    public void removeFromCache(int sc, TraceLine line) {
        this.cacheUsedSpace.put(sc, this.cacheUsedSpace.get(sc) - line.size);
        this.cacheLRU.get(sc).remove(line.id);        
    }

    // handles LRU, as well as evictions if exceeds capacity
    public void addToCache(int sc, TraceLine line) {
        // if there is not enough space to hold the item, just don't store it
        if (line.size > this.slabAllocation.get(sc) * SLAB_SIZE) return;

        LinkedHashMap<Long, Integer> currSlabLRU = this.cacheLRU.get(sc);
        Iterator<Long> currIt = currSlabLRU.keySet().iterator();

        // remove from the slab class until there is enough space
        while (this.slabAllocation.get(sc) * SLAB_SIZE - this.cacheUsedSpace.get(sc) < line.size) {
            int removeItemSize = currSlabLRU.get(currIt.next());
            this.cacheUsedSpace.put(sc, this.cacheUsedSpace.get(sc) - removeItemSize);
            currIt.remove();
        }

        // add the item to the slab class
        this.cacheUsedSpace.put(sc, this.cacheUsedSpace.get(sc) + line.size);
        this.cacheLRU.get(sc).put(line.id, line.size);
    }

    public void processLine(TraceLine line) {
        int sc = getSlabClass(line.size);
        if (this.cacheLRU.get(sc).containsKey(line.id)) {
            this.removeFromCache(sc, line);
            this.epochhits++;
            this.hits++;
        } 
        this.addToCache(sc, line);
        this.requests++;
    }

    public void collectStats() {
        // figure out the utilization for each slab class and see if it looks right
        HashMap<Integer, Float> util = new HashMap<Integer, Float>();
        for (int sc : SLAB_CLASSES) {
            util.put(sc, (float) this.cacheUsedSpace.get(sc) / (float) (this.slabAllocation.get(sc) * SLAB_SIZE));
        }
        // write the results
        try {
            this.writer.write("epoch #" + String.valueOf(this.requests / LINES_READ_PER_CHUNK) + "\n");
            this.writer.write(util.toString() + "\n");
            this.writer.write(String.format("epoch hitrate: %d / %d = %f\n", this.epochhits, this.requests, (float) this.epochhits / (float) this.requests));
            this.writer.write(String.format("overall hitrate: %d / %d = %f\n", this.hits, this.requests, (float) this.hits / (float) this.requests));
            this.writer.write("\n");
            this.epochhits = 0;   
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void calculateHitRate() {        
        try {
            TraceLine line;
            while((line = this.readTraceLine()) != null) {
                if (this.requests % LINES_READ_PER_CHUNK == 0) {
                    this.collectStats();
                }
                this.processLine(line);
            }
            this.writer.close();
            this.reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args){  
        System.out.println("Initializing cache and counting hits!");  
        HashMap<Integer, Integer> slabAllocation = new HashMap<>();
        slabAllocation.put(64, 16);
        slabAllocation.put(128, 8);
        slabAllocation.put(256, 8);
        slabAllocation.put(512, 4);
        slabAllocation.put(1024, 4);
        slabAllocation.put(2048, 3);
        slabAllocation.put(4096, 3);
        slabAllocation.put(8192, 2);
        slabAllocation.put(16384, 2);
        slabAllocation.put(32768, 2);
        slabAllocation.put(65536, 1);
        slabAllocation.put(131072, 1);
        slabAllocation.put(262144, 1);
        slabAllocation.put(524288, 1);
        slabAllocation.put(1048576, 1);
        
        CacheSimulatorStatic currCache = new CacheSimulatorStatic("/mntData2/jason/cphy/w04.oracleGeneral.bin", "./results/w04.static-cache.txt", slabAllocation);
        currCache.calculateHitRate();
    }

}