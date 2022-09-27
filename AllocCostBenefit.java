/* 
 * Determines a slab allocation using cost/benefit analysis
 */
import java.util.*;
import java.io.*;
import java.nio.*;
import java.security.spec.ECFieldF2m;

public class AllocCostBenefit {

    public class TraceLine {
        // static variables
        static byte[] id_buff = new byte[8];
        static byte[] size_buff = new byte[4];
        static byte[] next_time_buff = new byte[8];
    
        // class variables
        int real_time;
        long id;
        int size;
        long next_time;


        // constructor: parse numbers from whatever is stored in static arrs
        public TraceLine() {
            this.id = ByteBuffer.wrap(id_buff).getLong();
            this.size = ByteBuffer.wrap(size_buff).getInt();
            this.next_time = ByteBuffer.wrap(next_time_buff).getLong();
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
    public static int[] SLAB_COUNTS = { 16, 8, 8, 4, 4, 3, 3, 
        2, 2, 2, 1, 1, 1, 1, 1 }; // arbitrarily assign initial slab counts
    public static Map<Integer, Integer> SLAB_COUNTS_MAP;
        static {
            SLAB_COUNTS_MAP = new HashMap<>();
            SLAB_COUNTS_MAP.put(64, 16);
            SLAB_COUNTS_MAP.put(128, 8);
            SLAB_COUNTS_MAP.put(256, 8);
            SLAB_COUNTS_MAP.put(512, 4);
            SLAB_COUNTS_MAP.put(1024, 4);
            SLAB_COUNTS_MAP.put(2048, 3);
            SLAB_COUNTS_MAP.put(4096, 3);
            SLAB_COUNTS_MAP.put(8192, 2);
            SLAB_COUNTS_MAP.put(16384, 2);
            SLAB_COUNTS_MAP.put(32768, 2);
            SLAB_COUNTS_MAP.put(65536, 1);
            SLAB_COUNTS_MAP.put(131072, 1);
            SLAB_COUNTS_MAP.put(262144, 1);
            SLAB_COUNTS_MAP.put(524288, 1);
            SLAB_COUNTS_MAP.put(1048576, 1);
        }
    public static int LINES_READ_PER_CHUNK = 5400;
    static int SLAB_SIZE = 1048576;

    // class variables
    int t;
    long numLines;
    String traceLink;
    String resultLink;
    HashMap<Integer, Long> idToClosestTime;
    BufferedInputStream reader;
    BufferedWriter writer;
    
 
    // cache variables
    public HashMap<Integer, Integer> cacheUsedSpace;                    // maps sc to how much space used
    public HashMap<Integer, LinkedHashMap<Long, CacheItem>> cacheLRU;   // maps sc to LRU list

    // constructor
    public AllocCostBenefit(String traceLink, String resultLink) {
        // class variables
        this.t = 0;
        this.traceLink = traceLink;
        this.numLines = new File(this.traceLink).length();
        this.resultLink = resultLink;
        this.idToClosestTime = new HashMap<Integer, Long>();
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
            this.cacheLRU.put(sc, new LinkedHashMap<Long, CacheItem>() {
                // protected boolean removeEldestEntry(Map.Entry<Long, CacheItem> eldest) {
                //     if (cacheUsedSpace.get(sc) > SLAB_SIZE * SLAB_COUNTS_MAP.get(sc)) {
                //         cacheUsedSpace.put(sc, cacheUsedSpace.get(sc) - eldest.getValue().size);
                //         return true;
                //     } else {
                //         return false;
                //     }
                // }
            });
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
        return SLAB_CLASSES[low];
        
    }

    // class methods
    public TraceLine readTraceLine() throws IOException{
        // discard the first 4 bytes (actual time)
        if (this.reader.read(TraceLine.size_buff) == -1) return null;

        // read the next fields from the trace
        if (this.reader.read(TraceLine.id_buff) == -1) return null;
        if (this.reader.read(TraceLine.size_buff) == -1) return null;
        if (this.reader.read(TraceLine.next_time_buff) == -1) return null;
        this.t++;
        return new TraceLine();
    }

    // @requires: the item is contained in the sc
    public void removeFromCache(int sc, long id, CacheItem oldItem) {
        this.cacheUsedSpace.put(sc, this.cacheUsedSpace.get(sc) - oldItem.size);
        this.cacheLRU.get(sc).remove(id);
    }

    public void addToCache(int sc, TraceLine line) {
        // remove until there is enough space in the sc
        LinkedHashMap<Long, CacheItem> slabClassLRU = this.cacheLRU.get(sc);
        Iterator<Long> it = slabClassLRU.keySet().iterator();
        while (this.cacheUsedSpace.get(sc) + line.size > SLAB_COUNTS_MAP.get(sc) * SLAB_SIZE) {
            Long removeItemId = it.next();
            this.cacheUsedSpace.put(sc, this.cacheUsedSpace.get(sc) - slabClassLRU.get(removeItemId).size);
            it.remove();
        }
        this.cacheUsedSpace.put(sc, this.cacheUsedSpace.get(sc) + line.size);
        slabClassLRU.put(line.id, new CacheItem(line.size, line.next_time));
    }

    public void processLine(TraceLine line) {
        // if the item isn't going to fit in any slab class, just ignore it
        if (line.size > SLAB_SIZE) return;
        
        // add to the cache
        int sc = getSlabClass(line.size);
        CacheItem oldItem = this.cacheLRU.get(sc).get(line.id);
        if (oldItem != null) {
            // assumes that objects cannot change sc
            this.removeFromCache(sc, line.id, oldItem);
        }
        this.addToCache(sc, line);
    }

    public void collectStats() {
        try {
            for (int sc : SLAB_CLASSES) {
                LinkedHashMap<Long, CacheItem> slabClassState = this.cacheLRU.get(sc);
                int numNeverAccessed = 0;
                int totalBenefit = 0;
                Iterator<Map.Entry<Long, CacheItem>> it = slabClassState.entrySet().iterator();

                while (it.hasNext()) {
                    CacheItem item = it.next().getValue();

                    // calculate the cost
                    if (item.next_time == -1) numNeverAccessed++;

                    // calculate the benefit (if it exists)
                    if (item.next_time > 0) {
                        totalBenefit += (item.next_time - this.t) * sc;
                    } else {
                        totalBenefit += (this.numLines - this.t) * sc;
                    }
                }

                String s = String.format("cost:%d/benefit:%d/ratio:%f\n", numNeverAccessed, totalBenefit, (float) numNeverAccessed / totalBenefit);
                this.writer.write(s);
            }
            this.writer.write("\n");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("error collecting stats");
        }
    }

    public void processTrace() throws IOException {
        TraceLine line;
        while((line = this.readTraceLine()) != null) {
            System.out.printf("%d %d %d\n", line.id, line.next_time, line.size);
            if (this.t % LINES_READ_PER_CHUNK == 0) {
                this.collectStats();
            }
            this.processLine(line);
            this.t++;
        }
        reader.close();
    }

    public static void main(String[] args) throws Exception {
        AllocCostBenefit alloc = new AllocCostBenefit("./traces/binTrace", "./results/test-alloc.txt");
        alloc.processTrace();
    }
}



