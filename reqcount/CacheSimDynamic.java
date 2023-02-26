// import java.util.*;
// import java.io.*;
// import java.io.File;
// import java.nio.*;

// import java.nio.file.Paths;
// import java.nio.file.Files;


// public class CacheSimDynamic {

//     public static class TraceLine {
//         // static variables
//         static byte[] id_buff = new byte[8];
//         static byte[] size_buff = new byte[4];

//         // class variables
//         long id;
//         int size;

//         // constructor: parse numbers from whatever is stored in static arrs
//         public TraceLine() {
//             ByteBuffer bb;

//             bb = ByteBuffer.wrap(id_buff);
//             bb.order( ByteOrder.LITTLE_ENDIAN );
//             this.id = bb.getLong();

//             bb = ByteBuffer.wrap(size_buff);
//             bb.order( ByteOrder.LITTLE_ENDIAN );
//             this.size = bb.getInt();
//         }

//         // constructor: manually parse line from values and set here
//         public TraceLine(long id, int size, long next_time) {
//             this.id = id;
//             this.size = size;
//         }
//     }
    
//     public static int[] SLAB_SIZES = {64, 128, 256, 512, 1024, 2048, 4096, 8192, 
//         16384, 32768, 65536, 131072, 262144, 524288, 1048576};
//     public static int[] POTENTIAL_TOTAL_SLABS_COUNTS = { 10, 20, 50, 100, 200, 512, 1024, 2048, 4096 };
//     public static int SLAB_SIZE = 1048576;
//     public static int LINES_READ_PER_CHUNK = 540000;
//     public static String traceLink = "/mntData2/cluster46sampled100.txt";
//     public static String allocationLink = "./HFCResults/HFC-smallslabs-cluster50hashed-4096slabs.txt";
//     public static String resultLink = "";

//     // public static String allocationLink;

//     public int hits;
//     public int requests;
//     public int epochNum;

//     public ArrayList<HashMap<Integer, Integer>> slabAllocPerEpoch;
//     public HashMap<Integer, Integer> slabAllocation;

//     public HashMap<Integer, Integer> cacheUsedSpace;
//     public HashMap<Long, Integer> cacheContents; // maps id to size
//     public HashMap<Integer, LinkedHashSet<Long>> cacheLRU;

//     public BufferedInputStream reader;

//     /* 
//         Constructor
//     */
//     public CacheSimDynamic(ArrayList<HashMap<Integer, Integer>> slabAllocPerEpoch) {
//         this.hits = 0;
//         this.requests = 1;
//         this.epochNum = 0;

//         this.slabAllocPerEpoch = slabAllocPerEpoch;

//         this.cacheUsedSpace = new HashMap<Integer, Integer>();
//         this.cacheContents = new HashMap<Long, Integer>();
//         this.cacheLRU = new HashMap<Integer, LinkedHashSet<Long>>();
        
//         for (int sc : SLAB_SIZES) {
//             this.cacheUsedSpace.put(sc, 0);
//             this.cacheLRU.put(sc, new LinkedHashSet<Long>());
//         }
        
//         try {
//             this.reader = new BufferedInputStream(new FileInputStream(new File(traceLink)));
//         } catch(Exception e) {
//             e.printStackTrace();
//         }

//     }

//     public void initCache() {
//         try {
//             // determine the slab allocation for the trace by reading result file
//             this.slabAllocation = this.getNextSlabAllocation();
//         } catch (Exception e) {
//             e.printStackTrace();
//         }
//     }

//     public TraceLine readTraceLine() throws IOException {
//         // discard the first 4 bytes (actual time)
//         if (this.reader.read(TraceLine.size_buff) == -1) return null;

//         // read the next fields from the trace
//         if (this.reader.read(TraceLine.id_buff) == -1) return null;
//         if (this.reader.read(TraceLine.size_buff) == -1) return null;
//         this.reader.skip(8);

//         return new TraceLine();
//     }

//     public HashMap<Integer, Integer> getNextSlabAllocation() {
//         try {
//             // HashMap<Integer, Integer> newSlabAlloc = new HashMap<Integer, Integer>();
           
//             // String line = this.slabAllocReader.readLine();
//             // line = line.substring(1, line.length() - 1);
//             // String[] keyVals = line.split(", ");
//             // for(String keyVal : keyVals ){
//             //     String[] parts = keyVal.split("=", 2);
//             //     int sc = Integer.parseInt(parts[0]);
//             //     int slabs = Integer.parseInt(parts[1]);

//             //     newSlabAlloc.put(sc, slabs);
//             // }   
//             // return newSlabAlloc;
//             if (epochNum < slabAllocPerEpoch.size()) {         
//                this.epochNum++;   
//             }
//             return slabAllocPerEpoch.get(this.epochNum-1);
//         } catch (Exception e) {
//             e.printStackTrace();
//             return null;
//         }
//     }
    
//     public BufferedReader openTrace() {
//         // URL url = new URL(this.trace_url);
//         // return new BufferedReader(new InputStreamReader(url.openStream()));
//         try {
//             return Files.newBufferedReader(Paths.get(traceLink));
//         } catch (Exception e) {
//             e.printStackTrace();
//             return null;
//         }
//     }

//     public static int getSlabClass(int size) {
//         for (int sc : SLAB_SIZES) {
//             if (size < sc) {
//                 return sc;
//             }
//         }
//         return SLAB_SIZES[SLAB_SIZES.length - 1];   
//     }

//     public void reallocSlabs(HashMap<Integer, Integer> newSlabAlloc) {
//         for (int sc : this.slabAllocation.keySet()) {
//             if (this.slabAllocation.get(sc) != newSlabAlloc.get(sc)) {
//                 // if the allocation has changed, evict the LRU items until desired allocation reached
//                 Iterator<Long> currIt = this.cacheLRU.get(sc).iterator();
//                 while ((long) this.cacheUsedSpace.get(sc) > (long) newSlabAlloc.get(sc) * SLAB_SIZE) {
//                     Long removeItemId = currIt.next();
//                     Integer removeItemSize = this.cacheContents.get(removeItemId);

//                     this.cacheUsedSpace.put(sc, this.cacheUsedSpace.get(sc) - removeItemSize);
//                     this.cacheContents.remove(removeItemId);   
//                     currIt.remove();
//                 }
//             }
//         }
//         this.slabAllocation = newSlabAlloc;
//     }

//     public void addToSlabClass(int sc, long id, int size) {
//         // if there is just not enough space to hold the item, just don't store it
//         if (size > this.slabAllocation.get(sc) * SLAB_SIZE) {
//             return;
//         }

//         Iterator<Long> currIt = this.cacheLRU.get(sc).iterator();

//         // remove from the slab class until there is enough space
//         while ((long) this.slabAllocation.get(sc) * SLAB_SIZE < 
//                     (long) this.cacheUsedSpace.get(sc) + size) {
//             Long removeItemId = currIt.next();
//             Integer removeItemSize = this.cacheContents.get(removeItemId);

//             this.cacheUsedSpace.put(sc, this.cacheUsedSpace.get(sc) - removeItemSize);
//             this.cacheContents.remove(removeItemId);   
//             currIt.remove();
//         }

//         // add the item to the slab class
//         this.cacheUsedSpace.put(sc, this.cacheUsedSpace.get(sc) + size);
//         this.cacheContents.put(id, size);
//         this.cacheLRU.get(sc).add(id);
//     }

//     public void processLine(TraceLine line) {
//         // parse the line for id and size
//         // String[] itemArr = line.split(",");
//         // long id = Long.parseLong(itemArr[1]);
//         long id = line.id;
//         int size = line.size;

//         // int size = Integer.parseInt(itemArr[2]) + Integer.parseInt(itemArr[3]);
//         int sc = getSlabClass(size);

//         // re-evaluate slab allocation and re-allocate if necessary
//         if (this.requests % LINES_READ_PER_CHUNK == 0) {    
//             HashMap<Integer, Integer> newSlabAlloc = this.getNextSlabAllocation();
//             if (!newSlabAlloc.equals(this.slabAllocation)) {
//                 this.reallocSlabs(newSlabAlloc);
//             }
//         }

//         if (this.cacheContents.containsKey(id)) {
//             int oldSize = this.cacheContents.get(id);
//             if (size != oldSize) {
//                 // if the size of the item has changed, then remove it from the old and add again
//                 int oldSC = getSlabClass(oldSize);
//                 this.cacheUsedSpace.put(oldSC, this.cacheUsedSpace.get(oldSC) - oldSize);   
//                 this.cacheContents.remove(id);
//                 this.cacheLRU.get(oldSC).remove(id);

//                 this.addToSlabClass(sc, id, size);
//             } else {
//                 // if it's a hit, just move item up in LRU
//                 this.cacheLRU.get(sc).remove(id);
//                 this.cacheLRU.get(sc).add(id);
//                 this.hits++;
//             }
            
//             this.requests++;
//         } else {
//             this.addToSlabClass(sc, id, size);
            
//             this.requests++;
//         }
//     }

//     // driver function for looping through trace
//     public float calculateHitRate() {
//         this.initCache();

//         try {
//             TraceLine line;
//             while((line = this.readTraceLine()) != null) {
//                 this.processLine(line);
//             }
//         } catch (Exception e) {
//             e.printStackTrace();
//         }

//         return (float) (((double) this.hits) / (this.requests));
//     }

//     // public void printResults() {
//     //     System.out.println(this.allocationLink + " d");
//     //     System.out.printf("HITS: %d\n", this.hits);
//     //     System.out.printf("REQUESTS: %d\n", this.requests);
//     //     System.out.printf("HIT RATE: %f\n", ((double) this.hits) / this.requests);
        
//     //     // System.out.print("SPACE ALLOCATION: ");
//     //     // System.out.println(this.cacheUsedSpace);
        
//     //     // for (int sc : this.slabAllocation.keySet()) {
//     //     //     this.slabAllocation.put(sc, this.slabAllocation.get(sc) * SLAB_SIZE);
//     //     // }
//     //     // System.out.print("ALLOWED SPACE: ");
//     //     // System.out.println(this.slabAllocation);        
//     // }

//     public static void main(String[] args){  
//         System.out.println("Initializing cache and counting hits!");  

//         // for (int n : CacheDynamic.POTENTIAL_TOTAL_SLABS_COUNTS) {
//         //     CacheDynamic.allocationLink = "./HFCResults/HFC-smallslabs-" + "cluster46sampled100" 
//         //                             + "-" + Integer.toString(n) + "slabs.txt";
//         //     CacheDynamic currCache = new CacheDynamic();
//         //     currCache.calculateHitRate();
//         //     // currCache.printResults();
//         // }
//     }

// }




/* 
 * Simulates a static cache on the specified trace 
 */

import java.util.*;
import java.io.*;
import java.nio.*;

public class CacheSimDynamic {

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

    public ArrayList<HashMap<Integer, Integer>> slabAllocationEpoch;

    /* 
        Constructor
    */
    public CacheSimDynamic(String traceLink, String resultLink, ArrayList<HashMap<Integer, Integer>> slabAllocationEpoch) {
        this.hits = 0;
        this.epochhits = 0;
        this.requests = 1;
        this.traceLink = traceLink;
        this.resultLink = resultLink;
        
        this.slabAllocationEpoch = slabAllocationEpoch;
        this.slabAllocation = this.slabAllocationEpoch.get(0);

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
        // HashMap<Integer, Float> util = new HashMap<Integer, Float>();
        // for (int sc : SLAB_CLASSES) {
        //     util.put(sc, (float) this.cacheUsedSpace.get(sc) / (float) (this.slabAllocation.get(sc) * SLAB_SIZE));
        // }
        this.slabAllocation = this.slabAllocationEpoch.get(
            Integer.min(this.requests / LINES_READ_PER_CHUNK, slabAllocationEpoch.size() - 1)
        );

        // write the results
        try {
            // this.writer.write("epoch " + String.valueOf(this.requests / LINES_READ_PER_CHUNK) + "\n");
            // this.writer.write(util.toString() + "\n");
            // this.writer.write(String.format("epoch hitrate: %f\n", (float) this.epochhits / (float) LINES_READ_PER_CHUNK));
            // this.writer.write(String.format("lifetime hitrate: %f\n", (float) this.hits / (float) this.requests));
            // this.writer.write("\n");
            this.epochhits = 0;   
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public float calculateHitRate() {        
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
        return (float) this.hits / (float) this.requests;
    }

    // example how to run
    public static void main(String[] args){  
        // System.out.println("Initializing cache and counting hits!");  
        // HashMap<Integer, Integer> slabAllocation = new HashMap<>();
        // slabAllocation.put(64, 16);
        // slabAllocation.put(128, 8);
        // slabAllocation.put(256, 8);
        // slabAllocation.put(512, 4);
        // slabAllocation.put(1024, 4);
        // slabAllocation.put(2048, 3);
        // slabAllocation.put(4096, 3);
        // slabAllocation.put(8192, 2);
        // slabAllocation.put(16384, 2);
        // slabAllocation.put(32768, 2);
        // slabAllocation.put(65536, 1);
        // slabAllocation.put(131072, 1);
        // slabAllocation.put(262144, 1);
        // slabAllocation.put(524288, 1);
        // slabAllocation.put(1048576, 1);
        
        // CacheSimDynamic currCache = new CacheSimDynamic("/mntData2/jason/cphy/w05.oracleGeneral.bin", "./results/w05.static-cache.txt", slabAllocation);
        // currCache.calculateHitRate();
    }

}