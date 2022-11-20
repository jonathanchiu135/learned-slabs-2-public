/* 
 * Determines a slab allocation using cost/benefit analysis
 */
import java.util.*;
import java.io.*;
import java.nio.*;

public class AllocCostBenefit {

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
        
            // this.id = ByteBuffer.wrap(id_buff).getLong();
            // this.size = ByteBuffer.wrap(size_buff).getInt();
            // this.next_time = ByteBuffer.wrap(next_time_buff).getLong();
        }

        // constructor: manually parse line from values and set here
        public TraceLine(long id, int size, long next_time) {
            this.id = id;
            this.size = size;
            this.next_time = next_time;
        }
    }

    public class CacheItem { 
        int size;
        long next_time;

        public CacheItem(int size, long next_time) {
            this.size = size;
            this.next_time = next_time;
        }
    }

    // static variables: are 15 slab classes
    public static int[] SLAB_CLASSES = { 64, 128, 256, 512, 1024, 2048, 4096, 8192, 
        16384, 32768, 65536, 131072, 262144, 524288, 1048576};
    public static int LINES_READ_PER_CHUNK = 540000;
    static int SLAB_SIZE = 1048576;
    static int MOVE_THRESHOLD = 1000;

    // class variables
    int t;
    long numLines;
    String traceLink;
    String resultLink;
    HashMap<Integer, Integer> SLAB_COUNTS_MAP;
    BufferedInputStream reader;
    BufferedWriter writer;
    
 
    // cache variables
    public HashMap<Integer, Integer> cacheUsedSpace;                    // maps sc to how much total space in sc
    public HashMap<Integer, LinkedHashMap<Long, CacheItem>> cacheLRU;   // maps sc to LRU list
    public int epochhits;
    public int lifetimehits;

    // constructor
    public AllocCostBenefit(String traceLink, String resultLink) {
        // class variables
        this.t = 1;
        this.traceLink = traceLink;
        this.numLines = new File(this.traceLink).length() / 24;
        this.resultLink = resultLink;

        // init readers and writers
        try {
            this.reader = new BufferedInputStream(new FileInputStream(new File(this.traceLink)));
            this.writer = new BufferedWriter(new FileWriter(this.resultLink));
        } catch (IOException e) {
            e.printStackTrace();
        }

        // cache variables
        this.cacheUsedSpace = new HashMap<>();
        this.cacheLRU = new HashMap<>();
        for (int sc : SLAB_CLASSES) {
            this.cacheUsedSpace.put(sc, 0);
            this.cacheLRU.put(sc, new LinkedHashMap<Long, CacheItem>());
        }
        this.epochhits = 0;
        this.lifetimehits = 0;

        // set initial slab counts
        this.SLAB_COUNTS_MAP = new HashMap<>();
        this.SLAB_COUNTS_MAP.put(64, 16);
        this.SLAB_COUNTS_MAP.put(128, 8);
        this.SLAB_COUNTS_MAP.put(256, 8);
        this.SLAB_COUNTS_MAP.put(512, 4);
        this.SLAB_COUNTS_MAP.put(1024, 4);
        this.SLAB_COUNTS_MAP.put(2048, 3);
        this.SLAB_COUNTS_MAP.put(4096, 3);
        this.SLAB_COUNTS_MAP.put(8192, 2);
        this.SLAB_COUNTS_MAP.put(16384, 2);
        this.SLAB_COUNTS_MAP.put(32768, 2);
        this.SLAB_COUNTS_MAP.put(65536, 1);
        this.SLAB_COUNTS_MAP.put(131072, 1);
        this.SLAB_COUNTS_MAP.put(262144, 1);
        this.SLAB_COUNTS_MAP.put(524288, 1);
        this.SLAB_COUNTS_MAP.put(1048576, 1);
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

    // @requires: the item is contained in the sc
    public void removeFromCache(int sc, long id, CacheItem oldItem) {
        this.cacheUsedSpace.put(sc, this.cacheUsedSpace.get(sc) - oldItem.size);
        this.cacheLRU.get(sc).remove(id);
    }

    public void addToCache(int sc, TraceLine line) {
        // remove until there is enough space in the sc
        if (line.size > this.SLAB_COUNTS_MAP.get(sc) * SLAB_SIZE) {
            return;
        }
        LinkedHashMap<Long, CacheItem> slabClassLRU = this.cacheLRU.get(sc);
        Iterator<Long> it = slabClassLRU.keySet().iterator();
        while (this.cacheUsedSpace.get(sc) + line.size > this.SLAB_COUNTS_MAP.get(sc) * SLAB_SIZE) {
            Long removeItemId = it.next();
            this.cacheUsedSpace.put(sc, this.cacheUsedSpace.get(sc) - slabClassLRU.get(removeItemId).size);
            it.remove();
        }
        this.cacheUsedSpace.put(sc, this.cacheUsedSpace.get(sc) + line.size);
        slabClassLRU.put(line.id, new CacheItem(line.size, line.next_time));
    }
    

    // move least used -> to most used
    // just use purely hitrate to judge performance (don't scale by size)


    public void processLine(TraceLine line) {
        // if the item isn't going to fit in any slab class, just ignore it
        if (line.size > SLAB_SIZE) return;
        
        // add to the cache
        int sc = getSlabClass(line.size);
        CacheItem oldItem = this.cacheLRU.get(sc).get(line.id);
        if (oldItem != null) {
            // assumes that objects cannot change sc (if does, will still count as hit)
            this.lifetimehits++;
            this.epochhits++;
            this.removeFromCache(sc, line.id, oldItem);
        }
        this.addToCache(sc, line);
    }

    public Map<Integer, Float> computeCBPerSlab() {
        Map<Integer, Float> scToCB = new HashMap<>();
        for (int sc : SLAB_CLASSES) {
            LinkedHashMap<Long, CacheItem> slabClassState = this.cacheLRU.get(sc);
            Iterator<Map.Entry<Long, CacheItem>> it = slabClassState.entrySet().iterator();
            float total_ratio = 0;
            while (it.hasNext()) {
                CacheItem item = it.next().getValue();

                // since cost is in the numerator, if never accessed again, C/B = 0; just don't add
                // add C/B per item (instead of accumulating all, then calculating C/B)
                if (item.next_time != -1) {
                    // divide by B
                    long time_btw_access = (item.next_time == -1 ? this.numLines : item.next_time) - this.t;
                    total_ratio += (float) ((10000000000.0 / time_btw_access) / sc);
                }
            }
            scToCB.put(sc, total_ratio);
        }
        return scToCB;
    }

    public void moveSlab(int min, int max) {
        // drop items from the LRU until have enough space to remove a slab 
        LinkedHashMap<Long, CacheItem> slabClassLRU = this.cacheLRU.get(min);
        Iterator<Long> it = slabClassLRU.keySet().iterator();
        while (this.SLAB_COUNTS_MAP.get(min) * SLAB_SIZE - this.cacheUsedSpace.get(min) < SLAB_SIZE) {
            Long removeItemId = it.next();
            this.cacheUsedSpace.put(min, this.cacheUsedSpace.get(min) - slabClassLRU.get(removeItemId).size);
            it.remove();
        }

        this.SLAB_COUNTS_MAP.put(max, this.SLAB_COUNTS_MAP.get(max) + 1);
        this.SLAB_COUNTS_MAP.put(min, this.SLAB_COUNTS_MAP.get(min) - 1);
    }

    /*
        Cost: indicator for if item accessed again
        Benefit: amount of space "freed up" by moving slab away
        Low C/B => more likely to move away 
    */
    public void collectStats() {
        try {
            // re-alloc the slabs based on cb analysis
            Map<Integer, Float> scToCB = computeCBPerSlab();

            // calculate the min SC (where the SLAB_COUNTS is still > 0) and max SC
            int min = -1;
            int max = -1;
            float maxCB = 0;
            float minCB = Float.MAX_VALUE;
            for (int sc : SLAB_CLASSES) {
                if (scToCB.get(sc) >= maxCB) {
                    max = sc;
                    maxCB = scToCB.get(sc);
                } 
                if (scToCB.get(sc) <= minCB && this.SLAB_COUNTS_MAP.get(sc) > 0) {
                    min = sc;
                    minCB = scToCB.get(sc);
                }
            }

            // move a slab from min to max slab class
            // if (min != -1 && min != max && minCB < MOVE_THRESHOLD) {
            if (min != -1 && min != max) {
                if (this.SLAB_COUNTS_MAP.get(min) != 0) moveSlab(min, max);
            }   
            
            // write the hit rate over the last epoch to file
            this.writer.write("epoch " + String.valueOf(this.t / LINES_READ_PER_CHUNK) + "\n");
            // this.writer.write(String.format("moved slab from %d to %d\n", min, max));
            // this.writer.write("cost / benefits: " + scToCB.toString() + "\n");
            // this.writer.write("slab counts: " + this.SLAB_COUNTS_MAP.toString() + "\n");
            this.writer.write(String.format("epoch hitrate: %f\n", (float) this.epochhits / (float) LINES_READ_PER_CHUNK ));
            this.writer.write(String.format("lifetime hitrate: %f\n", (float) this.lifetimehits / (float) this.t));    
            this.writer.write("\n");
            this.epochhits = 0;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("error collecting stats");
        }
    }

    public void processTrace() {
        try {
            TraceLine line;
            while((line = this.readTraceLine()) != null) {
                if (this.t % LINES_READ_PER_CHUNK == 0) {
                    this.collectStats();
                }
                this.processLine(line);
                this.t++;
            }
            this.writer.close();
            this.reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // example how to run
    public static void main(String[] args) throws Exception {
        AllocCostBenefit alloc = new AllocCostBenefit("/mntData2/jason/cphy/w20.oracleGeneral.bin", "./results/w20.cost-benefit.txt");
        alloc.processTrace();
    }
}



