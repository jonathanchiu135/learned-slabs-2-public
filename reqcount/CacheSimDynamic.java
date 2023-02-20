import java.util.*;
import java.io.*;
import java.nio.file.*;

public class CacheSimDynamic {
    
    public static int[] SLAB_SIZES = {64, 128, 256, 512, 1024, 2048, 4096, 8192, 
        16384, 32768, 65536, 131072, 262144, 524288, 1048576};
    public static int[] POTENTIAL_TOTAL_SLABS_COUNTS = { 10, 20, 50, 100, 200, 512, 1024, 2048, 4096 };
    public static int SLAB_SIZE = 10000;
    public static int LINES_READ_PER_CHUNK = 540000;
    public static String traceLink = "/mntData2/cluster46sampled100.txt";
    public static String allocationLink = "./HFCResults/HFC-smallslabs-cluster50hashed-4096slabs.txt";
    public static String resultLink = "";

    // public static String allocationLink;

    public int hits;
    public int requests;
    public int epochNum;

    public ArrayList<HashMap<Integer, Integer>> slabAllocPerEpoch;
    public HashMap<Integer, Integer> slabAllocation;

    public HashMap<Integer, Integer> cacheUsedSpace;
    public HashMap<Long, Integer> cacheContents; // maps id to size
    public HashMap<Integer, LinkedHashSet<Long>> cacheLRU;

    /* 
        Constructor
    */
    public CacheSimDynamic(ArrayList<HashMap<Integer, Integer>> slabAllocPerEpoch) {
        this.hits = 0;
        this.requests = 1;
        this.epochNum = 0;

        this.slabAllocPerEpoch = slabAllocPerEpoch;

        this.cacheUsedSpace = new HashMap<Integer, Integer>();
        this.cacheContents = new HashMap<Long, Integer>();
        this.cacheLRU = new HashMap<Integer, LinkedHashSet<Long>>();
        
        for (int sc : SLAB_SIZES) {
            this.cacheUsedSpace.put(sc, 0);
            this.cacheLRU.put(sc, new LinkedHashSet<Long>());
        }
    }

    public void initCache() {
        try {
            // determine the slab allocation for the trace by reading result file
            this.slabAllocation = this.getNextSlabAllocation();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public HashMap<Integer, Integer> getNextSlabAllocation() {
        try {
            // HashMap<Integer, Integer> newSlabAlloc = new HashMap<Integer, Integer>();
           
            // String line = this.slabAllocReader.readLine();
            // line = line.substring(1, line.length() - 1);
            // String[] keyVals = line.split(", ");
            // for(String keyVal : keyVals ){
            //     String[] parts = keyVal.split("=", 2);
            //     int sc = Integer.parseInt(parts[0]);
            //     int slabs = Integer.parseInt(parts[1]);

            //     newSlabAlloc.put(sc, slabs);
            // }   
            // return newSlabAlloc;
            if (epochNum < slabAllocPerEpoch.size()) {         
               this.epochNum++;   
            }
            return slabAllocPerEpoch.get(epochNum-1);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public BufferedReader openTrace() {
        // URL url = new URL(this.trace_url);
        // return new BufferedReader(new InputStreamReader(url.openStream()));
        try {
            return Files.newBufferedReader(Paths.get(traceLink));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static int getSlabClass(int size) {
        for (int sc : SLAB_SIZES) {
            if (size < sc) {
                return sc;
            }
        }
        return SLAB_SIZES[SLAB_SIZES.length - 1];   
    }

    public void reallocSlabs(HashMap<Integer, Integer> newSlabAlloc) {
        for (int sc : this.slabAllocation.keySet()) {
            if (this.slabAllocation.get(sc) != newSlabAlloc.get(sc)) {
                // if the allocation has changed, evict the LRU items until desired allocation reached
                Iterator<Long> currIt = this.cacheLRU.get(sc).iterator();
                while ((long) this.cacheUsedSpace.get(sc) > (long) newSlabAlloc.get(sc) * SLAB_SIZE) {
                    Long removeItemId = currIt.next();
                    Integer removeItemSize = this.cacheContents.get(removeItemId);

                    this.cacheUsedSpace.put(sc, this.cacheUsedSpace.get(sc) - removeItemSize);
                    this.cacheContents.remove(removeItemId);   
                    currIt.remove();
                }
            }
        }
        this.slabAllocation = newSlabAlloc;
    }

    public void addToSlabClass(int sc, long id, int size) {
        // if there is just not enough space to hold the item, just don't store it
        if (size > this.slabAllocation.get(sc) * SLAB_SIZE) {
            return;
        }

        Iterator<Long> currIt = this.cacheLRU.get(sc).iterator();

        // remove from the slab class until there is enough space
        while ((long) this.slabAllocation.get(sc) * SLAB_SIZE < 
                    (long) this.cacheUsedSpace.get(sc) + size) {
            Long removeItemId = currIt.next();
            Integer removeItemSize = this.cacheContents.get(removeItemId);

            this.cacheUsedSpace.put(sc, this.cacheUsedSpace.get(sc) - removeItemSize);
            this.cacheContents.remove(removeItemId);   
            currIt.remove();
        }

        // add the item to the slab class
        this.cacheUsedSpace.put(sc, this.cacheUsedSpace.get(sc) + size);
        this.cacheContents.put(id, size);
        this.cacheLRU.get(sc).add(id);
    }

    public void processLine(String line) {
        // parse the line for id and size
        String[] itemArr = line.split(",");
        long id = Long.parseLong(itemArr[1]);
        int size = Integer.parseInt(itemArr[2]) + Integer.parseInt(itemArr[3]);
        int sc = getSlabClass(size);

        // re-evaluate slab allocation and re-allocate if necessary
        if (this.requests % LINES_READ_PER_CHUNK == 0) {    
            HashMap<Integer, Integer> newSlabAlloc = this.getNextSlabAllocation();
            if (!newSlabAlloc.equals(this.slabAllocation)) {
                this.reallocSlabs(newSlabAlloc);
            }
        }

        if (this.cacheContents.containsKey(id)) {
            int oldSize = this.cacheContents.get(id);
            if (size != oldSize) {
                // if the size of the item has changed, then remove it from the old and add again
                int oldSC = getSlabClass(oldSize);
                this.cacheUsedSpace.put(oldSC, this.cacheUsedSpace.get(oldSC) - oldSize);   
                this.cacheContents.remove(id);
                this.cacheLRU.get(oldSC).remove(id);

                this.addToSlabClass(sc, id, size);
            } else {
                // if it's a hit, just move item up in LRU
                this.cacheLRU.get(sc).remove(id);
                this.cacheLRU.get(sc).add(id);
                this.hits++;
            }
            
            this.requests++;
        } else {
            this.addToSlabClass(sc, id, size);
            
            this.requests++;
        }
    }

    // driver function for looping through trace
    public float calculateHitRate() {
        this.initCache();
        
        BufferedReader trace = this.openTrace();
        try {
            String line;
            while((line = trace.readLine()) != null) {
                this.processLine(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return (float) (((double) this.hits) / (this.requests));
    }

    // public void printResults() {
    //     System.out.println(this.allocationLink + " d");
    //     System.out.printf("HITS: %d\n", this.hits);
    //     System.out.printf("REQUESTS: %d\n", this.requests);
    //     System.out.printf("HIT RATE: %f\n", ((double) this.hits) / this.requests);
        
    //     // System.out.print("SPACE ALLOCATION: ");
    //     // System.out.println(this.cacheUsedSpace);
        
    //     // for (int sc : this.slabAllocation.keySet()) {
    //     //     this.slabAllocation.put(sc, this.slabAllocation.get(sc) * SLAB_SIZE);
    //     // }
    //     // System.out.print("ALLOWED SPACE: ");
    //     // System.out.println(this.slabAllocation);        
    // }

    public static void main(String[] args){  
        System.out.println("Initializing cache and counting hits!");  

        // for (int n : CacheDynamic.POTENTIAL_TOTAL_SLABS_COUNTS) {
        //     CacheDynamic.allocationLink = "./HFCResults/HFC-smallslabs-" + "cluster46sampled100" 
        //                             + "-" + Integer.toString(n) + "slabs.txt";
        //     CacheDynamic currCache = new CacheDynamic();
        //     currCache.calculateHitRate();
        //     // currCache.printResults();
        // }
    }

}
